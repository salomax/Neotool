package io.github.salomax.neotool.banking.integration

import io.github.salomax.neotool.banking.repo.account.FinancialAccountRepository
import io.github.salomax.neotool.banking.domain.transaction.BillStatus
import io.github.salomax.neotool.banking.model.dto.transaction.CreateTransactionCommand
import io.github.salomax.neotool.banking.repo.transaction.CreditCardBillRepository
import io.github.salomax.neotool.banking.service.transaction.CreditCardBillService
import io.github.salomax.neotool.banking.domain.transaction.CreditDebitType
import io.github.salomax.neotool.banking.repo.transaction.TransactionRepository
import io.github.salomax.neotool.banking.service.transaction.TransactionService
import io.github.salomax.neotool.banking.domain.transaction.TransactionStatus
import io.github.salomax.neotool.banking.domain.transaction.TransactionType
import io.github.salomax.neotool.banking.model.dto.transaction.UpdateTransactionCommand
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.sql.DriverManager
import java.time.LocalDate
import java.util.UUID

@MicronautTest(startApplication = true)
@DisplayName("CreditCardBill lifecycle IT")
class CreditCardBillLifecycleIT : BankingPostgresIntegrationTest() {
    @Inject
    lateinit var transactionService: TransactionService

    @Inject
    lateinit var transactionRepository: TransactionRepository

    @Inject
    lateinit var creditCardBillService: CreditCardBillService

    @Inject
    lateinit var creditCardBillRepository: CreditCardBillRepository

    @Inject
    lateinit var financialAccountRepository: FinancialAccountRepository

    private lateinit var accountId: UUID
    private lateinit var userId: UUID
    private lateinit var cardAccountId: UUID
    private lateinit var bankAccountId: UUID

    private val today: LocalDate = LocalDate.now()

    @BeforeEach
    override fun setUp() {
        val container = getPgContainer()
        val jdbcUrl = buildJdbcUrlWithSslDisabled(container.jdbcUrl)
        DriverManager.getConnection(jdbcUrl, container.username, container.password).use { conn ->
            accountId = UUID.randomUUID()
            userId = UUID.randomUUID()
            cardAccountId = UUID.randomUUID()
            bankAccountId = UUID.randomUUID()

            conn.createStatement().use { stmt ->
                // Credit card account (CREDIT / CREDIT_CARD) with closingDay=7, dueDay=15
                stmt.executeUpdate(
                    """
                    INSERT INTO banking.financial_accounts (
                        id, account_id, created_by_user_id, type, subtype, name, currency_code,
                        include_in_net_worth, include_in_dashboard, created_at, updated_at
                    )
                    VALUES (
                        '$cardAccountId', '$accountId', '$userId',
                        'CREDIT', 'CREDIT_CARD',
                        'Test Card', 'BRL',
                        TRUE, TRUE, NOW(), NOW()
                    )
                    """.trimIndent(),
                )
                stmt.executeUpdate(
                    """
                    INSERT INTO banking.financial_account_card_data (
                        financial_account_id, masked_number, credit_limit, available_credit_limit,
                        min_payment_percentage, closing_day, due_day
                    )
                    VALUES (
                        '$cardAccountId', '**** 1234', 2000.00, 2000.00,
                        15.0, 7, 15
                    )
                    """.trimIndent(),
                )

                // Bank account to use as optional sourceAccountId for payBill
                stmt.executeUpdate(
                    """
                    INSERT INTO banking.financial_accounts (
                        id, account_id, created_by_user_id, type, subtype, name, currency_code,
                        include_in_net_worth, include_in_dashboard, created_at, updated_at
                    )
                    VALUES (
                        '$bankAccountId', '$accountId', '$userId',
                        'BANK', 'CONTA_CORRENTE',
                        'Checking', 'BRL',
                        TRUE, TRUE, NOW(), NOW()
                    )
                    """.trimIndent(),
                )
            }
        }

        // Sanity check: both accounts are visible to the domain repository
        val accounts = financialAccountRepository.findByAccountId(accountId)
        assertThat(accounts.map { it.id }).contains(cardAccountId, bankAccountId)
    }

