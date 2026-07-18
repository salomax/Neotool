package io.github.salomax.neotool.banking.graphql.resolver

import io.github.salomax.neotool.banking.domain.preferences.AccountPreferences
import io.github.salomax.neotool.banking.domain.transaction.CreditDebitType
import io.github.salomax.neotool.banking.domain.transaction.Transaction
import io.github.salomax.neotool.banking.domain.transaction.TransactionStatus
import io.github.salomax.neotool.banking.domain.transaction.TransactionType
import io.github.salomax.neotool.banking.domain.wishlist.WishlistItem
import io.github.salomax.neotool.banking.domain.wishlist.WishlistItemPriority
import io.github.salomax.neotool.banking.domain.wishlist.WishlistItemStatus
import io.github.salomax.neotool.banking.graphql.wishlist.WishlistResolver
import io.github.salomax.neotool.banking.model.dto.transaction.CreateTransactionCommand
import io.github.salomax.neotool.banking.service.preferences.AccountPreferencesService
import io.github.salomax.neotool.banking.service.transaction.TransactionService
import io.github.salomax.neotool.banking.service.wishlist.DefaultWishlistItemService
import io.github.salomax.neotool.banking.service.wishlist.WishlistProductMetadataPreview
import io.github.salomax.neotool.banking.service.wishlist.WishlistProductMetadataPreviewService
import io.github.salomax.neotool.banking.service.wishlist.WishlistPurchaseService
import io.github.salomax.neotool.banking.service.wishlist.WishlistPurchaseWithTransactionResult
import io.github.salomax.neotool.common.security.principal.PrincipalType
import io.github.salomax.neotool.common.security.principal.RequestPrincipal
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

class WishlistResolverTest {
    private lateinit var wishlistItemService: DefaultWishlistItemService
    private lateinit var transactionService: TransactionService
    private lateinit var wishlistPurchaseService: WishlistPurchaseService
    private lateinit var accountPreferencesService: AccountPreferencesService
    private lateinit var wishlistProductMetadataPreviewService: WishlistProductMetadataPreviewService
    private lateinit var resolver: WishlistResolver

    private val accountId = UUID.randomUUID()
    private val userId = UUID.randomUUID()
    private val itemId = UUID.randomUUID()
    private val financialAccountId = UUID.randomUUID()
    private val now = Instant.now()
    private val today = LocalDate.now()

    @BeforeEach
    fun setUp() {
        wishlistItemService = mock()
        transactionService = mock()
        wishlistPurchaseService = mock()
        accountPreferencesService = mock()
        wishlistProductMetadataPreviewService = mock()
        whenever(transactionService.findAccessibleCategory(any(), any())).thenReturn(null)
        whenever(accountPreferencesService.get(accountId))
            .thenReturn(AccountPreferences(accountId, TransactionStatus.PENDING, now, now))
        resolver =
            WishlistResolver(
                wishlistItemService = wishlistItemService,
                transactionService = transactionService,
                wishlistPurchaseService = wishlistPurchaseService,
                accountPreferencesService = accountPreferencesService,
                wishlistProductMetadataPreviewService = wishlistProductMetadataPreviewService,
            )
    }

    @Test
    fun `purchase wishlist item with transaction applies account default status when omitted`() {
        whenever(wishlistPurchaseService.purchaseWithTransaction(any(), any(), any(), any()))
            .thenReturn(
                WishlistPurchaseWithTransactionResult(
                    wishlistItem = wishlistItem(),
                    transaction = transaction(status = TransactionStatus.PENDING),
                ),
            )

        resolver.purchaseWishlistItemWithTransaction(
            id = itemId.toString(),
            input =
                mapOf(
                    "financialAccountId" to financialAccountId.toString(),
                    "creditDebitType" to "DEBIT",
                    "type" to "OTHER_EXPENSE",
                    "amount" to 399.90,
                    "date" to today.toString(),
                    "description" to "Nintendo Switch 2",
                ),
            principal = principal(),
        )

        val commandCaptor = argumentCaptor<CreateTransactionCommand>()
        verify(wishlistPurchaseService).purchaseWithTransaction(
            eq(accountId),
            eq(userId),
            eq(itemId),
            commandCaptor.capture(),
        )
        assertThat(commandCaptor.firstValue.status).isEqualTo(TransactionStatus.PENDING)
    }

    @Test
    fun `product metadata preview maps service response to graphql dto`() {
        whenever(wishlistProductMetadataPreviewService.preview("https://shop.example.com/product"))
            .thenReturn(
                WishlistProductMetadataPreview(
                    name = "Nintendo Switch 2",
                    estimatedPrice = 399.90,
                    currencyCode = "BRL",
                    imageUrl = "https://cdn.example.com/switch-2.png",
                ),
            )

        val preview = resolver.wishlistProductMetadataPreview("https://shop.example.com/product", principal())

        assertThat(preview?.name).isEqualTo("Nintendo Switch 2")
        assertThat(preview?.estimatedPrice).isEqualTo(399.90)
        assertThat(preview?.currencyCode).isEqualTo("BRL")
        assertThat(preview?.imageUrl).isEqualTo("https://cdn.example.com/switch-2.png")
    }

    private fun principal() =
        RequestPrincipal(
            principalType = PrincipalType.USER,
            userId = userId,
            serviceId = null,
            token = "test-token",
            tokenId = "test-token-id",
            permissionsFromToken = emptyList(),
            currentAccountId = accountId,
        )

    private fun wishlistItem() =
        WishlistItem(
            id = itemId,
            accountId = accountId,
            name = "Nintendo Switch 2",
            estimatedPrice = BigDecimal("399.90"),
            currencyCode = "BRL",
            categoryId = null,
            priority = WishlistItemPriority.HIGH,
            notes = null,
            targetDate = null,
            productUrl = "https://shop.example.com/product",
            productImageUrl = "https://cdn.example.com/switch-2.png",
            status = WishlistItemStatus.PURCHASED,
            sortOrder = 0,
            purchasedAt = today,
            deletedAt = null,
            createdAt = now,
            updatedAt = now,
        )

    private fun transaction(status: TransactionStatus) =
        Transaction(
            id = UUID.randomUUID(),
            financialAccountId = financialAccountId,
            accountId = accountId,
            createdByUserId = userId,
            creditDebitType = CreditDebitType.DEBIT,
            type = TransactionType.OTHER_EXPENSE,
            status = status,
            amount = BigDecimal("399.90"),
            date = today,
            description = "Nintendo Switch 2",
            categoryId = null,
            createdAt = now,
            updatedAt = now,
        )
}
