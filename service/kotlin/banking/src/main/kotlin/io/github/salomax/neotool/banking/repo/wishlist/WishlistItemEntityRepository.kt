package io.github.salomax.neotool.banking.repo.wishlist

import io.github.salomax.neotool.banking.model.wishlist.WishlistItemEntity
import io.micronaut.data.annotation.Query
import io.micronaut.data.annotation.Repository
import io.micronaut.data.jpa.repository.JpaRepository
import java.time.Instant
import java.util.UUID

@Repository
interface WishlistItemEntityRepository : JpaRepository<WishlistItemEntity, UUID> {
    fun findByIdAndAccountIdAndDeletedAtIsNull(
        id: UUID,
        accountId: UUID,
    ): WishlistItemEntity?

    @Query(
        value = """
            SELECT * FROM banking.wishlist_item
             WHERE account_id = :accountId
               AND deleted_at IS NULL
               AND (:status IS NULL OR status = :status)
             ORDER BY sort_order ASC, created_at ASC
        """,
        nativeQuery = true,
    )
    fun findAllByAccountIdAndStatus(
        accountId: UUID,
        status: String?,
    ): List<WishlistItemEntity>

    @Query(
        value = """
            SELECT MAX(sort_order)
              FROM banking.wishlist_item
             WHERE account_id = :accountId
               AND deleted_at IS NULL
               AND status = 'ACTIVE'
        """,
        nativeQuery = true,
    )
    fun findMaxSortOrder(accountId: UUID): Int?

    @Query(
        value = """
            UPDATE banking.wishlist_item
               SET deleted_at = :deletedAt,
                   updated_at = :deletedAt
             WHERE id = :id
               AND account_id = :accountId
               AND deleted_at IS NULL
        """,
        nativeQuery = true,
    )
    fun softDelete(
        id: UUID,
        accountId: UUID,
        deletedAt: Instant,
    ): Int
}
