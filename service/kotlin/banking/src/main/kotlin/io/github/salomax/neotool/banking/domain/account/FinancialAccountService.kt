package io.github.salomax.neotool.banking.domain.account

import io.github.salomax.neotool.banking.domain.issuer.IssuerRepository
import io.github.salomax.neotool.banking.domain.transaction.CreateTransactionCommand
import io.github.salomax.neotool.banking.domain.transaction.CreditDebitType
import io.github.salomax.neotool.banking.domain.transaction.TransactionService
import io.github.salomax.neotool.banking.domain.transaction.TransactionStatus
import io.github.salomax.neotool.banking.domain.transaction.TransactionType
import jakarta.inject.Singleton
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/**
 * Domain service for [FinancialAccount] operations.
 *
 * [accountId] is always the tenant account ID resolved from the JWT by the GraphQL layer.
 * [userId] is the authenticated user ID (audit field).
 */
@Singleton
open class FinancialAccountService(
    private val financialAccountRepository: FinancialAccountRepository,
    private val issuerRepository: IssuerRepository,
    private val transactionService: TransactionService,
) {
    fun listByAccount(accountId: UUID): List<FinancialAccount> =
        financialAccountRepository.findByAccountId(accountId)

    fun create(command: CreateFinancialAccountCommand, accountId: UUID, userId: UUID): FinancialAccount {
        // TODO(billing): enforce plan-based quota before creation.
        //   Gate: plan_allows("banking:account:create") AND quota("financial_accounts.max") not exceeded.
        //   Planned limits — FREE: 5, BASIC: 20, PRO: 100.
        //   Implement when the billing service and PlanAccessSnapshot are available.

        require(command.issuerId != null || !command.issuerName.isNullOrBlank()) {
            "Either issuerId or issuerName must be provided"
        }
        if (command.subtype == FinancialAccountSubtype.CREDIT_CARD ||
            command.subtype == FinancialAccountSubtype.CHARGE_CARD
        ) {
            requireNotNull(command.cardData?.closingDay) { "cardData.closingDay is required for ${command.subtype}" }
            requireNotNull(command.cardData?.dueDay) { "cardData.dueDay is required for ${command.subtype}" }
        }

        val issuer = command.issuerId?.let { issuerRepository.findById(it) }

        val effectiveName = command.name?.takeIf { it.isNotBlank() }
            ?: issuer?.name
            ?: command.issuerName
            ?: throw IllegalArgumentException("Account name could not be resolved; provide name or a valid issuerId")

        val now = Instant.now()
        val account = FinancialAccount(
            accountId = accountId,
            createdByUserId = userId,
            type = command.type,
            subtype = command.subtype,
            name = effectiveName,
            currencyCode = command.currencyCode?.takeIf { it.isNotBlank() } ?: "BRL",
            includeInNetWorth = command.includeInNetWorth ?: true,
            includeInDashboard = command.includeInDashboard ?: true,
            issuerId = command.issuerId,
            themeColor = command.themeColor,
            issuerName = issuer?.name ?: command.issuerName,
            issuerCountry = issuer?.countryCode,
            issuerCompeCode = issuer?.compeCode,
            issuerIspb = issuer?.ispb,
            cardLastFourDigits = extractLastFourDigits(command.cardData?.maskedNumber),
            bankData = command.bankData,
            cardData = command.cardData,
            createdAt = now,
            updatedAt = now,
        )

        val saved = financialAccountRepository.save(account)

        if (command.initialBalance != null && command.initialBalance.compareTo(BigDecimal.ZERO) != 0) {
            require(command.initialBalanceDate != null) {
                "initialBalanceDate is required when initialBalance is provided"
            }
            transactionService.create(
                CreateTransactionCommand(
                    financialAccountId = saved.id!!,
                    creditDebitType = if (command.initialBalance > BigDecimal.ZERO) CreditDebitType.CREDIT else CreditDebitType.DEBIT,
                    type = TransactionType.BALANCE_ADJUSTMENT,
                    amount = command.initialBalance.abs(),
                    date = command.initialBalanceDate,
                    description = "Saldo inicial",
                    status = TransactionStatus.POSTED,
                    currencyCode = saved.currencyCode,
                ),
                accountId,
                userId,
            )
        }

        return saved
    }

    private fun extractLastFourDigits(maskedNumber: String?): String? {
        if (maskedNumber.isNullOrBlank()) return null
        val onlyDigits = maskedNumber.filter(Char::isDigit)
        return if (onlyDigits.length >= 4) onlyDigits.takeLast(4) else null
    }
}
