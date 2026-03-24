package io.github.salomax.neotool.banking.repo.transaction

import io.github.salomax.neotool.banking.model.transaction.TransactionCategoryEntity
import io.micronaut.data.annotation.Query
import io.micronaut.data.annotation.Repository
import io.micronaut.data.jpa.repository.JpaRepository
import java.time.Instant
import java.util.UUID

/**
 * JPA repository for banking.transaction_categories (entity layer).
 */
@Repository
interface TransactionCategoryEntityRepository : JpaRepository<TransactionCategoryEntity, UUID> {
    /**
     * Returns all system categories + user-defined categories for the given account.
     * Excludes soft-deleted rows.
     */
    @Query(
        """
        SELECT *
        FROM banking.transaction_categories
        WHERE (account_id IS NULL OR account_id = :accountId)
          AND deleted_at IS NULL
        ORDER BY sort_order ASC, created_at ASC
        """,
        nativeQuery = true,
    )
    fun findByAccountIdIsNullOrAccountIdAndDeletedAtIsNull(accountId: UUID): List<TransactionCategoryEntity>

    @Query(
        """
        SELECT *
        FROM banking.transaction_categories
        WHERE id IN (:ids)
          AND (account_id IS NULL OR account_id = :accountId)
          AND deleted_at IS NULL
        ORDER BY sort_order ASC, created_at ASC
        """,
        nativeQuery = true,
    )
    fun findAccessibleByIds(
        ids: Collection<UUID>,
        accountId: UUID,
    ): List<TransactionCategoryEntity>

    fun findByIdAndDeletedAtIsNull(id: UUID): TransactionCategoryEntity?

    @Query(
        """
        SELECT COUNT(*) > 0
        FROM banking.transaction_categories
        WHERE name = :name
          AND account_id = :accountId
          AND (
            (CAST(:parentId AS uuid) IS NULL AND parent_id IS NULL) OR parent_id = CAST(:parentId AS uuid)
          )
          AND deleted_at IS NULL
        """,
        nativeQuery = true,
    )
    fun existsByNameAndParentIdAndAccountIdAndDeletedAtIsNull(
        name: String,
        parentId: UUID?,
        accountId: UUID,
    ): Boolean

    @Query(
        """
        SELECT MAX(sort_order)
        FROM banking.transaction_categories
        WHERE account_id = :accountId
          AND (
            (CAST(:parentId AS uuid) IS NULL AND parent_id IS NULL) OR parent_id = CAST(:parentId AS uuid)
          )
          AND deleted_at IS NULL
        """,
        nativeQuery = true,
    )
    fun findMaxSortOrder(
        accountId: UUID,
        parentId: UUID?,
    ): Int?

    @Query(
        """
        UPDATE banking.transaction_categories
        SET deleted_at = :now, updated_at = :now
        WHERE id = :id AND account_id = :accountId AND deleted_at IS NULL
        """,
        nativeQuery = true,
    )
    fun softDelete(
        id: UUID,
        accountId: UUID,
        now: Instant,
    )

    @Query(
        """
        UPDATE banking.transaction_categories
        SET deleted_at = :now, updated_at = :now
        WHERE parent_id = :parentId AND account_id = :accountId AND deleted_at IS NULL
        """,
        nativeQuery = true,
    )
    fun softDeleteChildren(
        parentId: UUID,
        accountId: UUID,
        now: Instant,
    )
}