    private fun createCardTransaction(
        amount: BigDecimal,
        date: LocalDate,
        type: TransactionType = TransactionType.CARD,
    ) = transactionService.create(
        command =
            CreateTransactionCommand(
                financialAccountId = cardAccountId,
                creditDebitType = CreditDebitType.DEBIT,
                type = type,
                amount = amount,
                date = date,
                description = "Test tx $date",
                status = TransactionStatus.POSTED,
            ),
        accountId = accountId,
        userId = userId,
    )

    @Nested
    @DisplayName("bill auto-creation and totals")
    inner class BillCreationTests {
        @Test
        fun `create transaction on card auto-creates bill with correct closing and due date`() {
            val txDate = LocalDate.of(today.year, today.month, 1)

            val tx = createCardTransaction(BigDecimal("100.00"), txDate)

            assertThat(tx.billId).isNotNull
            val bill = creditCardBillService.requireBillForAccount(tx.billId!!, accountId)

            val expectedClosing = creditCardBillService.computeClosingDate(txDate, 7)
            val expectedDue = creditCardBillService.computeDueDate(expectedClosing, 15)

            assertThat(bill.closingDate).isEqualTo(expectedClosing)
            assertThat(bill.dueDate).isEqualTo(expectedDue)
        }

        @Test
        fun `two transactions in same cycle share bill and totalAmount is sum`() {
            val txDate1 = LocalDate.of(today.year, today.month, 1)
            val txDate2 = LocalDate.of(today.year, today.month, 3)

            val tx1 = createCardTransaction(BigDecimal("100.00"), txDate1)
            val tx2 = createCardTransaction(BigDecimal("50.00"), txDate2)

            assertThat(tx1.billId).isNotNull
            assertThat(tx2.billId).isEqualTo(tx1.billId)

            val bill = creditCardBillService.requireBillForAccount(tx1.billId!!, accountId)
            assertThat(bill.totalAmount).isEqualByComparingTo("150.00")
        }

        @Test
        fun `transaction after closing day creates new bill`() {
            // closingDay=7 (configured in card data)
            val feb7 = LocalDate.of(2026, 2, 7)
            val feb8 = LocalDate.of(2026, 2, 8)

            val tx1 = createCardTransaction(BigDecimal("100.00"), feb7)
            val tx2 = createCardTransaction(BigDecimal("50.00"), feb8)

            assertThat(tx1.billId).isNotNull
            assertThat(tx2.billId).isNotNull
            assertThat(tx2.billId).isNotEqualTo(tx1.billId)

            val bills = creditCardBillService.listBills(cardAccountId, accountId)
            assertThat(bills).hasSize(2)
        }
    }

