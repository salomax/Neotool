package io.github.salomax.neotool.banking.domain.transaction

import java.util.UUID

/**
 * Domain repository for [CategoryRule].
 */
interface CategoryRuleRepository {
    /** Returns user rules + system rules for the account, ordered by priority (asc), then match_count desc. */
    fun findRulesForAccount(accountId: UUID): List<CategoryRule>

    /** Returns only user-owned rules (explicit + implicit) for the account. Excludes system rules. */
    fun findUserRulesForAccount(accountId: UUID): List<CategoryRule>

    fun findById(id: UUID): CategoryRule?

    fun save(rule: CategoryRule): CategoryRule

    fun softDelete(
        id: UUID,
        accountId: UUID,
    )

    /** Finds an implicit rule for this account with the exact pattern (for learning). */
    fun findImplicitByPattern(
        accountId: UUID,
        pattern: String,
    ): CategoryRule?

    fun incrementMatchCount(id: UUID)
}
