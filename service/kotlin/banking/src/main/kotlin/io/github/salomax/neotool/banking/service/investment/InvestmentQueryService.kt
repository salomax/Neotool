package io.github.salomax.neotool.banking.service.investment

import io.github.salomax.neotool.banking.domain.investment.*
import io.github.salomax.neotool.banking.domain.account.FinancialAccount
import io.github.salomax.neotool.banking.domain.account.FinancialAccountRepository
import io.github.salomax.neotool.banking.domain.investment.Asset
import io.github.salomax.neotool.banking.domain.investment.AssetRepository
import io.github.salomax.neotool.banking.domain.investment.AssetType
import io.github.salomax.neotool.banking.service.investment.FixedIncomeValuationService
import io.github.salomax.neotool.banking.domain.investment.FundMarketDataProvider
import io.github.salomax.neotool.banking.domain.investment.FundMarketSnapshot
import io.github.salomax.neotool.banking.service.investment.FundValuationService
import io.github.salomax.neotool.banking.domain.investment.Holding
import io.github.salomax.neotool.banking.domain.investment.HoldingRepository
import io.github.salomax.neotool.banking.domain.investment.HoldingPricingMode
import io.github.salomax.neotool.banking.domain.investment.IndicatorRateProvider
import io.github.salomax.neotool.banking.domain.investment.Indexer
import io.github.salomax.neotool.banking.domain.investment.InvestmentTransaction
import io.github.salomax.neotool.banking.domain.investment.InvestmentTransactionRepository
import io.github.salomax.neotool.banking.service.investment.ValuationSource
import io.github.salomax.neotool.banking.domain.issuer.Issuer
import io.github.salomax.neotool.banking.domain.issuer.IssuerRepository
import io.github.salomax.neotool.banking.graphql.account.BankAccountDataDTO
import io.github.salomax.neotool.banking.graphql.account.CardDataDTO
import io.github.salomax.neotool.banking.graphql.account.FinancialAccountDTO
import io.github.salomax.neotool.banking.graphql.investment.InvestmentAssetDTO
import io.github.salomax.neotool.banking.graphql.investment.InvestmentHoldingDTO
import io.github.salomax.neotool.banking.graphql.issuer.IssuerDTO
import jakarta.inject.Singleton
import mu.KotlinLogging
import java.math.BigDecimal
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID

/**
 * Query service that orchestrates all holding read operations:
 * batch-loading related entities, computing valuations, and mapping to DTOs.
 *
 * Resolvers must not call repositories directly — all holding queries go through here.
 */
