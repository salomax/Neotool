package io.github.salomax.neotool.banking.service.wishlist

import io.github.salomax.neotool.banking.domain.wishlist.WishlistItem
import io.github.salomax.neotool.banking.domain.wishlist.WishlistItemPriority
import io.github.salomax.neotool.banking.domain.wishlist.WishlistItemRepository
import io.github.salomax.neotool.banking.domain.wishlist.WishlistItemService
import io.github.salomax.neotool.banking.domain.wishlist.WishlistItemStatus
import io.github.salomax.neotool.banking.repo.transaction.TransactionCategoryRepository
import io.github.salomax.neotool.common.error.ValidationException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

class DefaultWishlistItemServiceTest {
    private val repository: WishlistItemRepository = mock()
    private val transactionCategoryRepository: TransactionCategoryRepository = mock()
    private val rules = WishlistItemService()
    private val service = DefaultWishlistItemService(repository, rules, transactionCategoryRepository)

    private val accountId = UUID.randomUUID()
    private val now = Instant.now()

    @Test
    fun `create validates product URL before saving`() {
        assertThatThrownBy {
            service.createWishlistItem(
                accountId,
                CreateWishlistItemCommand(
                    name = "Travel package",
                    estimatedPrice = BigDecimal("2500.00"),
                    currencyCode = "BRL",
                    categoryId = null,
                    priority = WishlistItemPriority.HIGH,
                    notes = null,
                    targetDate = null,
                    productUrl = "notaurl",
                    productImageUrl = null,
                ),
            )
        }
            .isInstanceOf(ValidationException::class.java)
    }

    @Test
    fun `reorder rewrites sort order for the full active set`() {
        val first = wishlistItem(id = UUID.randomUUID(), sortOrder = 0)
        val second = wishlistItem(id = UUID.randomUUID(), sortOrder = 1)
        whenever(repository.findAllByAccountId(accountId, WishlistItemStatus.ACTIVE)).thenReturn(listOf(first, second))
        whenever(repository.update(any())).thenAnswer { it.arguments[0] as WishlistItem }

        val reordered =
            service.reorderWishlistItems(
                accountId,
                listOf(
                    requireNotNull(second.id),
                    requireNotNull(first.id),
                ),
            )

        assertThat(reordered.map { it.id }).containsExactly(second.id, first.id)
        assertThat(reordered.map { it.sortOrder }).containsExactly(0, 1)
        verify(repository).update(second.copy(sortOrder = 0, updatedAt = reordered[0].updatedAt))
        verify(repository).update(first.copy(sortOrder = 1, updatedAt = reordered[0].updatedAt))
    }

    @Test
    fun `reorder rejects partial list`() {
        val first = wishlistItem(id = UUID.randomUUID(), sortOrder = 0)
        val second = wishlistItem(id = UUID.randomUUID(), sortOrder = 1)
        whenever(repository.findAllByAccountId(accountId, WishlistItemStatus.ACTIVE)).thenReturn(listOf(first, second))

        assertThatThrownBy {
            service.reorderWishlistItems(accountId, listOf(requireNotNull(first.id)))
        }
            .isInstanceOf(ValidationException::class.java)
    }

    @Test
    fun `mark as purchased stores status and purchase date`() {
        val item = wishlistItem(id = UUID.randomUUID(), status = WishlistItemStatus.ACTIVE, purchasedAt = null)
        whenever(repository.findById(requireNotNull(item.id), accountId)).thenReturn(item)
        val captor = argumentCaptor<WishlistItem>()
        whenever(repository.update(captor.capture())).thenAnswer { it.arguments[0] as WishlistItem }

        val updated = service.markWishlistItemAsPurchased(accountId, requireNotNull(item.id))

        assertThat(updated.status).isEqualTo(WishlistItemStatus.PURCHASED)
        assertThat(updated.purchasedAt).isEqualTo(LocalDate.now())
        assertThat(captor.firstValue.status).isEqualTo(WishlistItemStatus.PURCHASED)
    }

    @Test
    fun `delete requires existing active record before soft delete`() {
        whenever(repository.findById(any(), any())).thenReturn(null)

        assertThatThrownBy {
            service.deleteWishlistItem(accountId, UUID.randomUUID())
        }.isInstanceOf(io.github.salomax.neotool.common.error.NotFoundException::class.java)
    }

    private fun wishlistItem(
        id: UUID? = UUID.randomUUID(),
        sortOrder: Int = 0,
        status: WishlistItemStatus = WishlistItemStatus.ACTIVE,
        purchasedAt: LocalDate? = null,
    ): WishlistItem =
        WishlistItem(
            id = id,
            accountId = accountId,
            name = "MacBook Pro",
            estimatedPrice = BigDecimal("12000.00"),
            currencyCode = "BRL",
            categoryId = null,
            priority = WishlistItemPriority.HIGH,
            notes = null,
            targetDate = null,
            productUrl = "https://example.com/item",
            status = status,
            sortOrder = sortOrder,
            purchasedAt = purchasedAt,
            deletedAt = null,
            createdAt = now,
            updatedAt = now,
        )
}
