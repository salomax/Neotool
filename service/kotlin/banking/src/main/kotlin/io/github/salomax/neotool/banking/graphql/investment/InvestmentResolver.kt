package io.github.salomax.neotool.banking.graphql.investment

import io.github.salomax.neotool.banking.domain.investment.Asset
import io.github.salomax.neotool.banking.repo.investment.AssetRepository
import io.github.salomax.neotool.banking.domain.investment.AssetType
import io.github.salomax.neotool.banking.domain.investment.BrokerNote
import io.github.salomax.neotool.banking.service.investment.CnpjValidator
import io.github.salomax.neotool.banking.domain.investment.HoldingPricingMode
import io.github.salomax.neotool.banking.domain.investment.Indexer
import io.github.salomax.neotool.banking.service.investment.InvestmentAssetService
import io.github.salomax.neotool.banking.domain.investment.InvestmentSource
import io.github.salomax.neotool.banking.domain.investment.InvestmentTransaction
import io.github.salomax.neotool.banking.domain.investment.InvestmentTransactionDirection
import io.github.salomax.neotool.banking.service.investment.InvestmentTransactionService
import io.github.salomax.neotool.banking.domain.investment.InvestmentTransactionType
import io.github.salomax.neotool.banking.domain.investment.ManualFundValuation
import io.github.salomax.neotool.banking.domain.investment.RecordInvestmentTransactionCommand
import io.github.salomax.neotool.banking.service.investment.InvestmentQueryService
import io.github.salomax.neotool.banking.graphql.investment.BrokerNoteDTO
import io.github.salomax.neotool.banking.graphql.investment.HoldingPerformanceDTO
import io.github.salomax.neotool.banking.graphql.investment.HoldingPerformanceResolution
import io.github.salomax.neotool.banking.graphql.investment.InvestmentAssetDTO
import io.github.salomax.neotool.banking.graphql.investment.InvestmentHoldingDTO
import io.github.salomax.neotool.banking.graphql.investment.InvestmentTransactionDTO
import io.github.salomax.neotool.common.security.principal.RequestPrincipal
import jakarta.inject.Singleton
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

/**
 * GraphQL resolver for investment queries and mutations.
 * Holding queries delegate to [InvestmentQueryService]; mutations go through [InvestmentTransactionService].
 */
