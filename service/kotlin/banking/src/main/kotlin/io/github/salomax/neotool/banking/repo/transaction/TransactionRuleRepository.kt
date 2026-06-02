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

    /** Returns rule ids disabled for this account (used to mark system rules in UI). */
    fun findDisabledRuleIdsForAccount(accountId: UUID): Set<UUID>

    /** Disables a system/global rule for this account without mutating the source rule. */
    fun disableSystemRuleForAccount(
        accountId: UUID,
        ruleId: UUID,
    ): Boolean

    /** Re-enables a previously disabled system rule for this account. */
    fun enableSystemRuleForAccount(
        accountId: UUID,
        ruleId: UUID,
    ): Boolean

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

    fun incrementMatchCountBy(
        id: UUID,
        delta: Int,
    )

    /** Updates the priority of a single rule (used by reorder). */
    fun updatePriority(
        id: UUID,
        priority: Int,
        now: Instant,
    )
}
