package io.github.salomax.neotool.banking.graphql.wishlist.dto

import io.github.salomax.neotool.banking.domain.wishlist.WishlistItemPriority
import io.github.salomax.neotool.banking.domain.wishlist.WishlistItemStatus
import io.github.salomax.neotool.banking.graphql.transaction.TransactionCategoryDTO
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class WishlistItemDTO(
    val id: UUID,
    val name: String,
    val estimatedPrice: Double?,
    val currencyCode: String,
    val categoryId: UUID?,
    val category: TransactionCategoryDTO?,
    val priority: WishlistItemPriority,
    val notes: String?,
    val targetDate: LocalDate?,
    val productUrl: String?,
    val productImageUrl: String?,
    val status: WishlistItemStatus,
    val sortOrder: Int,
    val purchasedAt: LocalDate?,
    val createdAt: Instant,
    val updatedAt: Instant,
)