@Singleton
class InvestmentResolver(
    private val investmentTransactionService: InvestmentTransactionService,
    private val investmentAssetService: InvestmentAssetService,
    private val investmentQueryService: InvestmentQueryService,
    private val assetRepository: AssetRepository,
) {
    // ── Queries ───────────────────────────────────────────────────────────────

    fun investmentHoldings(
        financialAccountId: String,
        principal: RequestPrincipal,
    ): List<InvestmentHoldingDTO> {
        val accountId = requireAccountId(principal)
        return investmentQueryService.getHoldingsByFinancialAccount(UUID.fromString(financialAccountId), accountId)
    }

    fun allInvestmentHoldings(principal: RequestPrincipal): List<InvestmentHoldingDTO> {
        val accountId = requireAccountId(principal)
        return investmentQueryService.getAllHoldings(accountId)
    }

    fun investmentHolding(
        id: String,
        principal: RequestPrincipal,
    ): InvestmentHoldingDTO? {
        val accountId = requireAccountId(principal)
        return investmentQueryService.getHolding(UUID.fromString(id), accountId)
    }

    fun investmentTransactions(
        holdingId: String,
        principal: RequestPrincipal,
    ): List<InvestmentTransactionDTO> {
        val accountId = requireAccountId(principal)
        return investmentTransactionService
            .listByHolding(UUID.fromString(holdingId), accountId)
            .map { it.toDTO() }
    }

    fun investmentTransaction(
        id: String,
        principal: RequestPrincipal,
    ): InvestmentTransactionDTO {
        val accountId = requireAccountId(principal)
        return investmentTransactionService.findById(UUID.fromString(id), accountId).toDTO()
    }

    fun holdingPerformance(
        holdingId: String,
        resolution: String?,
        includeProjection: Boolean?,
        principal: RequestPrincipal,
    ): HoldingPerformanceDTO? {
        val accountId = requireAccountId(principal)
        val resolvedResolution = resolution?.let(HoldingPerformanceResolution::valueOf)
        return investmentQueryService.getHoldingPerformance(
            id = UUID.fromString(holdingId),
            accountId = accountId,
            resolution = resolvedResolution,
            includeProjection = includeProjection ?: false,
        )
    }

    fun investmentAsset(id: String): InvestmentAssetDTO? =
        assetRepository.findById(UUID.fromString(id))?.toDTO()

    // ── Mutations ─────────────────────────────────────────────────────────────

    fun createInvestmentAsset(
        input: Map<String, Any?>,
        @Suppress("UNUSED_PARAMETER") principal: RequestPrincipal,
    ): InvestmentAssetDTO {
        val name = input["name"] as String
        val assetType = AssetType.valueOf(input["assetType"] as String)
        val issuerName = input["issuerName"] as? String
        val fundCnpj = (input["fundCnpj"] as? String)?.takeIf { it.isNotBlank() }?.let(CnpjValidator::normalize)
        require(fundCnpj == null || CnpjValidator.isValid(fundCnpj)) { "fundCnpj must be a valid CNPJ" }
        val ticker = input["ticker"] as? String
        val isinCode = input["isinCode"] as? String
        val currencyCode = (input["currencyCode"] as? String) ?: "BRL"
        return investmentAssetService.create(
            name = name,
            assetType = assetType,
            issuerName = issuerName,
            fundCnpj = fundCnpj,
            ticker = ticker,
            isinCode = isinCode,
            currencyCode = currencyCode,
        ).toDTO()
    }

    fun recordInvestmentTransaction(
        input: Map<String, Any?>,
        principal: RequestPrincipal,
    ): InvestmentTransactionDTO {
        val accountId = requireAccountId(principal)
        val userId = requireNotNull(principal.userId) { "No user in token" }
        val command = input.toRecordCommand(accountId)
        return investmentTransactionService.record(command, accountId, userId).toDTO()
    }

    fun deleteInvestmentTransaction(
        id: String,
        principal: RequestPrincipal,
    ): Boolean {
        val accountId = requireAccountId(principal)
        return investmentTransactionService.delete(UUID.fromString(id), accountId)
    }

    fun deleteInvestmentHolding(
        id: String,
        principal: RequestPrincipal,
    ): Boolean {
        val accountId = requireAccountId(principal)
        return investmentTransactionService.deleteHolding(UUID.fromString(id), accountId)
    }

    // ── Mapping helpers ───────────────────────────────────────────────────────

    private fun Asset.toDTO() =
        InvestmentAssetDTO(
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
            logoAsset = null,
            priceFactor = priceFactor.toDouble(),
            createdAt = createdAt,
            updatedAt = updatedAt,
        )

    private fun InvestmentTransaction.toDTO() =
        InvestmentTransactionDTO(
            id = id!!,
            holdingId = holdingId,
            financialAccountId = financialAccountId,
            direction = direction,
            type = type,
            typeAdditionalInfo = typeAdditionalInfo,
            quantity = quantity?.toDouble(),
            unitPrice = unitPrice?.toDouble(),
            grossValue = grossValue.toDouble(),
            netValue = netValue?.toDouble(),
            incomeTax = incomeTax?.toDouble(),
            iof = iof?.toDouble(),
            indexerPercentage = indexerPercentage?.toDouble(),
            fixedAnnualRate = fixedAnnualRate?.toDouble(),
            priceFactor = priceFactor?.toDouble(),
            tradeDate = tradeDate,
            settlementDate = settlementDate,
            source = source,
            brokerNote = brokerNote?.toDTO(),
            createdAt = createdAt,
            updatedAt = updatedAt,
        )

    private fun BrokerNote.toDTO() =
        BrokerNoteDTO(
            brokerNoteNumber = brokerNoteNumber,
            grossValue = grossValue.toDouble(),
            brokerageFee = brokerageFee.toDouble(),
            clearingSettlementFee = clearingSettlementFee.toDouble(),
            clearingRegistrationFee = clearingRegistrationFee.toDouble(),
            stockExchangeAssetTradeNoticeFee = stockExchangeAssetTradeNoticeFee.toDouble(),
            stockExchangeFee = stockExchangeFee.toDouble(),
            clearingCustodyFee = clearingCustodyFee.toDouble(),
            incomeTax = incomeTax.toDouble(),
            taxes = taxes.toDouble(),
            netValue = netValue.toDouble(),
            createdAt = createdAt,
        )

    @Suppress("UNCHECKED_CAST")
    private fun Map<String, Any?>.toRecordCommand(accountId: UUID): RecordInvestmentTransactionCommand {
        val brokerNoteMap = this["brokerNote"] as? Map<String, Any?>
        val manualValuationMap = this["manualValuation"] as? Map<String, Any?>

        fun Any?.toLocalDateOrNull(): LocalDate? = when (this) {
            null -> null
            is LocalDate -> this
            is String -> LocalDate.parse(this)
            else -> null
        }

        return RecordInvestmentTransactionCommand(
            financialAccountId = UUID.fromString(this["financialAccountId"] as String),
            assetId = UUID.fromString(this["assetId"] as String),
            direction = InvestmentTransactionDirection.valueOf(this["direction"] as String),
            type = InvestmentTransactionType.valueOf(this["type"] as String),
            typeAdditionalInfo = this["typeAdditionalInfo"] as? String,
            quantity = (this["quantity"] as? Number)?.let { BigDecimal(it.toString()) },
            unitPrice = (this["unitPrice"] as? Number)?.let { BigDecimal(it.toString()) },
            grossValue = BigDecimal((this["grossValue"] as Number).toString()),
            netValue = (this["netValue"] as? Number)?.let { BigDecimal(it.toString()) },
            incomeTax = (this["incomeTax"] as? Number)?.let { BigDecimal(it.toString()) },
            iof = (this["iof"] as? Number)?.let { BigDecimal(it.toString()) },
            indexerPercentage = (this["indexerPercentage"] as? Number)?.let { BigDecimal(it.toString()) },
            fixedAnnualRate = (this["fixedAnnualRate"] as? Number)?.let { BigDecimal(it.toString()) },
            priceFactor = (this["priceFactor"] as? Number)?.let { BigDecimal(it.toString()) },
            tradeDate = this["tradeDate"].toLocalDateOrNull()
                ?: error("tradeDate must be a LocalDate or ISO-8601 string"),
            settlementDate = this["settlementDate"].toLocalDateOrNull(),
            source = InvestmentSource.MANUAL,
            brokerNote = brokerNoteMap?.toBrokerNote(accountId),
            indexer = (this["indexer"] as? String)?.let { Indexer.valueOf(it) },
            issueDate = this["issueDate"].toLocalDateOrNull(),
            maturityDate = this["maturityDate"].toLocalDateOrNull(),
            gracePeriodDate = this["gracePeriodDate"].toLocalDateOrNull(),
            isTaxExempt = (this["isTaxExempt"] as? Boolean) ?: false,
            providerInvestmentId = this["providerInvestmentId"] as? String,
            pricingMode = (this["pricingMode"] as? String)?.let(HoldingPricingMode::valueOf),
            manualValuation = manualValuationMap?.toManualFundValuation(),
        )
    }

    private fun Map<String, Any?>.toManualFundValuation(): ManualFundValuation =
        ManualFundValuation(
            grossValue = BigDecimal((this["grossValue"] as Number).toString()),
            valuedAt = when (val raw = this["valuedAt"]) {
                is LocalDate -> raw
                is String -> LocalDate.parse(raw)
                else -> error("manualValuation.valuedAt must be a LocalDate or ISO-8601 string")
            },
        )

    @Suppress("UNCHECKED_CAST")
    private fun Map<String, Any?>.toBrokerNote(accountId: UUID): BrokerNote =
        BrokerNote(
            brokerNoteNumber = this["brokerNoteNumber"] as? String,
            grossValue = BigDecimal((this["grossValue"] as Number).toString()),
            brokerageFee = (this["brokerageFee"] as? Number)
                ?.let { BigDecimal(it.toString()) } ?: BigDecimal.ZERO,
            clearingSettlementFee = (this["clearingSettlementFee"] as? Number)
                ?.let { BigDecimal(it.toString()) } ?: BigDecimal.ZERO,
            clearingRegistrationFee = (this["clearingRegistrationFee"] as? Number)
                ?.let { BigDecimal(it.toString()) } ?: BigDecimal.ZERO,
            stockExchangeAssetTradeNoticeFee = (this["stockExchangeAssetTradeNoticeFee"] as? Number)
                ?.let { BigDecimal(it.toString()) } ?: BigDecimal.ZERO,
            stockExchangeFee = (this["stockExchangeFee"] as? Number)
                ?.let { BigDecimal(it.toString()) } ?: BigDecimal.ZERO,
            clearingCustodyFee = (this["clearingCustodyFee"] as? Number)
                ?.let { BigDecimal(it.toString()) } ?: BigDecimal.ZERO,
            incomeTax = (this["incomeTax"] as? Number)
                ?.let { BigDecimal(it.toString()) } ?: BigDecimal.ZERO,
            taxes = (this["taxes"] as? Number)
                ?.let { BigDecimal(it.toString()) } ?: BigDecimal.ZERO,
            netValue = BigDecimal((this["netValue"] as Number).toString()),
            accountId = accountId,
        )

    private fun requireAccountId(principal: RequestPrincipal): UUID =
        requireNotNull(principal.currentAccountId) { "No active account in token" }
}
