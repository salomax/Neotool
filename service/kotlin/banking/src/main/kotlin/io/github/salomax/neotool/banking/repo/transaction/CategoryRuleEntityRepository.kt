package io.github.salomax.neotool.banking.repo.transaction

import io.github.salomax.neotool.banking.model.transaction.CategoryRuleEntity
import io.micronaut.data.annotation.Query
import io.micronaut.data.annotation.Repository
import io.micronaut.data.jpa.repository.JpaRepository
import java.time.Instant
import java.util.UUID

/**
 * JPA repository for banking.transaction_rules (entity layer).
 */
@Repository
interface CategoryRuleEntityRepository : JpaRepository<CategoryRuleEntity, UUID> {
    @Query(
        """
        SELECT r FROM io.github.salomax.neotool.banking.model.transaction.CategoryRuleEntity r
        WHERE r.deletedAt IS NULL
          AND (r.accountId IS NULL OR r.accountId = :accountId)
        ORDER BY r.priority ASC, r.matchCount DESC, r.createdAt DESC
        """,
    )
    fun findRulesForAccount(accountId: UUID): List<CategoryRuleEntity>

    @Query(
        """
        SELECT r FROM io.github.salomax.neotool.banking.model.transaction.CategoryRuleEntity r
        WHERE r.deletedAt IS NULL AND r.accountId = :accountId
        ORDER BY r.priority ASC, r.matchCount DESC, r.createdAt DESC
        """,
    )
    fun findUserRulesForAccount(accountId: UUID): List<CategoryRuleEntity>

    @Query(
        """
        SELECT r FROM io.github.salomax.neotool.banking.model.transaction.CategoryRuleEntity r
        WHERE r.deletedAt IS NULL
          AND r.isSystem = true
          AND r.accountId IS NULL
        ORDER BY r.priority ASC, r.matchCount DESC, r.createdAt DESC
        """,
    )
    fun findSystemRules(): List<CategoryRuleEntity>

    fun findByIdAndDeletedAtIsNull(id: UUID): CategoryRuleEntity?

    fun findByAccountIdAndPatternAndIsImplicitAndDeletedAtIsNull(
        accountId: UUID,
        pattern: String,
        isImplicit: Boolean,
    ): CategoryRuleEntity?

    @Query(
        """
        UPDATE banking.transaction_rules
        SET deleted_at = :now, updated_at = :now
        WHERE id = :id AND account_id = :accountId AND deleted_at IS NULL
        """,
        nativeQuery = true,
    )
    fun softDelete(
        id: UUID,
        accountId: UUID,
        now: Instant,
    ): Int

    @Query(
        """
        UPDATE banking.transaction_rules
        SET match_count = match_count + 1, updated_at = :now
        WHERE id = :id AND deleted_at IS NULL
        """,
        nativeQuery = true,
    )
    fun incrementMatchCount(
        id: UUID,
        now: Instant,
    )

    @Query(
        """
        UPDATE banking.transaction_rules
        SET priority = :priority, updated_at = :now
        WHERE id = :id AND deleted_at IS NULL
        """,
        nativeQuery = true,
    )
    fun updatePriority(
        id: UUID,
        priority: Int,
        now: Instant,
    )
}
