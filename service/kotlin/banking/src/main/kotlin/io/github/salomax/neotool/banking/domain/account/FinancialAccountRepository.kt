package io.github.salomax.neotool.banking.domain.account

import java.util.UUID

/**
 * Domain repository for [FinancialAccount].
 * Scoped by accountId (security.accounts); soft-delete is transparent to callers.
 */
interface FinancialAccountRepository {
    fun findByAccountId(accountId: UUID): List<FinancialAccount>
    fun save(account: FinancialAccount): FinancialAccount
}
