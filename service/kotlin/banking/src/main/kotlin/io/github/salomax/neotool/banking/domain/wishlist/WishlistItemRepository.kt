package io.github.salomax.neotool.banking.domain.wishlist

import java.util.UUID

interface WishlistItemRepository {
    fun findById(
        id: UUID,
        accountId: UUID,
    ): WishlistItem?

    fun findAllByAccountId(
        accountId: UUID,
        status: WishlistItemStatus? = null,
    ): List<WishlistItem>

    fun findMaxSortOrder(accountId: UUID): Int?

    fun save(item: WishlistItem): WishlistItem

    fun update(item: WishlistItem): WishlistItem

    fun softDelete(
        id: UUID,
        accountId: UUID,
    )
}