@Singleton
class InvestmentQueryService(
    private val holdingRepository: HoldingRepository,
    private val assetRepository: AssetRepository,
    private val financialAccountRepository: FinancialAccountRepository,
    private val issuerRepository: IssuerRepository,
    private val fixedIncomeValuationService: FixedIncomeValuationService,
    private val fundValuationService: FundValuationService,
    private val fundMarketDataProvider: FundMarketDataProvider,
    private val investmentTransactionRepository: InvestmentTransactionRepository,
    private val indicatorRateProvider: IndicatorRateProvider,
    private val clock: () -> LocalDate = { LocalDate.now() },
) {
    private val logger = KotlinLogging.logger {}

    fun getHoldingsByFinancialAccount(financialAccountId: UUID, accountId: UUID): List<InvestmentHoldingDTO> {
        val holdings = holdingRepository.findByFinancialAccount(financialAccountId, accountId)
            .filter { it.quantity > BigDecimal.ZERO }
        return mapHoldingsToDTO(holdings, accountId)
    }

    fun getAllHoldings(accountId: UUID): List<InvestmentHoldingDTO> {
        val holdings = holdingRepository.findByAccount(accountId)
            .filter { it.quantity > BigDecimal.ZERO }
        return mapHoldingsToDTO(holdings, accountId)
    }

    fun getHolding(id: UUID, accountId: UUID): InvestmentHoldingDTO? {
        val holding = holdingRepository.findById(id, accountId) ?: return null
        return mapHoldingsToDTO(listOf(holding), accountId).first()
    }

    // ── Private ───────────────────────────────────────────────────────────────

    private fun mapHoldingsToDTO(holdings: List<Holding>, accountId: UUID): List<InvestmentHoldingDTO> {
        if (holdings.isEmpty()) return emptyList()

        val accountsById = financialAccountRepository.findByAccountId(accountId).associateBy { it.id!! }
        val issuersById = issuerRepository
            .findByIds(accountsById.values.mapNotNull { it.issuerId }.toSet())
            .associateBy { it.id!! }
        val assetsById = assetRepository
            .findByIds(holdings.map { it.assetId }.toSet())
            .associateBy { it.id!! }
        val marketDataCnpjs = holdings
            .mapNotNull { holding ->
                val asset = assetsById[holding.assetId]
                if (holding.pricingMode == HoldingPricingMode.MARKET_DATA && asset?.assetType.isFundAsset()) {
                    asset?.fundCnpj?.normalizedFundCnpj()
                } else {
                    null
                }
            }
            .toSet()
        val marketSnapshotsByCnpj = safeGetFundMarketSnapshots(marketDataCnpjs)
        val holdingTransactionsById = loadHoldingTransactionsById(holdings, assetsById, marketSnapshotsByCnpj, accountId)
        val rateMap = safeGetIndicatorRates()
        val refDate = indicatorRateProvider.getReferenceDate()
        val today = clock()

        return holdings.map {
            it.toDTO(
                accountsById = accountsById,
                assetsById = assetsById,
                issuersById = issuersById,
                rateMap = rateMap,
                fixedIncomeRefDate = refDate,
                today = today,
                marketSnapshotsByCnpj = marketSnapshotsByCnpj,
                holdingTransactionsById = holdingTransactionsById,
            )
        }
    }

    private fun safeGetIndicatorRates(): Map<Indexer, BigDecimal> =
        try {
            indicatorRateProvider.getLatestRates()
        } catch (e: Exception) {
            logger.warn(e) { "Failed to fetch indicator rates; valuations will fall back to UNAVAILABLE" }
            emptyMap()
        }

    private fun safeGetFundMarketSnapshots(cnpjs: Set<String>): Map<String, FundMarketSnapshot> =
        try {
            fundMarketDataProvider.getSnapshotsByCnpj(cnpjs)
        } catch (e: Exception) {
            logger.warn(e) { "Failed to fetch fund market snapshots; fund valuations will fall back" }
            emptyMap()
        }

    private fun loadHoldingTransactionsById(
        holdings: List<Holding>,
        assetsById: Map<UUID, Asset>,
        marketSnapshotsByCnpj: Map<String, FundMarketSnapshot>,
        accountId: UUID,
    ): Map<UUID, List<InvestmentTransaction>> {
        val marketDataHoldingIds = holdings
            .mapNotNull { holding ->
                val asset = assetsById[holding.assetId] ?: return@mapNotNull null
                val normalizedCnpj = asset.fundCnpj?.normalizedFundCnpj() ?: return@mapNotNull null
                if (
                    holding.pricingMode == HoldingPricingMode.MARKET_DATA &&
                    asset.assetType.isFundAsset() &&
                    marketSnapshotsByCnpj.containsKey(normalizedCnpj)
                ) {
                    holding.id
                } else {
                    null
                }
            }
        if (marketDataHoldingIds.isEmpty()) return emptyMap()
        return investmentTransactionRepository.findByHoldingIds(marketDataHoldingIds, accountId)
            .groupBy { it.holdingId }
    }

    private fun Holding.toDTO(
        accountsById: Map<UUID, FinancialAccount>,
        assetsById: Map<UUID, Asset>,
        issuersById: Map<UUID, Issuer>,
        rateMap: Map<Indexer, BigDecimal>,
        fixedIncomeRefDate: LocalDate?,
        today: LocalDate,
        marketSnapshotsByCnpj: Map<String, FundMarketSnapshot>,
        holdingTransactionsById: Map<UUID, List<InvestmentTransaction>>,
    ): InvestmentHoldingDTO {
        val financialAccount = accountsById[financialAccountId]
        val asset = assetsById[assetId]
        val isExpired = maturityDate != null && maturityDate!!.isBefore(today)

        val valuation =
            manualValuation()
                ?: marketDataValuation(asset, marketSnapshotsByCnpj, holdingTransactionsById)
                ?: providerValuation()
                ?: estimatedFixedIncomeValuation(rateMap, fixedIncomeRefDate)
                ?: Valuation(null, null, null, null, ValuationSource.UNAVAILABLE, null)

        return InvestmentHoldingDTO(
            id = id!!,
            financialAccountId = financialAccountId,
            accountId = accountId,
            assetId = assetId,
            asset = asset?.toDTO(),
            financialAccount = financialAccount?.toDTO(issuersById[financialAccount.issuerId]),
            quantity = quantity.toDouble(),
            averageCost = averageCost.toDouble(),
            indexer = indexer,
            indexerPercentage = indexerPercentage?.toDouble(),
            preFixedRate = preFixedRate?.toDouble(),
            issueDate = issueDate,
            maturityDate = maturityDate,
            gracePeriodDate = gracePeriodDate,
            isTaxExempt = isTaxExempt,
            grossValue = valuation.grossValue,
            netValue = valuation.netValue,
            incomeTax = valuation.incomeTax,
            iof = valuation.iof,
            lastValuedAt = lastValuedAt,
            pricingMode = pricingMode,
            valuationSource = valuation.source,
            valuationReferenceDate = valuation.refDate,
            isExpired = isExpired,
            source = source,
            createdAt = createdAt,
            updatedAt = updatedAt,
        )
    }

    private fun Holding.manualValuation(): Valuation? {
        if (pricingMode != HoldingPricingMode.MANUAL || manualGrossValue == null) return null
        return Valuation(
            grossValue = manualGrossValue.toDouble(),
            netValue = null,
            incomeTax = null,
            iof = null,
            source = ValuationSource.MANUAL,
            refDate = manualValuedAt,
        )
    }

    private fun Holding.marketDataValuation(
        asset: Asset?,
        marketSnapshotsByCnpj: Map<String, FundMarketSnapshot>,
        holdingTransactionsById: Map<UUID, List<InvestmentTransaction>>,
    ): Valuation? {
        if (pricingMode != HoldingPricingMode.MARKET_DATA) return null
        if (asset == null || !asset.assetType.isFundAsset()) return null
        val fundCnpj = asset.fundCnpj?.normalizedFundCnpj() ?: return null
        val snapshot = marketSnapshotsByCnpj[fundCnpj] ?: return null
        val holdingId = id ?: return null
        return try {
            val result = fundValuationService.estimate(this, snapshot, holdingTransactionsById[holdingId].orEmpty())
            Valuation(
                grossValue = result.grossValue?.toDouble(),
                netValue = result.netValue?.toDouble(),
                incomeTax = result.incomeTax?.toDouble(),
                iof = result.iof?.toDouble(),
                source = result.valuationSource,
                refDate = result.valuationReferenceDate,
            )
        } catch (e: Exception) {
            logger.warn(e) { "Failed to estimate fund valuation for holding $holdingId" }
            null
        }
    }

    private fun Holding.providerValuation(): Valuation? {
        if (grossValue == null) return null
        return Valuation(
            grossValue = grossValue.toDouble(),
            netValue = netValue?.toDouble(),
            incomeTax = incomeTax?.toDouble(),
            iof = iof?.toDouble(),
            source = ValuationSource.PROVIDER,
            refDate = null,
        )
    }

    private fun Holding.estimatedFixedIncomeValuation(
        rateMap: Map<Indexer, BigDecimal>,
        refDate: LocalDate?,
    ): Valuation? =
        try {
            val result = fixedIncomeValuationService.estimate(this, rateMap, refDate)
            Valuation(
                result.grossValue?.toDouble(),
                result.netValue?.toDouble(),
                result.incomeTax?.toDouble(),
                result.iof?.toDouble(),
                result.valuationSource,
                result.valuationReferenceDate,
            )
        } catch (e: Exception) {
            logger.warn(e) { "Failed to estimate valuation for holding $id" }
            null
        }

    private data class Valuation(
        val grossValue: Double?,
        val netValue: Double?,
        val incomeTax: Double?,
        val iof: Double?,
        val source: ValuationSource,
        val refDate: LocalDate?,
    )

    private fun Asset.toDTO() = InvestmentAssetDTO(
        id = id!!,
        ticker = ticker,
        isinCode = isinCode,
        name = name,
        assetType = assetType,
        exchange = exchange,
        currencyCode = currencyCode,
        issuerCnpj = issuerCnpj,
        issuerName = issuerName,
        fundCnpj = fundCnpj,
        anbimaCategory = anbimaCategory,
        anbimaClass = anbimaClass,
        anbimaSubclass = anbimaSubclass,
        priceFactor = priceFactor.toDouble(),
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

    private fun AssetType?.isFundAsset(): Boolean =
        this == AssetType.MUTUAL_FUND || this == AssetType.PGBL || this == AssetType.VGBL

    private fun String.normalizedFundCnpj(): String = filter(Char::isDigit)

    private fun FinancialAccount.toDTO(issuer: Issuer?) = FinancialAccountDTO(
        id = id!!,
        type = type,
        subtype = subtype,
        name = name,
        currencyCode = currencyCode,
        includeInNetWorth = includeInNetWorth,
        includeInDashboard = includeInDashboard,
        themeColor = themeColor,
        issuer = issuer?.let {
            IssuerDTO(
                id = it.id!!,
                name = it.name,
                domain = it.domain,
                compeCode = it.compeCode,
                ispb = it.ispb,
                countryCode = it.countryCode,
                logoKey = it.logoKey,
                category = it.category,
                logoAsset = it.logoKey?.let { key -> mapOf("__typename" to "Asset", "id" to key) },
            )
        },
        issuerName = issuerName,
        bankData = bankData?.let {
            BankAccountDataDTO(
                branchCode = it.branchCode,
                number = it.number,
                checkDigit = it.checkDigit,
                maskedNumber = it.maskedNumber,
            )
        },
        cardData = cardData?.let {
            CardDataDTO(
                maskedNumber = it.maskedNumber,
                bin = it.bin,
                cardNetwork = it.cardNetwork,
                cardLevel = it.cardLevel,
                creditLimit = it.creditLimit?.toDouble(),
                availableCreditLimit = it.availableCreditLimit?.toDouble(),
                minimumPaymentPercentage = it.minimumPaymentPercentage?.toDouble(),
                closingDay = it.closingDay,
                dueDay = it.dueDay,
                linkedBankAccountId = it.linkedBankAccountId,
            )
        },
        connectionStatus = connectionStatus,
        consentExpiresAt = consentExpiresAt?.atOffset(ZoneOffset.UTC),
        lastSyncAt = lastSyncAt?.atOffset(ZoneOffset.UTC),
        createdAt = createdAt.atOffset(ZoneOffset.UTC),
        updatedAt = updatedAt.atOffset(ZoneOffset.UTC),
    )
}
