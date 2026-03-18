package io.github.salomax.neotool.banking.integration

import io.github.salomax.neotool.banking.domain.transaction.CreditDebitType
import io.github.salomax.neotool.banking.domain.transaction.Transaction
import io.github.salomax.neotool.banking.domain.transaction.TransactionRepository
import io.github.salomax.neotool.banking.domain.transaction.TransactionSource
import io.github.salomax.neotool.banking.domain.transaction.TransactionStatus
import io.github.salomax.neotool.banking.domain.transaction.TransactionType
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.sql.DriverManager
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@MicronautTest(startApplication = true)
@DisplayName("TransactionRepository IT")
class TransactionRepositoryIT : BankingPostgresIntegrationTest() {
    @Inject
    lateinit var transactionRepository: TransactionRepository

    private lateinit var accountId: UUID
    private lateinit var userId: UUID
    private lateinit var financialAccountId: UUID
    private val today = LocalDate.now()

    @BeforeEach
    fun insertTestData() {
        val container = getPgContainer()
        val jdbcUrl = buildJdbcUrlWithSslDisabled(container.jdbcUrl)
        DriverManager.getConnection(jdbcUrl, container.username, container.password).use { conn ->
            userId = UUID.randomUUID()
            accountId = UUID.randomUUID()
            financialAccountId = UUID.randomUUID()
            conn.createStatement().executeUpdate(
                """
                INSERT INTO banking.financial_accounts (id, account_id, created_by_user_id, type, subtype, name, currency_code, created_at, updated_at)
                VALUES ('$financialAccountId', '$accountId', '$userId', 'BANK', 'CONTA_CORRENTE', 'IT Bank', 'BRL', NOW(), NOW())
                """.trimIndent(),
            )
        }
    }

    @Nested
    @DisplayName("save")
    inner class SaveTests {
        @Test
        fun `row exists in DB id is populated created_at and updated_at set`() {
            val tx =
                Transaction(
                    financialAccountId = financialAccountId,
                    accountId = accountId,
                    createdByUserId = userId,
                    creditDebitType = CreditDebitType.DEBIT,
                    type = TransactionType.OTHER_EXPENSE,
                    status = TransactionStatus.POSTED,
                    source = TransactionSource.MANUAL,
                    amount = BigDecimal("100.00"),
                    date = today,
                    description = "Save test",
                    createdAt = Instant.now(),
                    updatedAt = Instant.now(),
                )
            val saved = transactionRepository.save(tx)
            assertThat(saved.id).isNotNull
            assertThat(saved.createdAt).isNotNull
            assertThat(saved.updatedAt).isNotNull
            assertThat(saved.financialAccountId).isEqualTo(financialAccountId)
            assertThat(saved.description).isEqualTo("Save test")
        }
    }

    @Nested
    @DisplayName("findByFinancialAccount")
    inner class FindByFinancialAccountTests {
        @Test
        fun `returns rows ordered by date DESC created_at DESC and excludes soft-deleted`() {
            val base =
                Transaction(
                    financialAccountId = financialAccountId,
                    accountId = accountId,
                    createdByUserId = userId,
                    creditDebitType = CreditDebitType.DEBIT,
                    type = TransactionType.OTHER_EXPENSE,
                    amount = BigDecimal("10.00"),
                    date = today,
                    description = "X",
                    createdAt = Instant.now(),
                    updatedAt = Instant.now(),
                )
            val t1 = transactionRepository.save(base.copy(description = "First", date = today.minusDays(1)))
            val t2 = transactionRepository.save(base.copy(description = "Second", date = today))
            val t3 = transactionRepository.save(base.copy(description = "Third", date = today.minusDays(2)))
            transactionRepository.softDelete(t2.id!!, accountId)
            val list = transactionRepository.findByFinancialAccount(financialAccountId, accountId)
            assertThat(list).hasSize(2)
            assertThat(list.map { it.description }).containsExactly("First", "Third")
            assertThat(list[0].date).isEqualTo(today.minusDays(1))
            assertThat(list[1].date).isEqualTo(today.minusDays(2))
        }
    }

    @Nested
    @DisplayName("findById")
    inner class FindByIdTests {
        @Test
        fun `returns null when accountId does not match tenant isolation`() {
            val tx =
                transactionRepository.save(
                    Transaction(
                        financialAccountId = financialAccountId,
                        accountId = accountId,
                        createdByUserId = userId,
                        creditDebitType = CreditDebitType.DEBIT,
                        type = TransactionType.OTHER_EXPENSE,
                        amount = BigDecimal("1.00"),
                        date = today,
                        description = "X",
                        createdAt = Instant.now(),
                        updatedAt = Instant.now(),
                    ),
                )
            val otherAccountId = UUID.randomUUID()
            val found = transactionRepository.findById(tx.id!!, otherAccountId)
            assertThat(found).isNull()
        }
    }

