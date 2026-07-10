package io.github.salomax.neotool.banking.service.wishlist

import io.github.salomax.neotool.banking.domain.wishlist.WishlistItem
import io.github.salomax.neotool.banking.domain.wishlist.WishlistItemPriority
import io.github.salomax.neotool.banking.domain.wishlist.WishlistItemRepository
import io.github.salomax.neotool.banking.domain.wishlist.WishlistItemService
import io.github.salomax.neotool.banking.domain.wishlist.WishlistItemStatus
import io.github.salomax.neotool.banking.repo.transaction.TransactionCategoryRepository
import io.github.salomax.neotool.common.error.CommonErrorCode
import io.github.salomax.neotool.common.error.NotFoundException
import io.github.salomax.neotool.common.error.ValidationException
import io.micronaut.transaction.annotation.Transactional
import jakarta.inject.Singleton
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@Singleton
open class DefaultWishlistItemService(
    private val wishlistItemRepository: WishlistItemRepository,
    private val wishlistItemService: WishlistItemService,
    private val transactionCategoryRepository: TransactionCategoryRepository,
) {
    fun listWishlistItems(
        accountId: UUID,
        status: WishlistItemStatus?,
    ): List<WishlistItem> = wishlistItemRepository.findAllByAccountId(accountId, status)

    fun findWishlistItem(
        accountId: UUID,
        itemId: UUID,
    ): WishlistItem? = wishlistItemRepository.findById(itemId, accountId)

    fun createWishlistItem(
        accountId: UUID,
        input: CreateWishlistItemCommand,
    ): WishlistItem {
        requireAccessibleCategory(input.categoryId, accountId)
        val now = Instant.now()
        val item =
            WishlistItem(
                accountId = accountId,
                name = input.name.trim(),
                estimatedPrice = input.estimatedPrice,
                currencyCode = (input.currencyCode ?: "BRL").trim().uppercase(),
                categoryId = input.categoryId,
                priority = input.priority,
                notes = input.notes?.trim()?.takeIf { it.isNotEmpty() },
                targetDate = input.targetDate,
                productUrl = input.productUrl?.trim()?.takeIf { it.isNotEmpty() },
                productImageUrl = input.productImageUrl?.trim()?.takeIf { it.isNotEmpty() },
                status = WishlistItemStatus.ACTIVE,
                sortOrder = (wishlistItemRepository.findMaxSortOrder(accountId) ?: -1) + 1,
                purchasedAt = null,
                createdAt = now,
                updatedAt = now,
            )
        wishlistItemService.validate(item)
        return wishlistItemRepository.save(item)
    }

    fun updateWishlistItem(
        accountId: UUID,
        itemId: UUID,
        input: UpdateWishlistItemCommand,
    ): WishlistItem {
        val existing = requireWishlistItem(accountId, itemId)
        val nextCategoryId = if (input.categoryIdDefined) input.categoryId else existing.categoryId
        requireAccessibleCategory(nextCategoryId, accountId)
        val updated =
            existing.copy(
                name = input.name?.trim() ?: existing.name,
                estimatedPrice = if (input.estimatedPriceDefined) input.estimatedPrice else existing.estimatedPrice,
                categoryId = nextCategoryId,
                priority = input.priority ?: existing.priority,
                notes =
                    if (input.notesDefined) {
                        input.notes?.trim()?.takeIf { it.isNotEmpty() }
                    } else {
                        existing.notes
                    },
                targetDate = if (input.targetDateDefined) input.targetDate else existing.targetDate,
                productUrl =
                    if (input.productUrlDefined) {
                        input.productUrl?.trim()?.takeIf { it.isNotEmpty() }
                    } else {
                        existing.productUrl
                    },
                productImageUrl =
                    if (input.productImageUrlDefined) {
                        input.productImageUrl?.trim()?.takeIf { it.isNotEmpty() }
                    } else {
                        existing.productImageUrl
                    },
                updatedAt = Instant.now(),
            )
        wishlistItemService.validate(updated)
        return wishlistItemRepository.update(updated)
    }

    @Transactional
    open fun reorderWishlistItems(
        accountId: UUID,
        orderedIds: List<UUID>,
    ): List<WishlistItem> {
        val activeItems = wishlistItemRepository.findAllByAccountId(accountId, WishlistItemStatus.ACTIVE)
        val activeIds = activeItems.mapNotNull { it.id }
        if (orderedIds.size != activeIds.size || orderedIds.toSet() != activeIds.toSet()) {
            throw ValidationException(
                errorCode = CommonErrorCode.INVALID_INPUT,
                field = "orderedIds",
                parameters = mapOf("reason" to "must contain the full set of active wishlist item ids exactly once"),
            )
        }

        val itemsById = activeItems.associateBy { requireNotNull(it.id) }
        val now = Instant.now()
        return orderedIds.mapIndexed { index, id ->
            val item = itemsById[id] ?: throw IllegalStateException("Wishlist item $id not loaded for reorder")
            val reordered = item.copy(sortOrder = index, updatedAt = now)
            wishlistItemRepository.update(reordered)
        }
    }

    fun markWishlistItemAsPurchased(
        accountId: UUID,
        itemId: UUID,
        purchasedAt: LocalDate = LocalDate.now(),
    ): WishlistItem {
        val existing = requireWishlistItem(accountId, itemId)
        if (existing.status == WishlistItemStatus.PURCHASED) {
            return existing
        }
        val purchased =
            existing.copy(
                status = WishlistItemStatus.PURCHASED,
                purchasedAt = purchasedAt,
                updatedAt = Instant.now(),
            )
        wishlistItemService.validate(purchased)
        return wishlistItemRepository.update(purchased)
    }

    fun deleteWishlistItem(
        accountId: UUID,
        itemId: UUID,
    ): Boolean {
        requireWishlistItem(accountId, itemId)
        wishlistItemRepository.softDelete(itemId, accountId)
        return true
    }

    private fun requireWishlistItem(
        accountId: UUID,
        itemId: UUID,
    ): WishlistItem =
        wishlistItemRepository.findById(itemId, accountId)
            ?: throw NotFoundException(
                errorCode = CommonErrorCode.RESOURCE_NOT_FOUND,
                field = "id",
                parameters = mapOf("resource" to "WishlistItem", "id" to itemId),
            )

    private fun requireAccessibleCategory(
        categoryId: UUID?,
        accountId: UUID,
    ) {
        if (categoryId == null) return
        val category = transactionCategoryRepository.findById(categoryId)
        if (category == null || (category.accountId != null && category.accountId != accountId)) {
            throw ValidationException(
                errorCode = CommonErrorCode.INVALID_INPUT,
                field = "categoryId",
                parameters = mapOf("reason" to "must reference an accessible transaction category"),
            )
        }
    }
}

data class CreateWishlistItemCommand(
    val name: String,
    val estimatedPrice: BigDecimal?,
    val currencyCode: String?,
    val categoryId: UUID?,
    val priority: WishlistItemPriority,
    val notes: String?,
    val targetDate: LocalDate?,
    val productUrl: String?,
    val productImageUrl: String?,
)

data class UpdateWishlistItemCommand(
    val name: String?,
    val estimatedPriceDefined: Boolean,
    val estimatedPrice: BigDecimal?,
    val categoryIdDefined: Boolean,
    val categoryId: UUID?,
    val priority: WishlistItemPriority?,
    val notesDefined: Boolean,
    val notes: String?,
    val targetDateDefined: Boolean,
    val targetDate: LocalDate?,
    val productUrlDefined: Boolean,
    val productUrl: String?,
    val productImageUrlDefined: Boolean,
    val productImageUrl: String?,
)
