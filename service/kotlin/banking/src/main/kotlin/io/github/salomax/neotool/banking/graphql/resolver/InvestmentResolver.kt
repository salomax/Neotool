package io.github.salomax.neotool.banking.graphql.resolver

import io.github.salomax.neotool.banking.domain.account.FinancialAccount
import io.github.salomax.neotool.banking.domain.account.FinancialAccountRepository
import io.github.salomax.neotool.banking.domain.investment.Asset
import io.github.salomax.neotool.banking.domain.investment.AssetType
import io.github.salomax.neotool.banking.domain.investment.AssetRepository
import io.github.salomax.neotool.banking.domain.investment.BrokerNote
import io.github.salomax.neotool.banking.domain.investment.Holding
import io.github.salomax.neotool.banking.domain.investment.HoldingRepository
import io.github.salomax.neotool.banking.domain.investment.Indexer
import io.github.salomax.neotool.banking.domain.investment.InvestmentSource
import io.github.salomax.neotool.banking.domain.investment.InvestmentTransaction
import io.github.salomax.neotool.banking.domain.investment.InvestmentTransactionDirection
import io.github.salomax.neotool.banking.domain.investment.InvestmentTransactionService
import io.github.salomax.neotool.banking.domain.investment.InvestmentTransactionType
import io.github.salomax.neotool.banking.domain.investment.RecordInvestmentTransactionCommand
import io.github.salomax.neotool.banking.domain.issuer.IssuerRepository
import io.github.salomax.neotool.banking.graphql.dto.BankAccountDataDTO
import io.github.salomax.neotool.banking.graphql.dto.CardDataDTO
import io.github.salomax.neotool.banking.graphql.dto.BrokerNoteDTO
import io.github.salomax.neotool.banking.graphql.dto.FinancialAccountDTO
import io.github.salomax.neotool.banking.graphql.dto.InvestmentAssetDTO
import io.github.salomax.neotool.banking.graphql.dto.InvestmentHoldingDTO
import io.github.salomax.neotool.banking.graphql.dto.InvestmentTransactionDTO
import io.github.salomax.neotool.banking.graphql.dto.IssuerDTO
import io.github.salomax.neotool.banking.domain.issuer.Issuer
import io.github.salomax.neotool.common.security.principal.RequestPrincipal
import jakarta.inject.Singleton
import java.math.BigDecimal
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID

/**
 * GraphQL resolver for investment queries and mutations.
 * Delegates business logic to [InvestmentTransactionService] and [HoldingRepository].
 */
@Singleton
class InvestmentResolver(
    private val investmentTransactionService: InvestmentTransactionService,
    private val holdingRepository: HoldingRepository,
    private val assetRepository: AssetRepository,
    private val financialAccountRepository: FinancialAccountRepository,
    private val issuerRepository: IssuerRepository,
) {
    // ── Queries ───────────────────────────────────────────────────────────────

    fun investmentHoldings(
        financialAccountId: String,
        principal: RequestPrincipal,
    ): List<InvestmentHoldingDTO> {
        val accountId = requireAccountId(principal)
        val faId = UUID.fromString(financialAccountId)
        val accountsById = financialAccountRepository.findByAccountId(accountId).associateBy { it.id!! }
        val issuersById = issuerRepository.findByIds(accountsById.values.mapNotNull { it.issuerId }.toSet()).associateBy { it.id!! }
        return holdingRepository
            .findByFinancialAccount(faId, accountId)
            .filter { it.quantity > BigDecimal.ZERO }
            .map { it.toDTO(accountsById[it.financialAccountId], issuersById) }
    }

    fun allInvestmentHoldings(principal: RequestPrincipal): List<InvestmentHoldingDTO> {
        val accountId = requireAccountId(principal)
        val accountsById = financialAccountRepository.findByAccountId(accountId).associateBy { it.id!! }
        val issuersById = issuerRepository.findByIds(accountsById.values.mapNotNull { it.issuerId }.toSet()).associateBy { it.id!! }
        return holdingRepository
            .findByAccount(accountId)
            .filter { it.quantity > BigDecimal.ZERO }
            .map { it.toDTO(accountsById[it.financialAccountId], issuersById) }
    }

    fun investmentHolding(
        id: String,
        principal: RequestPrincipal,
    ): InvestmentHoldingDTO? {
        val accountId = requireAccountId(principal)
        val holding = holdingRepository.findById(UUID.fromString(id), accountId) ?: return null
        val accountsById = financialAccountRepository.findByAccountId(accountId).associateBy { it.id!! }
        val issuersById = issuerRepository.findByIds(accountsById.values.mapNotNull { it.issuerId }.toSet()).associateBy { it.id!! }
        return holding.toDTO(accountsById[holding.financialAccountId], issuersById)
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

    fun investmentAsset(id: String): InvestmentAssetDTO? =
        assetRepository.findById(UUID.fromString(id))?.toDTO()

    // ── Mutations ─────────────────────────────────────────────────────────────

    fun createInvestmentAsset(
        input: Map<String, Any?>,
        principal: RequestPrincipal,
    ): InvestmentAssetDTO {
        // Mutation is authorized at GraphQL wiring level; assets are global (not tenant-scoped).
        requireNotNull(principal.userId) { "No user in token" }

        val name = (input["name"] as? String)?.trim().orEmpty()
        require(name.isNotBlank()) { "name is required" }

        val assetTypeRaw = (input["assetType"] as? String)?.trim().orEmpty()
        require(assetTypeRaw.isNotBlank()) { "assetType is required" }

        val asset =
            Asset(
                name = name,
                assetType = AssetType.valueOf(assetTypeRaw),
                issuerName = (input["issuerName"] as? String)?.trim()?.takeIf { it.isNotBlank() },
                ticker = (input["ticker"] as? String)?.trim()?.takeIf { it.isNotBlank() },
                isinCode = (input["isinCode"] as? String)?.trim()?.takeIf { it.isNotBlank() },
                currencyCode = (input["currencyCode"] as? String)?.trim()?.takeIf { it.isNotBlank() } ?: "BRL",
            )

        return assetRepository.save(asset).toDTO()
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

    // ── Mapping helpers ───────────────────────────────────────────────────────

    private fun Holding.toDTO(
        financialAccount: FinancialAccount?,
        issuersById: Map<UUID, Issuer>,
    ): InvestmentHoldingDTO {
        val asset = assetId.let { assetRepository.findById(it) }
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
            grossValue = grossValue?.toDouble(),
            netValue = netValue?.toDouble(),
            incomeTax = incomeTax?.toDouble(),
            iof = iof?.toDouble(),
            lastValuedAt = lastValuedAt,
            source = source,
            createdAt = createdAt,
            updatedAt = updatedAt,
        )
    }

    private fun FinancialAccount.toDTO(issuer: Issuer?) =
        FinancialAccountDTO(
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
            agreedRate = agreedRate?.toDouble(),
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

    private fun getDate(
        map: Map<String, Any?>,
        key: String,
    ): LocalDate? {
        val v = map[key] ?: return null
        return when (v) {
            is String -> LocalDate.parse(v)
            is LocalDate -> v
            else -> v.toString().let { s -> runCatching { LocalDate.parse(s) }.getOrNull() }
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