    @Nested
    @DisplayName("softDelete")
    inner class SoftDeleteTests {
        @Test
        fun `sets deleted_at subsequent findById returns null returns false when id not found`() {
            val tx =
                transactionRepository.save(
                    Transaction(
                        financialAccountId = financialAccountId,
                        accountId = accountId,
                        createdByUserId = userId,
                        creditDebitType = CreditDebitType.DEBIT,
                        type = TransactionType.OTHER_EXPENSE,
                        amount = BigDecimal("1.00"),
                        date = today,
                        description = "To delete",
                        createdAt = Instant.now(),
                        updatedAt = Instant.now(),
                    ),
                )
            val result = transactionRepository.softDelete(tx.id!!, accountId)
            assertThat(result).isTrue
            assertThat(transactionRepository.findById(tx.id!!, accountId)).isNull()
            val notFound = transactionRepository.softDelete(UUID.randomUUID(), accountId)
            assertThat(notFound).isFalse
        }
    }

    @Nested
    @DisplayName("update")
    inner class UpdateTests {
        @Test
        fun `persists changed fields source and financial_account_id remain unchanged`() {
            val tx =
                transactionRepository.save(
                    Transaction(
                        financialAccountId = financialAccountId,
                        accountId = accountId,
                        createdByUserId = userId,
                        creditDebitType = CreditDebitType.DEBIT,
                        type = TransactionType.OTHER_EXPENSE,
                        status = TransactionStatus.POSTED,
                        source = TransactionSource.MANUAL,
                        amount = BigDecimal("50.00"),
                        date = today,
                        description = "Original",
                        notes = "Notes",
                        createdAt = Instant.now(),
                        updatedAt = Instant.now(),
                    ),
                )
            val updated =
                transactionRepository.update(
                    tx.copy(
                        description = "Updated",
                        amount = BigDecimal("75.00"),
                        notes = "New notes",
                        updatedAt = Instant.now(),
                    ),
                )
            assertThat(updated.description).isEqualTo("Updated")
            assertThat(updated.amount).isEqualByComparingTo("75.00")
            assertThat(updated.notes).isEqualTo("New notes")
            assertThat(updated.source).isEqualTo(TransactionSource.MANUAL)
            assertThat(updated.financialAccountId).isEqualTo(financialAccountId)
        }
    }

    @Nested
    @DisplayName("uq_transactions_provider")
    inner class UniqueConstraintTests {
        @Test
        fun `inserting same financial_account_id and provider_transaction_id twice throws`() {
            val providerId = "ofb-${UUID.randomUUID()}"
            val tx1 =
                transactionRepository.save(
                    Transaction(
                        financialAccountId = financialAccountId,
                        accountId = accountId,
                        createdByUserId = userId,
                        creditDebitType = CreditDebitType.DEBIT,
                        type = TransactionType.OTHER_EXPENSE,
                        amount = BigDecimal("10.00"),
                        date = today,
                        description = "OFB",
                        providerTransactionId = providerId,
                        createdAt = Instant.now(),
                        updatedAt = Instant.now(),
                    ),
                )
            assertThat(tx1.id).isNotNull
            val tx2 =
                Transaction(
                    financialAccountId = financialAccountId,
                    accountId = accountId,
                    createdByUserId = userId,
                    creditDebitType = CreditDebitType.CREDIT,
                    type = TransactionType.OTHER_INCOME,
                    amount = BigDecimal("10.00"),
                    date = today,
                    description = "OFB duplicate",
                    providerTransactionId = providerId,
                    createdAt = Instant.now(),
                    updatedAt = Instant.now(),
                )
            org.junit.jupiter.api.assertThrows<Exception> {
                transactionRepository.save(tx2)
            }
        }
    }

