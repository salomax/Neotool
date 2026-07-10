package io.github.salomax.neotool.banking.domain.wishlist

import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class WishlistItem(
    val id: UUID? = null,
    val accountId: UUID,
    val name: String,
    val estimatedPrice: BigDecimal? = null,
    val currencyCode: String = "BRL",
    val categoryId: UUID? = null,
    val priority: WishlistItemPriority,
    val notes: String? = null,
    val targetDate: LocalDate? = null,
    val productUrl: String? = null,
    val productImageUrl: String? = null,
    val status: WishlistItemStatus = WishlistItemStatus.ACTIVE,
    val sortOrder: Int = 0,
    val purchasedAt: LocalDate? = null,
    val deletedAt: Instant? = null,
    val createdAt: Instant,
    val updatedAt: Instant,
)

enum class WishlistItemPriority {
    HIGH,
    MEDIUM,
    LOW,
}

enum class WishlistItemStatus {
    ACTIVE,
    PURCHASED,
}
