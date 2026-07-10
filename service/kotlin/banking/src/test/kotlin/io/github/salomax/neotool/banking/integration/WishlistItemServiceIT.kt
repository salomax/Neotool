package io.github.salomax.neotool.banking.integration

import io.github.salomax.neotool.banking.domain.wishlist.WishlistItemStatus
import io.github.salomax.neotool.banking.model.dto.transaction.CreateTransactionCommand
import io.github.salomax.neotool.banking.repo.transaction.TransactionRepository
import io.github.salomax.neotool.banking.service.wishlist.CreateWishlistItemCommand
import io.github.salomax.neotool.banking.service.wishlist.DefaultWishlistItemService
import io.github.salomax.neotool.banking.service.wishlist.UpdateWishlistItemCommand
import io.github.salomax.neotool.banking.service.wishlist.WishlistPurchaseService
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

@MicronautTest(startApplication = true, transactional = false)
@DisplayName("WishlistItemService IT")
class WishlistItemServiceIT : BankingPostgresIntegrationTest() {
    @Inject
    lateinit var wishlistItemService: DefaultWishlistItemService

    @Inject
    lateinit var wishlistPurchaseService: WishlistPurchaseService

    @Inject
    lateinit var transactionRepository: TransactionRepository

    @Test
    fun `crud lifecycle stays scoped to the owning account`() {
        val accountId = UUID.randomUUID()
        val otherAccountId = UUID.randomUUID()

        val created =
            wishlistItemService.createWishlistItem(
                accountId,
                CreateWishlistItemCommand(
                    name = "Trip to Tokyo",
                    estimatedPrice = BigDecimal("8500.00"),
                    currencyCode = "BRL",
                    categoryId = null,
                    priority = io.github.salomax.neotool.banking.domain.wishlist.WishlistItemPriority.HIGH,
                    notes = "Spring",
                    targetDate = null,
                    productUrl = "https://example.com/tokyo",
                    productImageUrl = null,
                ),
            )

        assertThat(wishlistItemService.findWishlistItem(otherAccountId, requireNotNull(created.id))).isNull()

        val updated =
            wishlistItemService.updateWishlistItem(
                accountId,
                requireNotNull(created.id),
                UpdateWishlistItemCommand(
                    name = "Trip to Tokyo - Updated",
                    estimatedPriceDefined = true,
                    estimatedPrice = BigDecimal("9000.00"),
                    categoryIdDefined = true,
                    categoryId = null,
                    priority = null,
                    notesDefined = true,
                    notes = "Autumn",
                    targetDateDefined = false,
                    targetDate = null,
                    productUrlDefined = true,
                    productUrl = "https://example.com/tokyo-updated",
                    productImageUrlDefined = false,
                    productImageUrl = null,
                ),
            )

        assertThat(updated.name).isEqualTo("Trip to Tokyo - Updated")
        assertThat(updated.estimatedPrice).isEqualByComparingTo("9000.00")
        assertThat(updated.notes).isEqualTo("Autumn")

        val purchased = wishlistItemService.markWishlistItemAsPurchased(accountId, requireNotNull(created.id))
        assertThat(purchased.status).isEqualTo(WishlistItemStatus.PURCHASED)
        assertThat(purchased.purchasedAt).isNotNull()

        assertThat(wishlistItemService.listWishlistItems(accountId, WishlistItemStatus.ACTIVE)).isEmpty()
        assertThat(wishlistItemService.listWishlistItems(accountId, WishlistItemStatus.PURCHASED))
            .extracting("id")
            .containsExactly(created.id)

        assertThat(wishlistItemService.deleteWishlistItem(accountId, requireNotNull(created.id))).isTrue()
        assertThat(wishlistItemService.findWishlistItem(accountId, requireNotNull(created.id))).isNull()
    }

