package io.github.salomax.neotool.banking.domain.account

import io.github.salomax.neotool.banking.domain.issuer.IssuerRepository
import jakarta.inject.Singleton
import java.time.Instant
import java.util.UUID

/**
 * Domain service for [FinancialAccount] operations.
 *
 * [accountId] is always the security.accounts ID (tenant) resolved from the JWT by the GraphQL layer.
 * [userId] is the security.users ID of the authenticated user (audit field).
 */
@Singleton
open class FinancialAccountService(
    private val financialAccountRepository: FinancialAccountRepository,
    private val issuerRepository: IssuerRepository,
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
            bankData = command.bankData,
            cardData = command.cardData,
            createdAt = now,
            updatedAt = now,
        )

        return financialAccountRepository.save(account)
    }
}
