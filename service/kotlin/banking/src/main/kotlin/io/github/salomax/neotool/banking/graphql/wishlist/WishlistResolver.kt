package io.github.salomax.neotool.banking.graphql.wishlist

import io.github.salomax.neotool.banking.domain.transaction.TransactionCategory
import io.github.salomax.neotool.banking.domain.wishlist.WishlistItem
import io.github.salomax.neotool.banking.domain.wishlist.WishlistItemStatus
import io.github.salomax.neotool.banking.graphql.transaction.TransactionCategoryDTO
import io.github.salomax.neotool.banking.graphql.transaction.TransactionInputParsing
import io.github.salomax.neotool.banking.graphql.wishlist.dto.WishlistItemDTO
import io.github.salomax.neotool.banking.graphql.wishlist.dto.WishlistProductMetadataPreviewDTO
import io.github.salomax.neotool.banking.graphql.wishlist.dto.WishlistPurchaseWithTransactionPayloadDTO
import io.github.salomax.neotool.banking.graphql.wishlist.dto.WishlistPurchasedTransactionSummaryDTO
import io.github.salomax.neotool.banking.service.preferences.AccountPreferencesService
import io.github.salomax.neotool.banking.service.transaction.TransactionService
import io.github.salomax.neotool.banking.service.wishlist.DefaultWishlistItemService
import io.github.salomax.neotool.banking.service.wishlist.WishlistProductMetadataPreviewService
import io.github.salomax.neotool.banking.service.wishlist.WishlistPurchaseService
import io.github.salomax.neotool.common.error.CommonErrorCode
import io.github.salomax.neotool.common.error.ValidationException
import io.github.salomax.neotool.common.security.principal.RequestPrincipal
import jakarta.inject.Singleton
import java.util.UUID

