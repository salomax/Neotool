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
        SELECT r.*
        FROM banking.transaction_rules r
        WHERE r.deleted_at IS NULL
          AND (r.account_id = :accountId OR (r.account_id IS NULL AND r.id NOT IN (
            SELECT adr.rule_id
            FROM banking.account_disabled_rules adr
            WHERE adr.account_id = :accountId
          )))
        ORDER BY r.priority ASC, r.match_count DESC, r.created_at DESC
        """,
        nativeQuery = true,
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

    @Query(
        """
        SELECT adr.rule_id
        FROM banking.account_disabled_rules adr
        WHERE adr.account_id = :accountId
        """,
        nativeQuery = true,
    )
    fun findDisabledRuleIdsForAccount(accountId: UUID): List<UUID>

    @Query(
        """
        INSERT INTO banking.account_disabled_rules (account_id, rule_id, created_at)
        SELECT :accountId, :ruleId, :now
        WHERE EXISTS (
          SELECT 1
          FROM banking.transaction_rules r
          WHERE r.id = :ruleId
            AND r.deleted_at IS NULL
            AND r.is_system = true
            AND r.account_id IS NULL
        )
        ON CONFLICT (account_id, rule_id) DO NOTHING
        """,
        nativeQuery = true,
    )
    fun disableSystemRuleForAccount(
        accountId: UUID,
        ruleId: UUID,
        now: Instant,
    ): Int

    @Query(
        """
        DELETE FROM banking.account_disabled_rules
        WHERE account_id = :accountId AND rule_id = :ruleId
        """,
        nativeQuery = true,
    )
    fun enableSystemRuleForAccount(
        accountId: UUID,
        ruleId: UUID,
    ): Int

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
        SET match_count = match_count + :delta, updated_at = :now
        WHERE id = :id AND deleted_at IS NULL
        """,
        nativeQuery = true,
    )
    fun incrementMatchCountBy(
        id: UUID,
        delta: Int,
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