    @Nested
    @DisplayName("bill movement on update and delete")
    inner class BillMutationTests {
        @Test
        fun `updating transaction date across cycle boundary moves amount between bills`() {
            val feb1 = LocalDate.of(2026, 2, 1)
            val feb8 = LocalDate.of(2026, 2, 8) // next cycle when closingDay=7

            val tx = createCardTransaction(BigDecimal("100.00"), feb1)
            val originalBillId = tx.billId!!

            val updated =
                transactionService.update(
                    id = tx.id!!,
                    command =
                        UpdateTransactionCommand(
                            date = feb8,
                        ),
                    accountId = accountId,
                    userId = userId,
                )

            val newBillId = updated.billId!!
            assertThat(newBillId).isNotEqualTo(originalBillId)

            val originalBill = creditCardBillService.requireBillForAccount(originalBillId, accountId)
            val newBill = creditCardBillService.requireBillForAccount(newBillId, accountId)

            assertThat(originalBill.totalAmount).isEqualByComparingTo("0.00")
            assertThat(newBill.totalAmount).isEqualByComparingTo("100.00")
        }

        @Test
        fun `deleting transaction decreases bill totalAmount`() {
            val tx = createCardTransaction(BigDecimal("120.00"), today)
            val billBefore = creditCardBillService.requireBillForAccount(tx.billId!!, accountId)
            assertThat(billBefore.totalAmount).isEqualByComparingTo("120.00")

            val deleted = transactionService.delete(tx.id!!, accountId)
            assertThat(deleted).isTrue

            val billAfter = creditCardBillService.requireBillForAccount(tx.billId!!, accountId)
            assertThat(billAfter.totalAmount).isEqualByComparingTo("0.00")
        }

        @Test
        fun `BALANCE_ADJUSTMENT transaction on card has no billId`() {
            val tx =
                transactionService.create(
                    command =
                        CreateTransactionCommand(
                            financialAccountId = cardAccountId,
                            creditDebitType = CreditDebitType.CREDIT,
                            type = TransactionType.BALANCE_ADJUSTMENT,
                            amount = BigDecimal("1000.00"),
                            date = today,
                            description = "Opening balance",
                            status = TransactionStatus.POSTED,
                        ),
                    accountId = accountId,
                    userId = userId,
                )

            assertThat(tx.billId).isNull()

            val bills = creditCardBillService.listBills(cardAccountId, accountId)
            assertThat(bills).isEmpty()
        }
    }

    @Nested
    @DisplayName("payBill integration")
    inner class PayBillTests {
        @Test
        fun `payBill with full amount marks bill as PAID and creates transfer`() {
            val tx = createCardTransaction(BigDecimal("200.00"), today.minusDays(10))
            val billBefore = creditCardBillService.requireBillForAccount(tx.billId!!, accountId)
            assertThat(billBefore.status).isNotEqualTo(BillStatus.PAID)
            assertThat(billBefore.totalAmount).isEqualByComparingTo("200.00")

            val billAfter =
                transactionService.payBill(
                    billId = billBefore.id!!,
                    amount = billBefore.totalAmount,
                    sourceAccountId = bankAccountId,
                    accountId = accountId,
                    userId = userId,
                )

            assertThat(billAfter.status).isEqualTo(BillStatus.PAID)
            assertThat(billAfter.totalAmount).isEqualByComparingTo("0.00")

            val cardTransactions =
                transactionRepository.findByFinancialAccount(cardAccountId, accountId)
                    .filter { it.type == TransactionType.TRANSFER }
            assertThat(cardTransactions).anySatisfy {
                assertThat(it.creditDebitType).isEqualTo(CreditDebitType.CREDIT)
                assertThat(it.billId).isEqualTo(billAfter.id)
            }
        }

        @Test
        fun `payBill with partial amount decreases totalAmount but does not mark as PAID`() {
            val tx = createCardTransaction(BigDecimal("300.00"), today.minusDays(10))
            val billBefore = creditCardBillService.requireBillForAccount(tx.billId!!, accountId)
            assertThat(billBefore.totalAmount).isEqualByComparingTo("300.00")

            val partialAmount = BigDecimal("100.00")
            val billAfter =
                transactionService.payBill(
                    billId = billBefore.id!!,
                    amount = partialAmount,
                    sourceAccountId = null,
                    accountId = accountId,
                    userId = userId,
                )

            assertThat(billAfter.totalAmount).isEqualByComparingTo("200.00")
            assertThat(billAfter.status).isNotEqualTo(BillStatus.PAID)
        }
    }