    @Nested
    @DisplayName("listBelongsToOptions")
    inner class ListBelongsToOptionsTests {
        @Test
        fun `returns distinct user ids and names from account transactions`() {
            transactionRepository.save(
                Transaction(
                    financialAccountId = financialAccountId,
                    accountId = accountId,
                    createdByUserId = userId,
                    belongsToUserId = userId,
                    creditDebitType = CreditDebitType.DEBIT,
                    type = TransactionType.OTHER_EXPENSE,
                    amount = BigDecimal("10.00"),
                    date = today,
                    description = "A",
                    createdAt = Instant.now(),
                    updatedAt = Instant.now(),
                ),
            )
            transactionRepository.save(
                Transaction(
                    financialAccountId = financialAccountId,
                    accountId = accountId,
                    createdByUserId = userId,
                    belongsToUserId = null,
                    belongsToName = "Partner",
                    creditDebitType = CreditDebitType.DEBIT,
                    type = TransactionType.OTHER_EXPENSE,
                    amount = BigDecimal("20.00"),
                    date = today,
                    description = "B",
                    createdAt = Instant.now(),
                    updatedAt = Instant.now(),
                ),
            )
            transactionRepository.save(
                Transaction(
                    financialAccountId = financialAccountId,
                    accountId = accountId,
                    createdByUserId = userId,
                    belongsToUserId = null,
                    belongsToName = "Partner",
                    creditDebitType = CreditDebitType.DEBIT,
                    type = TransactionType.OTHER_EXPENSE,
                    amount = BigDecimal("30.00"),
                    date = today,
                    description = "C",
                    createdAt = Instant.now(),
                    updatedAt = Instant.now(),
                ),
            )

            val options = transactionRepository.listBelongsToOptions(accountId)

            assertThat(options.map { it.userId }).contains(userId)
            assertThat(options.map { it.name }).contains("Partner")
            assertThat(options.count { it.name == "Partner" }).isEqualTo(1)
        }
    }

    @Nested
    @DisplayName("listTags")
    inner class ListTagsTests {
        @Test
        fun `returns distinct tags used by account transactions`() {
            transactionRepository.save(
                Transaction(
                    financialAccountId = financialAccountId,
                    accountId = accountId,
                    createdByUserId = userId,
                    creditDebitType = CreditDebitType.DEBIT,
                    type = TransactionType.OTHER_EXPENSE,
                    amount = BigDecimal("10.00"),
                    date = today,
                    description = "A",
                    tags = listOf("Food", "Weekend"),
                    createdAt = Instant.now(),
                    updatedAt = Instant.now(),
                ),
            )
            transactionRepository.save(
                Transaction(
                    financialAccountId = financialAccountId,
                    accountId = accountId,
                    createdByUserId = userId,
                    creditDebitType = CreditDebitType.DEBIT,
                    type = TransactionType.OTHER_EXPENSE,
                    amount = BigDecimal("20.00"),
                    date = today,
                    description = "B",
                    tags = listOf("Food", "Bills"),
                    createdAt = Instant.now(),
                    updatedAt = Instant.now(),
                ),
            )

            val tags = transactionRepository.listTags(accountId)

            assertThat(tags).containsExactly("Bills", "Food", "Weekend")
        }

        @Test
        fun `persists and loads tags on transaction save and find`() {
            val saved =
                transactionRepository.save(
                    Transaction(
                        financialAccountId = financialAccountId,
                        accountId = accountId,
                        createdByUserId = userId,
                        creditDebitType = CreditDebitType.DEBIT,
                        type = TransactionType.OTHER_EXPENSE,
                        amount = BigDecimal("15.00"),
                        date = today,
                        description = "Tagged",
                        tags = listOf("A", "B"),
                        createdAt = Instant.now(),
                        updatedAt = Instant.now(),
                    ),
                )

            val loaded = transactionRepository.findById(saved.id!!, accountId)

            assertThat(loaded).isNotNull
            assertThat(loaded!!.tags).containsExactlyInAnyOrder("A", "B")
        }
    }

