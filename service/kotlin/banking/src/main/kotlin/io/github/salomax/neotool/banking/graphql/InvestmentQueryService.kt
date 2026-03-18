package io.github.salomax.neotool.banking.graphql

import io.github.salomax.neotool.banking.domain.account.FinancialAccount
import io.github.salomax.neotool.banking.domain.account.FinancialAccountRepository
import io.github.salomax.neotool.banking.domain.investment.Asset
import io.github.salomax.neotool.banking.domain.investment.AssetRepository
import io.github.salomax.neotool.banking.domain.investment.FixedIncomeValuationService
import io.github.salomax.neotool.banking.domain.investment.Holding
import io.github.salomax.neotool.banking.domain.investment.HoldingRepository
import io.github.salomax.neotool.banking.domain.investment.IndicatorRateProvider
import io.github.salomax.neotool.banking.domain.investment.Indexer
import io.github.salomax.neotool.banking.domain.investment.ValuationSource
import io.github.salomax.neotool.banking.domain.issuer.Issuer
import io.github.salomax.neotool.banking.domain.issuer.IssuerRepository
import io.github.salomax.neotool.banking.graphql.dto.BankAccountDataDTO
import io.github.salomax.neotool.banking.graphql.dto.CardDataDTO
import io.github.salomax.neotool.banking.graphql.dto.FinancialAccountDTO
import io.github.salomax.neotool.banking.graphql.dto.InvestmentAssetDTO
import io.github.salomax.neotool.banking.graphql.dto.InvestmentHoldingDTO
import io.github.salomax.neotool.banking.graphql.dto.IssuerDTO
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
        val rateMap = safeGetIndicatorRates()
        val refDate = indicatorRateProvider.getReferenceDate()
        val today = clock()

        return holdings.map { it.toDTO(accountsById, assetsById, issuersById, rateMap, refDate, today) }
    }

    private fun safeGetIndicatorRates(): Map<Indexer, BigDecimal> =
        try {
            indicatorRateProvider.getLatestRates()
        } catch (e: Exception) {
            logger.warn(e) { "Failed to fetch indicator rates; valuations will fall back to UNAVAILABLE" }
            emptyMap()
        }

    private fun Holding.toDTO(
        accountsById: Map<UUID, FinancialAccount>,
        assetsById: Map<UUID, Asset>,
        issuersById: Map<UUID, Issuer>,
        rateMap: Map<Indexer, BigDecimal>,
        refDate: LocalDate?,
        today: LocalDate,
    ): InvestmentHoldingDTO {
        val financialAccount = accountsById[financialAccountId]
        val isExpired = maturityDate != null && maturityDate!!.isBefore(today)

        val valuation = if (grossValue != null) {
            Valuation(grossValue?.toDouble(), netValue?.toDouble(), incomeTax?.toDouble(), iof?.toDouble(), ValuationSource.PROVIDER, null)
        } else {
            try {
                val result = fixedIncomeValuationService.estimate(this, rateMap, refDate)
                Valuation(result.grossValue?.toDouble(), result.netValue?.toDouble(), result.incomeTax?.toDouble(), result.iof?.toDouble(), result.valuationSource, result.valuationReferenceDate)
            } catch (e: Exception) {
                logger.warn(e) { "Failed to estimate valuation for holding $id" }
                Valuation(null, null, null, null, ValuationSource.UNAVAILABLE, null)
            }
        }

        return InvestmentHoldingDTO(
            id = id!!,
            financialAccountId = financialAccountId,
            accountId = accountId,
            assetId = assetId,
            asset = assetsById[assetId]?.toDTO(),
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
            valuationSource = valuation.source,
            valuationReferenceDate = valuation.refDate,
            isExpired = isExpired,
            source = source,
            createdAt = createdAt,
            updatedAt = updatedAt,
        )
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
