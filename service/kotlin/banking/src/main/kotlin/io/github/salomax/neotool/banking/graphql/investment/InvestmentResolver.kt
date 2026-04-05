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
import io.github.salomax.neotool.banking.graphql.transaction.PageInfoDTO
import io.github.salomax.neotool.banking.graphql.transaction.TransactionConnectionDTO
import io.github.salomax.neotool.banking.graphql.transaction.TransactionDTO
import io.github.salomax.neotool.banking.graphql.transaction.TransactionEdgeDTO
import io.github.salomax.neotool.banking.graphql.transaction.TransactionResolver
import io.github.salomax.neotool.banking.domain.account.FinancialAccountType
import io.github.salomax.neotool.banking.repo.account.FinancialAccountRepository
import io.github.salomax.neotool.banking.service.investment.InvestmentCashTransactionCursor
import io.github.salomax.neotool.banking.service.investment.InvestmentCashService
import io.github.salomax.neotool.banking.service.investment.PortfolioFlowEventService
import io.github.salomax.neotool.banking.service.investment.InvestmentPortfolioSummaryService
import io.github.salomax.neotool.banking.service.investment.InvestmentCashTransactionType
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
import io.github.salomax.neotool.banking.graphql.investment.InvestmentHoldingPerformanceDTO
import io.github.salomax.neotool.banking.graphql.investment.InvestmentPortfolioSummaryDTO
import io.github.salomax.neotool.banking.graphql.investment.InvestmentTransactionDTO
import io.github.salomax.neotool.banking.graphql.investment.PerformancePeriodDTO
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
    private val investmentPortfolioSummaryService: InvestmentPortfolioSummaryService,
    private val assetRepository: AssetRepository,
    private val investmentCashService: InvestmentCashService,
    private val transactionResolver: TransactionResolver,
    private val portfolioFlowEventService: PortfolioFlowEventService,
    private val financialAccountRepository: FinancialAccountRepository,
) {
    // ── Queries ───────────────────────────────────────────────────────────────

    fun investmentHoldings(
        financialAccountId: String?,
        principal: RequestPrincipal,
    ): List<InvestmentHoldingDTO> {
        val accountId = requireAccountId(principal)
        return investmentQueryService.getHoldings(
            financialAccountId = financialAccountId?.let(UUID::fromString),
            accountId = accountId,
        )
    }

    fun investmentCashBalance(
        financialAccountId: String?,
        principal: RequestPrincipal,
    ): Double {
        val accountId = requireAccountId(principal)
        return investmentQueryService
            .getInvestmentCashBalance(
                financialAccountId = financialAccountId?.let(UUID::fromString),
                accountId = accountId,
            )
            .toDouble()
    }

    fun investmentPortfolioSummary(
        financialAccountId: String?,
        period: String?,
        principal: RequestPrincipal,
    ): InvestmentPortfolioSummaryDTO {
        val accountId = requireAccountId(principal)
        return investmentPortfolioSummaryService.getSummary(
            financialAccountId = financialAccountId?.let(UUID::fromString),
            accountId = accountId,
            period = period?.let(PerformancePeriodDTO::valueOf) ?: PerformancePeriodDTO.YTD,
        )
    }

    fun investmentCashTransactions(
        financialAccountId: String?,
        first: Int?,
        after: String?,
        principal: RequestPrincipal,
    ): TransactionConnectionDTO {
        val accountId = requireAccountId(principal)
        val slice =
            investmentQueryService.listInvestmentCashTransactions(
                financialAccountId = financialAccountId?.let(UUID::fromString),
                accountId = accountId,
                first = first,
                after = after,
            )
        val dtos = transactionResolver.toTransactionDTOBatch(slice.items, accountId)
        val edges =
            slice.items.indices.map { i ->
                val tx = slice.items[i]
                TransactionEdgeDTO(
                    node = dtos[i],
                    cursor =
                        InvestmentCashTransactionCursor.encode(
                            tx.date,
                            requireNotNull(tx.id),
                        ),
                )
            }
        return TransactionConnectionDTO(
            edges = edges,
            pageInfo =
                PageInfoDTO(
                    hasNextPage = slice.hasNextPage,
                    hasPreviousPage = !after.isNullOrBlank(),
                    startCursor = slice.startCursor,
                    endCursor = slice.endCursor,
                ),
            totalCount = null,
        )
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

    fun investmentHoldingPerformance(
        holdingId: String,
        principal: RequestPrincipal,
    ): InvestmentHoldingPerformanceDTO? {
        val accountId = requireAccountId(principal)
        return investmentQueryService.getInvestmentHoldingPerformance(UUID.fromString(holdingId), accountId)
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

    fun createInvestmentCashTransaction(
        input: Map<String, Any?>,
        principal: RequestPrincipal,
    ): TransactionDTO {
        val accountId = requireAccountId(principal)
        val userId = requireNotNull(principal.userId) { "No user in token" }
        val type = InvestmentCashTransactionType.valueOf(input["type"] as String)
        val financialAccountId = UUID.fromString(input["financialAccountId"] as String)
        val amount = BigDecimal.valueOf((input["amount"] as Number).toDouble())
        val date =
            when (val raw = input["date"]) {
                is LocalDate -> raw
                is String -> LocalDate.parse(raw)
                else -> error("date must be a LocalDate or ISO-8601 string")
            }
        val description = input["description"] as? String
        val peerFinancialAccountId = (input["peerFinancialAccountId"] as? String)?.let(UUID::fromString)
        val registerAsBalanceAdjustment = input["registerAsBalanceAdjustment"] as? Boolean ?: false
        val saved =
            investmentCashService.createManualCashTransaction(
                financialAccountId = financialAccountId,
                type = type,
                amount = amount,
                date = date,
                description = description,
                accountId = accountId,
                userId = userId,
                peerFinancialAccountId = peerFinancialAccountId,
                registerAsBalanceAdjustment = registerAsBalanceAdjustment,
            )
        return transactionResolver.toTransactionDTO(saved, accountId)
    }

    fun updateInvestmentCashTransaction(
        id: String,
        input: Map<String, Any?>,
        principal: RequestPrincipal,
    ): TransactionDTO {
        val accountId = requireAccountId(principal)
        val userId = requireNotNull(principal.userId) { "No user in token" }
        val amount = BigDecimal.valueOf((input["amount"] as Number).toDouble())
        val date =
            when (val raw = input["date"]) {
                is LocalDate -> raw
                is String -> LocalDate.parse(raw)
                else -> error("date must be a LocalDate or ISO-8601 string")
            }
        val description = input["description"] as? String
        val saved =
            investmentCashService.updateManualCashLedgerTransaction(
                transactionId = UUID.fromString(id),
                amount = amount,
                date = date,
                description = description,
                accountId = accountId,
                userId = userId,
            )
        return transactionResolver.toTransactionDTO(saved, accountId)
    }

    fun deleteInvestmentCashTransaction(
        id: String,
        principal: RequestPrincipal,
    ): Boolean {
        val accountId = requireAccountId(principal)
        return investmentCashService.deleteManualCashLedgerTransaction(UUID.fromString(id), accountId)
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

    fun triggerPortfolioRecalculation(
        financialAccountId: String?,
        recalculateFrom: String?,
        principal: RequestPrincipal,
    ): Boolean {
        val accountId = requireAccountId(principal)
        val from = recalculateFrom?.let { LocalDate.parse(it) } ?: LocalDate.now().minusYears(10)
        val accounts = if (financialAccountId != null) {
            listOfNotNull(financialAccountRepository.findById(UUID.fromString(financialAccountId), accountId))
        } else {
            financialAccountRepository.findByAccountId(accountId)
                .filter { account -> account.type == FinancialAccountType.INVESTMENT }
        }
        for (account in accounts) {
            portfolioFlowEventService.requestRecalculation(
                financialAccountId = account.id,
                accountId = accountId,
                recalculateFrom = from,
                triggeredBy = "manual_trigger",
            )
        }
        return true
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