    @Nested
    @DisplayName("list and find helpers")
    inner class ListAndFindTests {
        @Test
        fun `creditCardBills returns bills ordered by closingDate DESC`() {
            // Three cycles: Jan, Feb, Mar
            val janTx = createCardTransaction(BigDecimal("50.00"), LocalDate.of(2026, 1, 5))
            val febTx = createCardTransaction(BigDecimal("60.00"), LocalDate.of(2026, 2, 5))
            val marTx = createCardTransaction(BigDecimal("70.00"), LocalDate.of(2026, 3, 5))

            assertThat(janTx.billId).isNotNull
            assertThat(febTx.billId).isNotNull
            assertThat(marTx.billId).isNotNull

            val bills = creditCardBillService.listBills(cardAccountId, accountId)
            assertThat(bills).hasSize(3)
            assertThat(bills.map { it.closingDate }).isSortedAccordingTo(Comparator.reverseOrder())
        }

        @Test
        fun `creditCardBill with null closingDate returns open or most recent bill`() {
            // Create a bill in the current cycle so that closingDate >= today
            val currentCycleTx =
                createCardTransaction(BigDecimal("80.00"), today)
            val bill = creditCardBillService.requireBillForAccount(currentCycleTx.billId!!, accountId)

            val found = creditCardBillService.findBill(cardAccountId, accountId, null)
            assertThat(found).isNotNull
            assertThat(found!!.closingDate).isEqualTo(bill.closingDate)
        }

        @Test
        fun `listBills throws when financialAccountId belongs to another account`() {
            val otherAccountId = UUID.randomUUID()
            val otherCardId = UUID.randomUUID()
            val container = getPgContainer()
            val jdbcUrl = buildJdbcUrlWithSslDisabled(container.jdbcUrl)
            DriverManager.getConnection(jdbcUrl, container.username, container.password).use { conn ->
                conn.createStatement().use { stmt ->
                    stmt.executeUpdate(
                        """
                        INSERT INTO banking.financial_accounts (
                            id, account_id, created_by_user_id, type, subtype, name, currency_code,
                            include_in_net_worth, include_in_dashboard, created_at, updated_at
                        )
                        VALUES (
                            '$otherCardId', '$otherAccountId', '$userId',
                            'CREDIT', 'CREDIT_CARD',
                            'Other Card', 'BRL',
                            TRUE, TRUE, NOW(), NOW()
                        )
                        """.trimIndent(),
                    )
                }
            }

            assertThatThrownBy {
                creditCardBillService.listBills(otherCardId, accountId)
            }.isInstanceOf(IllegalArgumentException::class.java)
                .hasMessageContaining("does not belong to account")
        }

        @Test
        fun `listBills throws when financialAccountId does not exist`() {
            assertThatThrownBy {
                creditCardBillService.listBills(UUID.randomUUID(), accountId)
            }.isInstanceOf(IllegalArgumentException::class.java)
                .hasMessageContaining("does not belong to account")
        }

        @Test
        fun `findBill throws when financialAccountId belongs to another account`() {
            val otherAccountId = UUID.randomUUID()
            val otherCardId = UUID.randomUUID()
            val container = getPgContainer()
            val jdbcUrl = buildJdbcUrlWithSslDisabled(container.jdbcUrl)
            DriverManager.getConnection(jdbcUrl, container.username, container.password).use { conn ->
                conn.createStatement().use { stmt ->
                    stmt.executeUpdate(
                        """
                        INSERT INTO banking.financial_accounts (
                            id, account_id, created_by_user_id, type, subtype, name, currency_code,
                            include_in_net_worth, include_in_dashboard, created_at, updated_at
                        )
                        VALUES (
                            '$otherCardId', '$otherAccountId', '$userId',
                            'CREDIT', 'CREDIT_CARD',
                            'Other Card Find', 'BRL',
                            TRUE, TRUE, NOW(), NOW()
                        )
                        """.trimIndent(),
                    )
                }
            }

            assertThatThrownBy {
                creditCardBillService.findBill(otherCardId, accountId, null)
            }.isInstanceOf(IllegalArgumentException::class.java)
                .hasMessageContaining("does not belong to account")
        }

        @Test
        fun `findBill throws when financialAccountId does not exist`() {
            assertThatThrownBy {
                creditCardBillService.findBill(UUID.randomUUID(), accountId, null)
            }.isInstanceOf(IllegalArgumentException::class.java)
                .hasMessageContaining("does not belong to account")
        }
    }
}
