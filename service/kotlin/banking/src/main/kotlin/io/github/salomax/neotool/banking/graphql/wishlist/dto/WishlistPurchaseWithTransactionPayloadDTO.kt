package io.github.salomax.neotool.banking.graphql.wishlist.dto

import java.time.LocalDate
import java.util.UUID

data class WishlistPurchaseWithTransactionPayloadDTO(
    val wishlistItem: WishlistItemDTO,
    val transaction: WishlistPurchasedTransactionSummaryDTO,
)

data class WishlistPurchasedTransactionSummaryDTO(
    val id: UUID,
    val date: LocalDate,
)
