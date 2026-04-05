package io.github.salomax.neotool.banking.repo.account

import io.github.salomax.neotool.banking.domain.account.FinancialAccount
import java.util.UUID

/**
 * Domain repository for [FinancialAccount].
 * Scoped by accountId (tenant ID from JWT); soft-delete is transparent to callers.
 */
interface FinancialAccountRepository {
    fun findByAccountId(accountId: UUID): List<FinancialAccount>

    fun findById(
        id: UUID,
        accountId: UUID,
    ): FinancialAccount?

    fun findAllInvestmentAccounts(): List<FinancialAccount>

    fun findCardAccountsByLastFourDigits(
        accountId: UUID,
        lastFourDigits: String,
    ): List<FinancialAccount>

    fun save(account: FinancialAccount): FinancialAccount

    fun update(account: FinancialAccount): FinancialAccount
}
