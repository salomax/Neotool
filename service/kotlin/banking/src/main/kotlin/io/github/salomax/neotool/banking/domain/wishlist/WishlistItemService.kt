package io.github.salomax.neotool.banking.domain.wishlist

import io.github.salomax.neotool.common.error.CommonErrorCode
import io.github.salomax.neotool.common.error.ValidationException
import jakarta.inject.Singleton
import java.math.BigDecimal
import java.net.URI

@Singleton
class WishlistItemService {
    fun validate(item: WishlistItem) {
        if (item.name.isBlank()) {
            throw invalidInput("name", "must not be blank")
        }
        if (item.name.length > 255) {
            throw invalidInput("name", "must not exceed 255 characters")
        }
        if (item.estimatedPrice != null && item.estimatedPrice <= BigDecimal.ZERO) {
            throw invalidInput("estimatedPrice", "must be greater than 0 when provided")
        }
        if (item.currencyCode.length != 3 || item.currencyCode.isBlank()) {
            throw invalidInput("currencyCode", "must be a 3-letter currency code")
        }
        val productUrl = item.productUrl?.trim()
        if (!productUrl.isNullOrEmpty() && !isValidUrl(productUrl)) {
            throw invalidInput("productUrl", "must be a valid http or https URL")
        }
        val productImageUrl = item.productImageUrl?.trim()
        if (!productImageUrl.isNullOrEmpty() && !isValidUrl(productImageUrl)) {
            throw invalidInput("productImageUrl", "must be a valid http or https URL")
        }
        if (item.status == WishlistItemStatus.PURCHASED && item.purchasedAt == null) {
            throw invalidInput("purchasedAt", "is required when status is PURCHASED")
        }
        if (item.status == WishlistItemStatus.ACTIVE && item.purchasedAt != null) {
            throw invalidInput("purchasedAt", "must be null when status is ACTIVE")
        }
        if (item.sortOrder < 0) {
            throw invalidInput("sortOrder", "must be greater than or equal to 0")
        }
    }

    private fun isValidUrl(value: String): Boolean =
        try {
            val parsed = URI(value)
            parsed.host?.isNotBlank() == true && (parsed.scheme == "http" || parsed.scheme == "https")
        } catch (_: Exception) {
            false
        }

    private fun invalidInput(
        field: String,
        reason: String,
    ): ValidationException =
        ValidationException(
            errorCode = CommonErrorCode.INVALID_INPUT,
            field = field,
            parameters = mapOf("reason" to reason),
        )
}
