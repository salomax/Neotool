package io.github.salomax.neotool.banking.domain.account

import java.util.UUID

/**
 * Domain repository for [FinancialAccount].
 * Scoped by accountId (tenant ID from JWT); soft-delete is transparent to callers.
 */
interface FinancialAccountRepository {
    fun findByAccountId(accountId: UUID): List<FinancialAccount>
    fun findCardAccountsByLastFourDigits(accountId: UUID, lastFourDigits: String): List<FinancialAccount>
    fun save(account: FinancialAccount): FinancialAccount
}
