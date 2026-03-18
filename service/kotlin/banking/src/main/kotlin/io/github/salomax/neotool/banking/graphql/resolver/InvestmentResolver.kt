package io.github.salomax.neotool.banking.graphql.resolver

import io.github.salomax.neotool.banking.domain.investment.Asset
import io.github.salomax.neotool.banking.domain.investment.AssetType
import io.github.salomax.neotool.banking.domain.investment.BrokerNote
import io.github.salomax.neotool.banking.domain.investment.InvestmentAssetService
import io.github.salomax.neotool.banking.domain.investment.InvestmentSource
import io.github.salomax.neotool.banking.domain.investment.InvestmentTransaction
import io.github.salomax.neotool.banking.domain.investment.InvestmentTransactionDirection
import io.github.salomax.neotool.banking.domain.investment.InvestmentTransactionService
import io.github.salomax.neotool.banking.domain.investment.InvestmentTransactionType
import io.github.salomax.neotool.banking.domain.investment.Indexer
import io.github.salomax.neotool.banking.domain.investment.RecordInvestmentTransactionCommand
import io.github.salomax.neotool.banking.graphql.InvestmentQueryService
import io.github.salomax.neotool.banking.graphql.dto.BrokerNoteDTO
import io.github.salomax.neotool.banking.graphql.dto.InvestmentAssetDTO
import io.github.salomax.neotool.banking.graphql.dto.InvestmentHoldingDTO
import io.github.salomax.neotool.banking.graphql.dto.InvestmentTransactionDTO
import io.github.salomax.neotool.common.security.principal.RequestPrincipal
import jakarta.inject.Singleton
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

/**
 * GraphQL resolver for investment queries and mutations.
 * Delegates all data access and orchestration to [InvestmentQueryService],
 * [InvestmentTransactionService], and [InvestmentAssetService].
 */
