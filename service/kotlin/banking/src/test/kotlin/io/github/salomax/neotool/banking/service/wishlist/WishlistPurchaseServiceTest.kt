package io.github.salomax.neotool.banking.service.wishlist

import io.github.salomax.neotool.banking.domain.transaction.CreateTransactionOutcome
import io.github.salomax.neotool.banking.domain.transaction.CreditDebitType
import io.github.salomax.neotool.banking.domain.transaction.Transaction
import io.github.salomax.neotool.banking.domain.transaction.TransactionStatus
import io.github.salomax.neotool.banking.domain.transaction.TransactionType
import io.github.salomax.neotool.banking.domain.wishlist.WishlistItem
import io.github.salomax.neotool.banking.domain.wishlist.WishlistItemPriority
import io.github.salomax.neotool.banking.domain.wishlist.WishlistItemStatus
import io.github.salomax.neotool.banking.model.dto.transaction.CreateTransactionCommand
import io.github.salomax.neotool.banking.service.transaction.TransactionService
import io.github.salomax.neotool.common.error.NotFoundException
import io.github.salomax.neotool.common.error.ValidationException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

class WishlistPurchaseServiceTest {
    private val wishlistItemService: DefaultWishlistItemService = mock()
    private val transactionService: TransactionService = mock()
    private val service = WishlistPurchaseService(wishlistItemService, transactionService)

    private val accountId = UUID.randomUUID()
    private val userId = UUID.randomUUID()
    private val itemId = UUID.randomUUID()
    private val financialAccountId = UUID.randomUUID()
    private val now = Instant.now()
    private val transactionDate = LocalDate.now()

    @Test
    fun `purchase with transaction creates transaction and marks item with transaction date`() {
        val item = wishlistItem(status = WishlistItemStatus.ACTIVE)
        val transaction = transaction()
        val purchasedItem = item.copy(status = WishlistItemStatus.PURCHASED, purchasedAt = transactionDate)
        val command = createTransactionCommand()

        whenever(wishlistItemService.findWishlistItem(accountId, itemId)).thenReturn(item)
        whenever(transactionService.createTransactionOutcome(command, accountId, userId))
            .thenReturn(CreateTransactionOutcome(transaction = transaction))
        whenever(
            wishlistItemService.markWishlistItemAsPurchased(
                accountId,
                itemId,
                transactionDate,
            ),
        ).thenReturn(purchasedItem)

        val result = service.purchaseWithTransaction(accountId, userId, itemId, command)

        assertThat(result.transaction).isEqualTo(transaction)
        assertThat(result.wishlistItem.status).isEqualTo(WishlistItemStatus.PURCHASED)
        assertThat(result.wishlistItem.purchasedAt).isEqualTo(transactionDate)
        verify(transactionService).createTransactionOutcome(command, accountId, userId)
        verify(wishlistItemService).markWishlistItemAsPurchased(accountId, itemId, transactionDate)
    }

    @Test
    fun `purchase with transaction fails when wishlist item does not exist`() {
        whenever(wishlistItemService.findWishlistItem(accountId, itemId)).thenReturn(null)

        assertThatThrownBy {
            service.purchaseWithTransaction(accountId, userId, itemId, createTransactionCommand())
        }.isInstanceOf(NotFoundException::class.java)

        verify(transactionService, never()).createTransactionOutcome(any(), any(), any())
    }

    @Test
    fun `purchase with transaction rejects already purchased items`() {
        whenever(wishlistItemService.findWishlistItem(accountId, itemId))
            .thenReturn(wishlistItem(status = WishlistItemStatus.PURCHASED, purchasedAt = transactionDate))

        assertThatThrownBy {
            service.purchaseWithTransaction(accountId, userId, itemId, createTransactionCommand())
        }.isInstanceOf(ValidationException::class.java)

        verify(transactionService, never()).createTransactionOutcome(any(), any(), any())
        verify(wishlistItemService, never()).markWishlistItemAsPurchased(eq(accountId), eq(itemId), any())
    }

    private fun createTransactionCommand() =
        CreateTransactionCommand(
            financialAccountId = financialAccountId,
            creditDebitType = CreditDebitType.DEBIT,
            type = TransactionType.OTHER_EXPENSE,
            amount = BigDecimal("399.90"),
            date = transactionDate,
            description = "Nintendo Switch 2",
            status = TransactionStatus.POSTED,
        )

    private fun wishlistItem(
        status: WishlistItemStatus,
        purchasedAt: LocalDate? = null,
    ) = WishlistItem(
        id = itemId,
        accountId = accountId,
        name = "Nintendo Switch 2",
        estimatedPrice = BigDecimal("399.90"),
        currencyCode = "BRL",
        categoryId = null,
        priority = WishlistItemPriority.HIGH,
        notes = null,
        targetDate = null,
        productUrl = "https://shop.example.com/switch-2",
        productImageUrl = "https://cdn.example.com/switch-2.png",
        status = status,
        sortOrder = 0,
        purchasedAt = purchasedAt,
        deletedAt = null,
        createdAt = now,
        updatedAt = now,
    )

    private fun transaction() =
        Transaction(
            id = UUID.randomUUID(),
            financialAccountId = financialAccountId,
            accountId = accountId,
            createdByUserId = userId,
            creditDebitType = CreditDebitType.DEBIT,
            type = TransactionType.OTHER_EXPENSE,
            status = TransactionStatus.POSTED,
            amount = BigDecimal("399.90"),
            date = transactionDate,
            description = "Nintendo Switch 2",
            createdAt = now,
            updatedAt = now,
        )
}
