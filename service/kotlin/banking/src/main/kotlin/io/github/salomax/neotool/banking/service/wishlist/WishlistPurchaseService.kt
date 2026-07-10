package io.github.salomax.neotool.banking.service.wishlist

import io.github.salomax.neotool.banking.domain.transaction.Transaction
import io.github.salomax.neotool.banking.domain.wishlist.WishlistItem
import io.github.salomax.neotool.banking.domain.wishlist.WishlistItemStatus
import io.github.salomax.neotool.banking.model.dto.transaction.CreateTransactionCommand
import io.github.salomax.neotool.banking.service.transaction.TransactionService
import io.github.salomax.neotool.common.error.CommonErrorCode
import io.github.salomax.neotool.common.error.NotFoundException
import io.github.salomax.neotool.common.error.ValidationException
import io.micronaut.transaction.annotation.Transactional
import jakarta.inject.Singleton
import java.util.UUID

@Singleton
open class WishlistPurchaseService(
    private val wishlistItemService: DefaultWishlistItemService,
    private val transactionService: TransactionService,
) {
    @Transactional
    open fun purchaseWithTransaction(
        accountId: UUID,
        userId: UUID,
        itemId: UUID,
        command: CreateTransactionCommand,
    ): WishlistPurchaseWithTransactionResult {
        val item =
            wishlistItemService.findWishlistItem(accountId, itemId)
                ?: throw NotFoundException(
                    errorCode = CommonErrorCode.RESOURCE_NOT_FOUND,
                    field = "id",
                    parameters = mapOf("resource" to "WishlistItem", "id" to itemId),
                )

        if (item.status == WishlistItemStatus.PURCHASED) {
            throw ValidationException(
                errorCode = CommonErrorCode.INVALID_INPUT,
                field = "id",
                parameters = mapOf("reason" to "wishlist item is already marked as purchased"),
            )
        }

        val outcome = transactionService.createTransactionOutcome(command, accountId, userId)
        val purchasedItem =
            wishlistItemService.markWishlistItemAsPurchased(
                accountId = accountId,
                itemId = requireNotNull(item.id),
                purchasedAt = outcome.transaction.date,
            )

        return WishlistPurchaseWithTransactionResult(
            wishlistItem = purchasedItem,
            transaction = outcome.transaction,
        )
    }
}

data class WishlistPurchaseWithTransactionResult(
    val wishlistItem: WishlistItem,
    val transaction: Transaction,
)
