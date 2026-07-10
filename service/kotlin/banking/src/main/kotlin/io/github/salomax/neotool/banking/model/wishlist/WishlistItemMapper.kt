package io.github.salomax.neotool.banking.model.wishlist

import io.github.salomax.neotool.banking.domain.wishlist.WishlistItem
import io.github.salomax.neotool.banking.domain.wishlist.WishlistItemPriority
import io.github.salomax.neotool.banking.domain.wishlist.WishlistItemStatus

object WishlistItemMapper {
    fun toDomain(entity: WishlistItemEntity): WishlistItem =
        WishlistItem(
            id = entity.id,
            accountId = entity.accountId,
            name = entity.name,
            estimatedPrice = entity.estimatedPrice,
            currencyCode = entity.currencyCode,
            categoryId = entity.categoryId,
            priority = WishlistItemPriority.valueOf(entity.priority),
            notes = entity.notes,
            targetDate = entity.targetDate,
            productUrl = entity.productUrl,
            productImageUrl = entity.productImageUrl,
            status = WishlistItemStatus.valueOf(entity.status),
            sortOrder = entity.sortOrder,
            purchasedAt = entity.purchasedAt,
            deletedAt = entity.deletedAt,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt,
        )

    fun toEntity(domain: WishlistItem): WishlistItemEntity =
        WishlistItemEntity(
            id = null,
            accountId = domain.accountId,
            name = domain.name,
            estimatedPrice = domain.estimatedPrice,
            currencyCode = domain.currencyCode,
            categoryId = domain.categoryId,
            priority = domain.priority.name,
            notes = domain.notes,
            targetDate = domain.targetDate,
            productUrl = domain.productUrl,
            productImageUrl = domain.productImageUrl,
            status = domain.status.name,
            sortOrder = domain.sortOrder,
            purchasedAt = domain.purchasedAt,
            deletedAt = domain.deletedAt,
            createdAt = domain.createdAt,
            updatedAt = domain.updatedAt,
        )

    fun updateEntity(
        entity: WishlistItemEntity,
        domain: WishlistItem,
    ) {
        entity.accountId = domain.accountId
        entity.name = domain.name
        entity.estimatedPrice = domain.estimatedPrice
        entity.currencyCode = domain.currencyCode
        entity.categoryId = domain.categoryId
        entity.priority = domain.priority.name
        entity.notes = domain.notes
        entity.targetDate = domain.targetDate
        entity.productUrl = domain.productUrl
        entity.productImageUrl = domain.productImageUrl
        entity.status = domain.status.name
        entity.sortOrder = domain.sortOrder
        entity.purchasedAt = domain.purchasedAt
        entity.deletedAt = domain.deletedAt
        entity.updatedAt = domain.updatedAt
    }
}
