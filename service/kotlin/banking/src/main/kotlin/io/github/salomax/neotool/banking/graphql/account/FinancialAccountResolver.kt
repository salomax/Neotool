package io.github.salomax.neotool.banking.graphql.account

import io.github.salomax.neotool.banking.domain.account.BankData
import io.github.salomax.neotool.banking.domain.account.CardData
import io.github.salomax.neotool.banking.domain.account.CreateFinancialAccountCommand
import io.github.salomax.neotool.banking.domain.account.CreditCardLevel
import io.github.salomax.neotool.banking.domain.account.CreditCardNetwork
import io.github.salomax.neotool.banking.domain.account.FinancialAccount
import io.github.salomax.neotool.banking.service.account.FinancialAccountService
import io.github.salomax.neotool.banking.domain.account.FinancialAccountSubtype
import io.github.salomax.neotool.banking.domain.account.FinancialAccountType
import io.github.salomax.neotool.banking.domain.account.UpdateFinancialAccountCommand
import io.github.salomax.neotool.banking.domain.issuer.Issuer
import io.github.salomax.neotool.banking.repo.issuer.IssuerRepository
import io.github.salomax.neotool.banking.graphql.account.BankAccountDataDTO
import io.github.salomax.neotool.banking.graphql.account.CardDataDTO
import io.github.salomax.neotool.banking.graphql.account.FinancialAccountDTO
import io.github.salomax.neotool.banking.graphql.issuer.IssuerDTO
import io.github.salomax.neotool.common.security.exception.AuthenticationRequiredException
import io.github.salomax.neotool.common.security.principal.RequestPrincipal
import jakarta.inject.Singleton
import java.math.BigDecimal
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID

/**
 * GraphQL resolver for [FinancialAccount] queries and mutations.
 *
 * Issuer data is batch-loaded once per query (small list) to avoid N+1.
 */
