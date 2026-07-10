package io.github.salomax.neotool.banking.repo.wishlist

import io.github.salomax.neotool.banking.domain.wishlist.WishlistItem
import io.github.salomax.neotool.banking.domain.wishlist.WishlistItemRepository
import io.github.salomax.neotool.banking.domain.wishlist.WishlistItemStatus
import io.github.salomax.neotool.banking.model.wishlist.WishlistItemMapper
import io.micronaut.transaction.annotation.Transactional
import jakarta.inject.Singleton
import java.time.Instant
import java.util.UUID

@Singleton
open class DefaultWishlistItemRepository(
    private val entityRepository: WishlistItemEntityRepository,
) : WishlistItemRepository {
    override fun findById(
        id: UUID,
        accountId: UUID,
    ): WishlistItem? =
        entityRepository.findByIdAndAccountIdAndDeletedAtIsNull(id, accountId)?.let(WishlistItemMapper::toDomain)

    override fun findAllByAccountId(
        accountId: UUID,
        status: WishlistItemStatus?,
    ): List<WishlistItem> =
        entityRepository.findAllByAccountIdAndStatus(accountId, status?.name).map(WishlistItemMapper::toDomain)

    override fun findMaxSortOrder(accountId: UUID): Int? = entityRepository.findMaxSortOrder(accountId)

    @Transactional
    override fun save(item: WishlistItem): WishlistItem =
        entityRepository.save(WishlistItemMapper.toEntity(item)).let(WishlistItemMapper::toDomain)

    @Transactional
    override fun update(item: WishlistItem): WishlistItem {
        val id = requireNotNull(item.id) { "Wishlist item id is required for update" }
        val entity =
            entityRepository.findByIdAndAccountIdAndDeletedAtIsNull(id, item.accountId)
                ?: throw NoSuchElementException("Wishlist item $id not found for account ${item.accountId}")
        WishlistItemMapper.updateEntity(entity, item)
        return entityRepository.update(entity).let(WishlistItemMapper::toDomain)
    }

    @Transactional
    override fun softDelete(
        id: UUID,
        accountId: UUID,
    ) {
        entityRepository.softDelete(id, accountId, Instant.now())
    }
}
