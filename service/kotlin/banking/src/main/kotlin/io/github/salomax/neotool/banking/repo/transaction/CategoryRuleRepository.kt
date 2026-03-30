package io.github.salomax.neotool.banking.repo.transaction

import io.github.salomax.neotool.banking.domain.transaction.TransactionRule
import java.time.Instant
import java.util.UUID

/**
 * Domain repository for [TransactionRule].
 */
interface TransactionRuleRepository {
    /** Returns user rules + system rules for the account, ordered by priority (asc), then match_count desc. */
    fun findRulesForAccount(accountId: UUID): List<TransactionRule>

    /** Returns only user-owned rules (explicit + implicit) for the account. Excludes system rules. */
    fun findUserRulesForAccount(accountId: UUID): List<TransactionRule>

    /** Returns only active system/global rules ordered by evaluation precedence. */
    fun findSystemRules(): List<TransactionRule>

    fun findById(id: UUID): TransactionRule?

    fun save(rule: TransactionRule): TransactionRule

    fun softDelete(
        id: UUID,
        accountId: UUID,
    )

    /** Finds an implicit rule for this account with the exact pattern (for learning). */
    fun findImplicitByPattern(
        accountId: UUID,
        pattern: String,
    ): TransactionRule?

    fun incrementMatchCount(id: UUID)

    /** Updates the priority of a single rule (used by reorder). */
    fun updatePriority(
        id: UUID,
        priority: Int,
        now: Instant,
    )
}