@Singleton
class FinancialAccountResolver(
    private val service: FinancialAccountService,
    private val issuerRepository: IssuerRepository,
) {
    fun listByAccount(principal: RequestPrincipal): List<FinancialAccountDTO> {
        val accountId = principal.resolveAccountId()
        val accounts = service.listByAccount(accountId)

        val issuerIds = accounts.mapNotNull { it.issuerId }.toSet()
        val issuersById = issuerRepository.findByIds(issuerIds).associateBy { it.id!! }

        return accounts.map { it.toDTO(issuersById[it.issuerId]) }
    }

    fun create(
        input: Map<String, Any?>,
        principal: RequestPrincipal,
    ): FinancialAccountDTO {
        val accountId = principal.resolveAccountId()
        val userId = requireNotNull(principal.userId) { "No user in token" }

        val command = input.toCommand()
        val saved = service.create(command, accountId, userId)
        val issuer = saved.issuerId?.let { issuerRepository.findById(it) }
        return saved.toDTO(issuer)
    }

    fun update(
        id: String,
        input: Map<String, Any?>,
        principal: RequestPrincipal,
    ): FinancialAccountDTO {
        val accountId = principal.resolveAccountId()
        val userId = requireNotNull(principal.userId) { "No user in token" }
        val financialAccountId = UUID.fromString(id)

        val command = input.toUpdateCommand()
        val saved = service.update(financialAccountId, command, accountId, userId)
        val issuer = saved.issuerId?.let { issuerRepository.findById(it) }
        return saved.toDTO(issuer)
    }

    // ── Mapping helpers ──────────────────────────────────────────────────────

    private fun FinancialAccount.toDTO(issuer: Issuer?): FinancialAccountDTO =
        FinancialAccountDTO(
            id = id!!,
            type = type,
            subtype = subtype,
            name = name,
            currencyCode = currencyCode,
            includeInNetWorth = includeInNetWorth,
            includeInDashboard = includeInDashboard,
            includeCashInAllocation = includeCashInAllocation,
            themeColor = themeColor,
            issuer = issuer?.toDTO(),
            issuerName = issuerName,
            bankData =
                bankData?.let {
                    BankAccountDataDTO(
                        branchCode = it.branchCode,
                        number = it.number,
                        checkDigit = it.checkDigit,
                        maskedNumber = it.maskedNumber,
                    )
                },
            cardData =
                cardData?.let {
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

    private fun Issuer.toDTO(): IssuerDTO =
        IssuerDTO(
            id = id!!,
            name = name,
            domain = domain,
            compeCode = compeCode,
            ispb = ispb,
            countryCode = countryCode,
            logoKey = logoKey,
            category = category,
            logoAsset = logoKey?.let { mapOf("__typename" to "Asset", "id" to it) },
        )

    private fun RequestPrincipal.resolveAccountId(): UUID =
        currentAccountId ?: accountIds.firstOrNull() ?: throw AuthenticationRequiredException("No active account in token")

    private fun getDate(
        map: Map<String, Any?>,
        key: String,
    ): LocalDate? {
        val value = map[key] ?: return null
        return when (value) {
            is String -> LocalDate.parse(value)
            is LocalDate -> value
            else -> value.toString().let { raw -> runCatching { LocalDate.parse(raw) }.getOrNull() }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun Map<String, Any?>.toCommand(): CreateFinancialAccountCommand {
        val bankDataInput = this["bankData"] as? Map<String, Any?>
        val cardDataInput = this["cardData"] as? Map<String, Any?>
        return CreateFinancialAccountCommand(
            type = FinancialAccountType.valueOf(this["type"] as String),
            subtype = FinancialAccountSubtype.valueOf(this["subtype"] as String),
            name = this["name"] as? String,
            issuerId = (this["issuerId"] as? String)?.let { UUID.fromString(it) },
            issuerName = this["issuerName"] as? String,
            themeColor = this["themeColor"] as? String,
            currencyCode = this["currencyCode"] as? String,
            includeInNetWorth = this["includeInNetWorth"] as? Boolean,
            includeInDashboard = this["includeInDashboard"] as? Boolean,
            includeCashInAllocation = this["includeCashInAllocation"] as? Boolean,
            bankData =
                bankDataInput?.let {
                    BankData(
                        branchCode = it["branchCode"] as? String,
                        number = it["number"] as? String,
                        checkDigit = it["checkDigit"] as? String,
                        maskedNumber = it["maskedNumber"] as? String,
                    )
                },
            cardData =
                cardDataInput?.let {
                    CardData(
                        maskedNumber = it["maskedNumber"] as? String,
                        bin = it["bin"] as? String,
                        cardNetwork = (it["cardNetwork"] as? String)?.let { v -> CreditCardNetwork.valueOf(v) },
                        cardLevel = (it["cardLevel"] as? String)?.let { v -> CreditCardLevel.valueOf(v) },
                        creditLimit = (it["creditLimit"] as? Number)?.let { v -> BigDecimal(v.toString()) },
                        closingDay = it["closingDay"] as? Int,
                        dueDay = it["dueDay"] as? Int,
                        linkedBankAccountId = (it["linkedBankAccountId"] as? String)?.let { v -> UUID.fromString(v) },
                    )
                },
            initialBalance = (this["initialBalance"] as? Number)?.let { BigDecimal(it.toString()) },
            initialBalanceDate = getDate(this, "initialBalanceDate"),
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun Map<String, Any?>.toUpdateCommand(): UpdateFinancialAccountCommand {
        val bankDataInput = this["bankData"] as? Map<String, Any?>
        val cardDataInput = this["cardData"] as? Map<String, Any?>
        return UpdateFinancialAccountCommand(
            type = FinancialAccountType.valueOf(this["type"] as String),
            subtype = FinancialAccountSubtype.valueOf(this["subtype"] as String),
            name = this["name"] as? String,
            issuerId = (this["issuerId"] as? String)?.let { UUID.fromString(it) },
            issuerName = this["issuerName"] as? String,
            themeColor = this["themeColor"] as? String,
            currencyCode = this["currencyCode"] as? String,
            includeInNetWorth = this["includeInNetWorth"] as? Boolean,
            includeInDashboard = this["includeInDashboard"] as? Boolean,
            includeCashInAllocation = this["includeCashInAllocation"] as? Boolean,
            bankData =
                bankDataInput?.let {
                    BankData(
                        branchCode = it["branchCode"] as? String,
                        number = it["number"] as? String,
                        checkDigit = it["checkDigit"] as? String,
                        maskedNumber = it["maskedNumber"] as? String,
                    )
                },
            cardData =
                cardDataInput?.let {
                    CardData(
                        maskedNumber = it["maskedNumber"] as? String,
                        bin = it["bin"] as? String,
                        cardNetwork = (it["cardNetwork"] as? String)?.let { v -> CreditCardNetwork.valueOf(v) },
                        cardLevel = (it["cardLevel"] as? String)?.let { v -> CreditCardLevel.valueOf(v) },
                        creditLimit = (it["creditLimit"] as? Number)?.let { v -> BigDecimal(v.toString()) },
                        closingDay = it["closingDay"] as? Int,
                        dueDay = it["dueDay"] as? Int,
                        linkedBankAccountId = (it["linkedBankAccountId"] as? String)?.let { v -> UUID.fromString(v) },
                    )
                },
        )
    }
}
