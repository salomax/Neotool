package io.github.salomax.neotool.banking.graphql.wishlist

import io.github.salomax.neotool.banking.domain.wishlist.WishlistItemPriority
import io.github.salomax.neotool.banking.service.wishlist.CreateWishlistItemCommand
import io.github.salomax.neotool.banking.service.wishlist.UpdateWishlistItemCommand
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

internal object WishlistItemInputParsing {
    fun parseCreateInput(input: Map<String, Any?>): CreateWishlistItemCommand =
        CreateWishlistItemCommand(
            name = input["name"] as String,
            estimatedPrice = input["estimatedPrice"].toBigDecimalOrNull(),
            currencyCode = input["currencyCode"] as String?,
            categoryId = (input["categoryId"] as String?)?.let(UUID::fromString),
            priority = WishlistItemPriority.valueOf(input["priority"] as String),
            notes = input["notes"] as String?,
            targetDate = input["targetDate"] as LocalDate?,
            productUrl = input["productUrl"] as String?,
            productImageUrl = input["productImageUrl"] as String?,
        )

    fun parseUpdateInput(input: Map<String, Any?>): UpdateWishlistItemCommand =
        UpdateWishlistItemCommand(
            name = input["name"] as String?,
            estimatedPriceDefined = input.containsKey("estimatedPrice"),
            estimatedPrice = input["estimatedPrice"].toBigDecimalOrNull(),
            categoryIdDefined = input.containsKey("categoryId"),
            categoryId = (input["categoryId"] as String?)?.let(UUID::fromString),
            priority = (input["priority"] as String?)?.let(WishlistItemPriority::valueOf),
            notesDefined = input.containsKey("notes"),
            notes = input["notes"] as String?,
            targetDateDefined = input.containsKey("targetDate"),
            targetDate = input["targetDate"] as LocalDate?,
            productUrlDefined = input.containsKey("productUrl"),
            productUrl = input["productUrl"] as String?,
            productImageUrlDefined = input.containsKey("productImageUrl"),
            productImageUrl = input["productImageUrl"] as String?,
        )

    private fun Any?.toBigDecimalOrNull(): BigDecimal? =
        when (this) {
            null -> null
            is BigDecimal -> this
            is Number -> BigDecimal(this.toString())
            is String -> takeIf { it.isNotBlank() }?.let(::BigDecimal)
            else -> error("Unsupported decimal value type: ${this::class.java.name}")
        }
}