@Singleton
class WishlistResolver(
    private val wishlistItemService: DefaultWishlistItemService,
    private val transactionService: TransactionService,
    private val wishlistPurchaseService: WishlistPurchaseService,
    private val accountPreferencesService: AccountPreferencesService,
    private val wishlistProductMetadataPreviewService: WishlistProductMetadataPreviewService,
) {
    fun wishlistItems(
        status: String?,
        principal: RequestPrincipal,
    ): List<WishlistItemDTO> {
        val accountId = requireAccountId(principal)
        val parsedStatus = status?.let(WishlistItemStatus::valueOf)
        val items = wishlistItemService.listWishlistItems(accountId, parsedStatus)
        val categories =
            transactionService.findAccessibleCategoriesByIds(
                items.mapNotNull { it.categoryId }.toSet(),
                accountId,
            )
        return items.map { it.toDto(categories[it.categoryId]) }
    }

    fun wishlistItem(
        id: String,
        principal: RequestPrincipal,
    ): WishlistItemDTO? {
        val accountId = requireAccountId(principal)
        return wishlistItemService.findWishlistItem(accountId, parseUuid(id, "id"))
            ?.let { item -> item.toDto(transactionService.findAccessibleCategory(item.categoryId, accountId)) }
    }

    fun wishlistProductMetadataPreview(
        url: String,
        principal: RequestPrincipal,
    ): WishlistProductMetadataPreviewDTO? {
        requireAccountId(principal)
        return wishlistProductMetadataPreviewService.preview(url)?.let {
            WishlistProductMetadataPreviewDTO(
                name = it.name,
                estimatedPrice = it.estimatedPrice,
                currencyCode = it.currencyCode,
                imageUrl = it.imageUrl,
            )
        }
    }

    fun createWishlistItem(
        input: Map<String, Any?>,
        principal: RequestPrincipal,
    ): WishlistItemDTO {
        val accountId = requireAccountId(principal)
        val item =
            wishlistItemService
                .createWishlistItem(
                    accountId,
                    WishlistItemInputParsing.parseCreateInput(input),
                )
        return item.toDto(transactionService.findAccessibleCategory(item.categoryId, accountId))
    }

    fun updateWishlistItem(
        id: String,
        input: Map<String, Any?>,
        principal: RequestPrincipal,
    ): WishlistItemDTO {
        val accountId = requireAccountId(principal)
        val item =
            wishlistItemService
                .updateWishlistItem(accountId, parseUuid(id, "id"), WishlistItemInputParsing.parseUpdateInput(input))
        return item.toDto(transactionService.findAccessibleCategory(item.categoryId, accountId))
    }

    fun reorderWishlistItems(
        orderedIds: List<String>,
        principal: RequestPrincipal,
    ): List<WishlistItemDTO> {
        val accountId = requireAccountId(principal)
        val items =
            wishlistItemService
                .reorderWishlistItems(accountId, orderedIds.map { parseUuid(it, "orderedIds") })
        val categories =
            transactionService.findAccessibleCategoriesByIds(
                items.mapNotNull { it.categoryId }.toSet(),
                accountId,
            )
        return items.map { it.toDto(categories[it.categoryId]) }
    }

    fun markWishlistItemAsPurchased(
        id: String,
        principal: RequestPrincipal,
    ): WishlistItemDTO {
        val accountId = requireAccountId(principal)
        val item = wishlistItemService.markWishlistItemAsPurchased(accountId, parseUuid(id, "id"))
        return item.toDto(transactionService.findAccessibleCategory(item.categoryId, accountId))
    }

    fun purchaseWishlistItemWithTransaction(
        id: String,
        input: Map<String, Any?>,
        principal: RequestPrincipal,
    ): WishlistPurchaseWithTransactionPayloadDTO {
        val accountId = requireAccountId(principal)
        val userId = requireNotNull(principal.userId) { "No user in token" }
        val command = TransactionInputParsing.parseCreateInput(input)
        val commandWithDefaultStatus =
            if ((input["status"] as? String?) != null) {
                command
            } else {
                command.copy(status = accountPreferencesService.get(accountId).defaultTransactionStatus)
            }

        val result =
            wishlistPurchaseService.purchaseWithTransaction(
                accountId = accountId,
                userId = userId,
                itemId = parseUuid(id, "id"),
                command = commandWithDefaultStatus,
            )
        val wishlistCategory = transactionService.findAccessibleCategory(result.wishlistItem.categoryId, accountId)
        return WishlistPurchaseWithTransactionPayloadDTO(
            wishlistItem = result.wishlistItem.toDto(wishlistCategory),
            transaction =
                WishlistPurchasedTransactionSummaryDTO(
                    id = requireNotNull(result.transaction.id),
                    date = result.transaction.date,
                ),
        )
    }

    fun deleteWishlistItem(
        id: String,
        principal: RequestPrincipal,
    ): Boolean {
        val accountId = requireAccountId(principal)
        return wishlistItemService.deleteWishlistItem(accountId, parseUuid(id, "id"))
    }

    private fun WishlistItem.toDto(category: TransactionCategory?): WishlistItemDTO =
        WishlistItemDTO(
            id = requireNotNull(id),
            name = name,
            estimatedPrice = estimatedPrice?.toDouble(),
            currencyCode = currencyCode,
            categoryId = categoryId,
            category = category?.toDto(),
            priority = priority,
            notes = notes,
            targetDate = targetDate,
            productUrl = productUrl,
            productImageUrl = productImageUrl,
            status = status,
            sortOrder = sortOrder,
            purchasedAt = purchasedAt,
            createdAt = createdAt,
            updatedAt = updatedAt,
        )

    private fun TransactionCategory.toDto(): TransactionCategoryDTO =
        TransactionCategoryDTO(
            id = requireNotNull(id),
            parentId = parentId,
            name = name,
            applicableTo = applicableTo,
            icon = icon,
            color = color,
            isSystem = isSystem,
            sortOrder = sortOrder,
        )

    private fun requireAccountId(principal: RequestPrincipal): UUID =
        requireNotNull(principal.currentAccountId) { "No active account in token" }

    private fun parseUuid(
        value: String,
        field: String,
    ): UUID =
        runCatching { UUID.fromString(value) }
            .getOrElse { throw invalidInput(field, "must be a valid UUID") }

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