@Singleton
class InvestmentResolver(
    private val investmentQueryService: InvestmentQueryService,
    private val investmentTransactionService: InvestmentTransactionService,
    private val investmentAssetService: InvestmentAssetService,
) {
    // ── Queries ───────────────────────────────────────────────────────────────

    fun investmentHoldings(
        financialAccountId: String,
        principal: RequestPrincipal,
    ): List<InvestmentHoldingDTO> =
        investmentQueryService.getHoldingsByFinancialAccount(
            UUID.fromString(financialAccountId),
            requireAccountId(principal),
        )

    fun allInvestmentHoldings(principal: RequestPrincipal): List<InvestmentHoldingDTO> =
        investmentQueryService.getAllHoldings(requireAccountId(principal))

    fun investmentHolding(
        id: String,
        principal: RequestPrincipal,
    ): InvestmentHoldingDTO? =
        investmentQueryService.getHolding(UUID.fromString(id), requireAccountId(principal))

    fun investmentTransactions(
        holdingId: String,
        principal: RequestPrincipal,
    ): List<InvestmentTransactionDTO> =
        investmentTransactionService
            .listByHolding(UUID.fromString(holdingId), requireAccountId(principal))
            .map { it.toDTO() }

    fun investmentTransaction(
        id: String,
        principal: RequestPrincipal,
    ): InvestmentTransactionDTO =
        investmentTransactionService.findById(UUID.fromString(id), requireAccountId(principal)).toDTO()

    fun investmentAsset(id: String): InvestmentAssetDTO? =
        investmentAssetService.findById(UUID.fromString(id))?.toDTO()

    // ── Mutations ─────────────────────────────────────────────────────────────

    fun createInvestmentAsset(
        input: Map<String, Any?>,
        principal: RequestPrincipal,
    ): InvestmentAssetDTO {
        requireNotNull(principal.userId) { "No user in token" }

        val name = (input["name"] as? String)?.trim().orEmpty()
        val assetTypeRaw = (input["assetType"] as? String)?.trim().orEmpty()
        require(assetTypeRaw.isNotBlank()) { "assetType is required" }
        val assetType = try {
            AssetType.valueOf(assetTypeRaw)
        } catch (_: IllegalArgumentException) {
            throw IllegalArgumentException("Unknown assetType: $assetTypeRaw")
        }

        return investmentAssetService.create(
            name = name,
            assetType = assetType,
            issuerName = (input["issuerName"] as? String)?.trim()?.takeIf { it.isNotBlank() },
            ticker = (input["ticker"] as? String)?.trim()?.takeIf { it.isNotBlank() },
            isinCode = (input["isinCode"] as? String)?.trim()?.takeIf { it.isNotBlank() },
            currencyCode = (input["currencyCode"] as? String)?.trim()?.takeIf { it.isNotBlank() } ?: "BRL",
        ).toDTO()
    }

    fun recordInvestmentTransaction(
        input: Map<String, Any?>,
        principal: RequestPrincipal,
    ): InvestmentTransactionDTO {
        val accountId = requireAccountId(principal)
        val userId = requireNotNull(principal.userId) { "No user in token" }
        return investmentTransactionService.record(input.toRecordCommand(accountId), accountId, userId).toDTO()
    }

    fun deleteInvestmentTransaction(
        id: String,
        principal: RequestPrincipal,
    ): Boolean =
        investmentTransactionService.delete(UUID.fromString(id), requireAccountId(principal))

    // ── DTO mapping ───────────────────────────────────────────────────────────

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

    private fun InvestmentTransaction.toDTO() = InvestmentTransactionDTO(
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
        agreedRate = agreedRate?.toDouble(),
        priceFactor = priceFactor?.toDouble(),
        tradeDate = tradeDate,
        settlementDate = settlementDate,
        source = source,
        brokerNote = brokerNote?.toDTO(),
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

    private fun BrokerNote.toDTO() = BrokerNoteDTO(
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

    // ── Input parsing ─────────────────────────────────────────────────────────

    private fun getDate(map: Map<String, Any?>, key: String): LocalDate? {
        val v = map[key] ?: return null
        return when (v) {
            is String -> LocalDate.parse(v)
            is LocalDate -> v
            else -> runCatching { LocalDate.parse(v.toString()) }.getOrNull()
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun Map<String, Any?>.toRecordCommand(accountId: UUID): RecordInvestmentTransactionCommand {
        val brokerNoteMap = this["brokerNote"] as? Map<String, Any?>
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
            agreedRate = (this["agreedRate"] as? Number)?.let { BigDecimal(it.toString()) },
            priceFactor = (this["priceFactor"] as? Number)?.let { BigDecimal(it.toString()) },
            tradeDate = getDate(this, "tradeDate") ?: throw IllegalArgumentException("tradeDate is required"),
            settlementDate = getDate(this, "settlementDate"),
            source = InvestmentSource.MANUAL,
            brokerNote = brokerNoteMap?.toBrokerNote(accountId),
            indexer = (this["indexer"] as? String)?.let { Indexer.valueOf(it) },
            preFixedRate = (this["preFixedRate"] as? Number)?.let { BigDecimal(it.toString()) },
            issueDate = getDate(this, "issueDate"),
            maturityDate = getDate(this, "maturityDate"),
            gracePeriodDate = getDate(this, "gracePeriodDate"),
            isTaxExempt = (this["isTaxExempt"] as? Boolean) ?: false,
            providerInvestmentId = this["providerInvestmentId"] as? String,
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun Map<String, Any?>.toBrokerNote(accountId: UUID): BrokerNote =
        BrokerNote(
            brokerNoteNumber = this["brokerNoteNumber"] as? String,
            grossValue = BigDecimal((this["grossValue"] as Number).toString()),
            brokerageFee = (this["brokerageFee"] as? Number)?.let { BigDecimal(it.toString()) } ?: BigDecimal.ZERO,
            clearingSettlementFee = (this["clearingSettlementFee"] as? Number)?.let { BigDecimal(it.toString()) } ?: BigDecimal.ZERO,
            clearingRegistrationFee = (this["clearingRegistrationFee"] as? Number)?.let { BigDecimal(it.toString()) } ?: BigDecimal.ZERO,
            stockExchangeAssetTradeNoticeFee = (this["stockExchangeAssetTradeNoticeFee"] as? Number)?.let { BigDecimal(it.toString()) } ?: BigDecimal.ZERO,
            stockExchangeFee = (this["stockExchangeFee"] as? Number)?.let { BigDecimal(it.toString()) } ?: BigDecimal.ZERO,
            clearingCustodyFee = (this["clearingCustodyFee"] as? Number)?.let { BigDecimal(it.toString()) } ?: BigDecimal.ZERO,
            incomeTax = (this["incomeTax"] as? Number)?.let { BigDecimal(it.toString()) } ?: BigDecimal.ZERO,
            taxes = (this["taxes"] as? Number)?.let { BigDecimal(it.toString()) } ?: BigDecimal.ZERO,
            netValue = BigDecimal((this["netValue"] as Number).toString()),
            accountId = accountId,
        )

    private fun requireAccountId(principal: RequestPrincipal): UUID =
        requireNotNull(principal.currentAccountId) { "No active account in token" }
}