    @Nested
    @DisplayName("findInDateRange")
    inner class FindInDateRangeTests {
        @Test
        fun `when financialAccountId is null CARD installment uses bill due month and BANK keeps transaction date`() {
            val cardFinancialAccountId = UUID.randomUUID()
            val billId = UUID.randomUUID()
            val januaryDate = LocalDate.of(2026, 1, 15)
            val februaryDate = LocalDate.of(2026, 2, 15)

            val container = getPgContainer()
            val jdbcUrl = buildJdbcUrlWithSslDisabled(container.jdbcUrl)
            DriverManager.getConnection(jdbcUrl, container.username, container.password).use { conn ->
                conn.createStatement().executeUpdate(
                    """
                    INSERT INTO banking.financial_accounts (id, account_id, created_by_user_id, type, subtype, name, currency_code, created_at, updated_at)
                    VALUES ('$cardFinancialAccountId', '$accountId', '$userId', 'CREDIT', 'CREDIT_CARD', 'IT Card', 'BRL', NOW(), NOW())
                    """.trimIndent(),
                )
                conn.createStatement().executeUpdate(
                    """
                    INSERT INTO banking.credit_card_bills (id, financial_account_id, account_id, closing_date, due_date, status, created_at, updated_at)
                    VALUES ('$billId', '$cardFinancialAccountId', '$accountId', '2026-01-31', '2026-02-10', 'OPEN', NOW(), NOW())
                    """.trimIndent(),
                )
            }

            val installmentTwo =
                transactionRepository.save(
                    Transaction(
                        financialAccountId = cardFinancialAccountId,
                        accountId = accountId,
                        createdByUserId = userId,
                        creditDebitType = CreditDebitType.DEBIT,
                        type = TransactionType.CARD,
                        amount = BigDecimal("150.00"),
                        date = januaryDate,
                        description = "Card installment 2",
                        billId = billId,
                        installmentNumber = 2,
                        totalInstallments = 3,
                        createdAt = Instant.now(),
                        updatedAt = Instant.now(),
                    ),
                )

            val installmentOne =
                transactionRepository.save(
                    Transaction(
                        financialAccountId = cardFinancialAccountId,
                        accountId = accountId,
                        createdByUserId = userId,
                        creditDebitType = CreditDebitType.DEBIT,
                        type = TransactionType.CARD,
                        amount = BigDecimal("150.00"),
                        date = januaryDate.minusDays(1),
                        description = "Card installment 1",
                        billId = billId,
                        installmentNumber = 1,
                        totalInstallments = 3,
                        createdAt = Instant.now(),
                        updatedAt = Instant.now(),
                    ),
                )

            val bankFebruaryTx =
                transactionRepository.save(
                    Transaction(
                        financialAccountId = financialAccountId,
                        accountId = accountId,
                        createdByUserId = userId,
                        creditDebitType = CreditDebitType.DEBIT,
                        type = TransactionType.OTHER_EXPENSE,
                        amount = BigDecimal("70.00"),
                        date = februaryDate,
                        description = "Bank February",
                        createdAt = Instant.now(),
                        updatedAt = Instant.now(),
                    ),
                )

            val februaryRows =
                transactionRepository.findInDateRange(
                    accountId = accountId,
                    financialAccountId = null,
                    year = 2026,
                    month = 2,
                )
            val januaryRows =
                transactionRepository.findInDateRange(
                    accountId = accountId,
                    financialAccountId = null,
                    year = 2026,
                    month = 1,
                )

            assertThat(februaryRows.mapNotNull { it.id }).contains(installmentTwo.id, bankFebruaryTx.id)
            assertThat(februaryRows.mapNotNull { it.id }).doesNotContain(installmentOne.id)
            assertThat(januaryRows.mapNotNull { it.id }).contains(installmentOne.id)
            assertThat(januaryRows.mapNotNull { it.id }).doesNotContain(installmentTwo.id, bankFebruaryTx.id)
        }

        @Test
        fun `when bill is missing CARD installment falls back to transaction date`() {
            val cardFinancialAccountId = UUID.randomUUID()
            val marchDate = LocalDate.of(2026, 3, 5)

            val container = getPgContainer()
            val jdbcUrl = buildJdbcUrlWithSslDisabled(container.jdbcUrl)
            DriverManager.getConnection(jdbcUrl, container.username, container.password).use { conn ->
                conn.createStatement().executeUpdate(
                    """
                    INSERT INTO banking.financial_accounts (id, account_id, created_by_user_id, type, subtype, name, currency_code, created_at, updated_at)
                    VALUES ('$cardFinancialAccountId', '$accountId', '$userId', 'CREDIT', 'CREDIT_CARD', 'IT Card (No Bill)', 'BRL', NOW(), NOW())
                    """.trimIndent(),
                )
            }

            val installmentWithoutBill =
                transactionRepository.save(
                    Transaction(
                        financialAccountId = cardFinancialAccountId,
                        accountId = accountId,
                        createdByUserId = userId,
                        creditDebitType = CreditDebitType.DEBIT,
                        type = TransactionType.CARD,
                        amount = BigDecimal("99.00"),
                        date = marchDate,
                        description = "Card installment without bill",
                        installmentNumber = 2,
                        totalInstallments = 3,
                        createdAt = Instant.now(),
                        updatedAt = Instant.now(),
                    ),
                )

            val marchRows =
                transactionRepository.findInDateRange(
                    accountId = accountId,
                    financialAccountId = null,
                    year = 2026,
                    month = 3,
                )
            val aprilRows =
                transactionRepository.findInDateRange(
                    accountId = accountId,
                    financialAccountId = null,
                    year = 2026,
                    month = 4,
                )

            assertThat(marchRows.mapNotNull { it.id }).contains(installmentWithoutBill.id)
            assertThat(aprilRows.mapNotNull { it.id }).doesNotContain(installmentWithoutBill.id)
        }
    }
}