    @Test
    fun `reorder persists the explicit active ordering`() {
        val accountId = UUID.randomUUID()
        val first =
            wishlistItemService.createWishlistItem(
                accountId,
                CreateWishlistItemCommand(
                    name = "Monitor",
                    estimatedPrice = BigDecimal("1800.00"),
                    currencyCode = "BRL",
                    categoryId = null,
                    priority = io.github.salomax.neotool.banking.domain.wishlist.WishlistItemPriority.MEDIUM,
                    notes = null,
                    targetDate = null,
                    productUrl = null,
                    productImageUrl = null,
                ),
            )
        val second =
            wishlistItemService.createWishlistItem(
                accountId,
                CreateWishlistItemCommand(
                    name = "Desk",
                    estimatedPrice = BigDecimal("950.00"),
                    currencyCode = "BRL",
                    categoryId = null,
                    priority = io.github.salomax.neotool.banking.domain.wishlist.WishlistItemPriority.LOW,
                    notes = null,
                    targetDate = null,
                    productUrl = null,
                    productImageUrl = null,
                ),
            )

        val reordered =
            wishlistItemService.reorderWishlistItems(
                accountId,
                listOf(
                    requireNotNull(second.id),
                    requireNotNull(first.id),
                ),
            )

        assertThat(reordered.map { it.id }).containsExactly(second.id, first.id)
        assertThat(wishlistItemService.listWishlistItems(accountId, WishlistItemStatus.ACTIVE).map { it.id })
            .containsExactly(second.id, first.id)
    }

    @Test
    fun `purchase with transaction creates ledger entry and moves item to history atomically`() {
        val accountId = UUID.randomUUID()
        val userId = UUID.randomUUID()
        val financialAccountId = UUID.randomUUID()
        seedBankFinancialAccount(accountId, userId, financialAccountId)

        val created =
            wishlistItemService.createWishlistItem(
                accountId,
                CreateWishlistItemCommand(
                    name = "PlayStation 5",
                    estimatedPrice = BigDecimal("3499.90"),
                    currencyCode = "BRL",
                    categoryId = null,
                    priority = io.github.salomax.neotool.banking.domain.wishlist.WishlistItemPriority.HIGH,
                    notes = "Bundle",
                    targetDate = null,
                    productUrl = "https://example.com/ps5",
                    productImageUrl = "https://cdn.example.com/ps5.png",
                ),
            )

        val purchaseDate = LocalDate.now()
        val result =
            wishlistPurchaseService.purchaseWithTransaction(
                accountId = accountId,
                userId = userId,
                itemId = requireNotNull(created.id),
                command =
                    CreateTransactionCommand(
                        financialAccountId = financialAccountId,
                        creditDebitType = io.github.salomax.neotool.banking.domain.transaction.CreditDebitType.DEBIT,
                        type = io.github.salomax.neotool.banking.domain.transaction.TransactionType.OTHER_EXPENSE,
                        amount = BigDecimal("3499.90"),
                        date = purchaseDate,
                        description = "PlayStation 5",
                    ),
            )

        assertThat(result.wishlistItem.status).isEqualTo(WishlistItemStatus.PURCHASED)
        assertThat(result.wishlistItem.purchasedAt).isEqualTo(purchaseDate)

        val savedTransaction = transactionRepository.findById(requireNotNull(result.transaction.id), accountId)
        assertThat(savedTransaction).isNotNull
        assertThat(savedTransaction?.description).isEqualTo("PlayStation 5")
        assertThat(savedTransaction?.amount).isEqualByComparingTo("3499.90")
        assertThat(savedTransaction?.date).isEqualTo(purchaseDate)

        assertThat(wishlistItemService.listWishlistItems(accountId, WishlistItemStatus.ACTIVE)).isEmpty()
        assertThat(wishlistItemService.listWishlistItems(accountId, WishlistItemStatus.PURCHASED))
            .extracting("id")
            .containsExactly(created.id)
    }

    private fun seedBankFinancialAccount(
        accountId: UUID,
        userId: UUID,
        financialAccountId: UUID,
    ) {
        val container = getPgContainer()
        val jdbcUrl = buildJdbcUrlWithSslDisabled(container.jdbcUrl)
        java.sql.DriverManager.getConnection(jdbcUrl, container.username, container.password).use { conn ->
            conn.createStatement().executeUpdate(
                """
                INSERT INTO banking.financial_accounts (id, account_id, created_by_user_id, type, subtype, name, currency_code, created_at, updated_at)
                VALUES ('$financialAccountId', '$accountId', '$userId', 'BANK', 'CONTA_CORRENTE', 'Wishlist IT Account', 'BRL', NOW(), NOW())
                """.trimIndent(),
            )
        }
    }
}
