package io.github.salomax.neotool.banking.integration

import io.github.salomax.neotool.banking.domain.transaction.CreditDebitType
import io.github.salomax.neotool.banking.domain.transaction.MonthlyBalanceSnapshot
import io.github.salomax.neotool.banking.domain.transaction.MonthlyBalanceSnapshotRepository
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
@DisplayName("MonthlyBalanceSnapshotRepository IT")
@org.junit.jupiter.api.Tag("integration")
class MonthlyBalanceSnapshotRepositoryIT : BankingPostgresIntegrationTest() {
    @Inject
    lateinit var snapshotRepository: MonthlyBalanceSnapshotRepository

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
            accountId = UUID.randomUUID()
            userId = UUID.randomUUID()
            financialAccountId = UUID.randomUUID()
            conn.createStatement().executeUpdate(
                """
                INSERT INTO banking.financial_accounts (id, account_id, created_by_user_id, type, subtype, name, currency_code, created_at, updated_at)
                VALUES ('$financialAccountId', '$accountId', '$userId', 'BANK', 'CONTA_CORRENTE', 'IT Bank', 'BRL', NOW(), NOW())
                """.trimIndent(),
            )
        }
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private fun snapshot(
        year: Int,
        month: Int,
        openingBalance: BigDecimal = BigDecimal.ZERO,
        closingBalance: BigDecimal = BigDecimal.ZERO,
        totalIncome: BigDecimal = BigDecimal.ZERO,
        totalExpenses: BigDecimal = BigDecimal.ZERO,
        isDirty: Boolean = false,
    ) = MonthlyBalanceSnapshot(
        financialAccountId = financialAccountId,
        accountId = accountId,
        year = year,
        month = month,
        openingBalance = openingBalance,
        closingBalance = closingBalance,
        totalIncome = totalIncome,
        totalExpenses = totalExpenses,
        isDirty = isDirty,
    )

    private fun saveTx(
        date: LocalDate,
        amount: BigDecimal,
        creditDebit: CreditDebitType,
        type: TransactionType = TransactionType.OTHER_EXPENSE,
        status: TransactionStatus = TransactionStatus.POSTED,
    ) = transactionRepository.save(
        Transaction(
            financialAccountId = financialAccountId,
            accountId = accountId,
            createdByUserId = userId,
            creditDebitType = creditDebit,
            type = type,
            status = status,
            source = TransactionSource.MANUAL,
            amount = amount,
            date = date,
            description = "IT test tx",
            createdAt = Instant.now(),
            updatedAt = Instant.now(),
        ),
    )

    // ── upsert ────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("upsert")
    inner class UpsertTests {
        @Test
        fun `inserts snapshot and returns it with populated createdAt`() {
            val saved = snapshotRepository.upsert(snapshot(2024, 3, closingBalance = BigDecimal("1500.00")))

            assertThat(saved.financialAccountId).isEqualTo(financialAccountId)
            assertThat(saved.year).isEqualTo(2024)
            assertThat(saved.month).isEqualTo(3)
            assertThat(saved.closingBalance).isEqualByComparingTo("1500.00")
            assertThat(saved.createdAt).isNotNull()
        }

        @Test
        fun `second upsert for same (account, year, month) updates fields without creating duplicate`() {
            snapshotRepository.upsert(snapshot(2024, 4, closingBalance = BigDecimal("100.00")))
            val updated =
                snapshotRepository.upsert(
                    snapshot(2024, 4, closingBalance = BigDecimal("200.00"), totalIncome = BigDecimal("500.00")),
                )

            assertThat(updated.closingBalance).isEqualByComparingTo("200.00")
            assertThat(updated.totalIncome).isEqualByComparingTo("500.00")

            // Confirm only one row exists
            val fetched = snapshotRepository.findByYearMonth(financialAccountId, 2024, 4)
            assertThat(fetched).isNotNull
            assertThat(fetched!!.closingBalance).isEqualByComparingTo("200.00")
        }

        @Test
        fun `upsert resets isDirty to false`() {
            snapshotRepository.upsert(snapshot(2024, 5, isDirty = true))
            val clean = snapshotRepository.upsert(snapshot(2024, 5, isDirty = false))

            assertThat(clean.isDirty).isFalse()
        }
    }

    // ── findByYearMonth ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("findByYearMonth")
    inner class FindByYearMonthTests {
        @Test
        fun `returns null for non-existent (account, year, month)`() {
            val result = snapshotRepository.findByYearMonth(financialAccountId, 2099, 1)
            assertThat(result).isNull()
        }

        @Test
        fun `returns snapshot for correct key`() {
            snapshotRepository.upsert(
                snapshot(2024, 6, openingBalance = BigDecimal("50.00"), closingBalance = BigDecimal("150.00")),
            )

            val result = snapshotRepository.findByYearMonth(financialAccountId, 2024, 6)

            assertThat(result).isNotNull
            assertThat(result!!.openingBalance).isEqualByComparingTo("50.00")
            assertThat(result.closingBalance).isEqualByComparingTo("150.00")
        }
    }

    // ── findLatestCleanBefore ─────────────────────────────────────────────────

    @Nested
    @DisplayName("findLatestCleanBefore")
    inner class FindLatestCleanBeforeTests {
        @Test
        fun `returns null when no snapshots exist`() {
            val result = snapshotRepository.findLatestCleanBefore(financialAccountId, 2025, 1)
            assertThat(result).isNull()
        }

        @Test
        fun `returns the most recent clean snapshot strictly before the given month`() {
            snapshotRepository.upsert(snapshot(2024, 1, closingBalance = BigDecimal("100.00")))
            snapshotRepository.upsert(snapshot(2024, 2, closingBalance = BigDecimal("200.00")))
            snapshotRepository.upsert(snapshot(2024, 3, closingBalance = BigDecimal("300.00"), isDirty = true))

            // Ask for months before March 2024
            val result = snapshotRepository.findLatestCleanBefore(financialAccountId, 2024, 3)

            assertThat(result).isNotNull
            // Feb is the latest clean before March (March is dirty and excluded by "clean before")
            assertThat(result!!.month).isEqualTo(2)
            assertThat(result.closingBalance).isEqualByComparingTo("200.00")
        }

        @Test
        fun `skips dirty snapshots and returns latest clean one`() {
            snapshotRepository.upsert(snapshot(2024, 1, closingBalance = BigDecimal("100.00")))
            snapshotRepository.upsert(snapshot(2024, 2, closingBalance = BigDecimal("200.00"), isDirty = true))
            // Month 3 is what we're computing, so we look before it

            val result = snapshotRepository.findLatestCleanBefore(financialAccountId, 2024, 3)

            assertThat(result).isNotNull
            assertThat(result!!.month).isEqualTo(1)
            assertThat(result.closingBalance).isEqualByComparingTo("100.00")
        }
    }

    // ── markDirtyFromMonth ────────────────────────────────────────────────────

    @Nested
    @DisplayName("markDirtyFromMonth")
    inner class MarkDirtyFromMonthTests {
        @Test
        fun `marks all snapshots at or after the given month as dirty`() {
            snapshotRepository.upsert(snapshot(2024, 1))
            snapshotRepository.upsert(snapshot(2024, 2))
            snapshotRepository.upsert(snapshot(2024, 3))

            snapshotRepository.markDirtyFromMonth(financialAccountId, 2024, 2)

            assertThat(snapshotRepository.findByYearMonth(financialAccountId, 2024, 1)!!.isDirty).isFalse()
            assertThat(snapshotRepository.findByYearMonth(financialAccountId, 2024, 2)!!.isDirty).isTrue()
            assertThat(snapshotRepository.findByYearMonth(financialAccountId, 2024, 3)!!.isDirty).isTrue()
        }

        @Test
        fun `marking dirty from later year does not affect earlier years`() {
            snapshotRepository.upsert(snapshot(2023, 12))
            snapshotRepository.upsert(snapshot(2024, 1))

            snapshotRepository.markDirtyFromMonth(financialAccountId, 2024, 1)

            assertThat(snapshotRepository.findByYearMonth(financialAccountId, 2023, 12)!!.isDirty).isFalse()
            assertThat(snapshotRepository.findByYearMonth(financialAccountId, 2024, 1)!!.isDirty).isTrue()
        }
    }

    // ── findFinancialAccountIdsWithDirtySnapshots ──────────────────────────────

    @Nested
    @DisplayName("findFinancialAccountIdsWithDirtySnapshots")
    inner class FindDirtyAccountIdsTests {
        @Test
        fun `returns financial account id when it has at least one dirty snapshot`() {
            snapshotRepository.upsert(snapshot(2024, 7, isDirty = true))

            val ids = snapshotRepository.findFinancialAccountIdsWithDirtySnapshots()

            assertThat(ids).contains(financialAccountId)
        }

        @Test
        fun `does not return id when all snapshots are clean`() {
            snapshotRepository.upsert(snapshot(2024, 8, isDirty = false))

            val ids = snapshotRepository.findFinancialAccountIdsWithDirtySnapshots()

            assertThat(ids).doesNotContain(financialAccountId)
        }
    }

    // ── TransactionRepository aggregate queries ───────────────────────────────

    @Nested
    @DisplayName("TransactionRepository.getMonthTotals")
    inner class GetMonthTotalsTests {
        @Test
        fun `returns all zeros for a month with no transactions`() {
            val result = transactionRepository.getMonthTotals(financialAccountId, 2030, 1)

            assertThat(result.totalCredit).isEqualByComparingTo(BigDecimal.ZERO)
            assertThat(result.totalDebit).isEqualByComparingTo(BigDecimal.ZERO)
            assertThat(result.totalIncome).isEqualByComparingTo(BigDecimal.ZERO)
            assertThat(result.totalExpenses).isEqualByComparingTo(BigDecimal.ZERO)
        }

        @Test
        fun `correctly sums credits and debits for the month`() {
            val month = LocalDate.of(2024, 9, 1)
            saveTx(month, BigDecimal("300.00"), CreditDebitType.CREDIT, TransactionType.SALARY)
            saveTx(month, BigDecimal("100.00"), CreditDebitType.DEBIT, TransactionType.OTHER_EXPENSE)
            // Different month — must be excluded
            saveTx(month.plusMonths(1), BigDecimal("500.00"), CreditDebitType.CREDIT, TransactionType.SALARY)

            val result = transactionRepository.getMonthTotals(financialAccountId, 2024, 9)

            assertThat(result.totalCredit).isEqualByComparingTo("300.00")
            assertThat(result.totalDebit).isEqualByComparingTo("100.00")
        }

        @Test
        fun `excludes TRANSFER and BALANCE_ADJUSTMENT from totalIncome and totalExpenses`() {
            val month = LocalDate.of(2024, 10, 1)
            saveTx(month, BigDecimal("200.00"), CreditDebitType.CREDIT, TransactionType.SALARY)
            saveTx(month, BigDecimal("50.00"), CreditDebitType.CREDIT, TransactionType.TRANSFER)
            saveTx(month, BigDecimal("75.00"), CreditDebitType.DEBIT, TransactionType.OTHER_EXPENSE)
            saveTx(month, BigDecimal("25.00"), CreditDebitType.DEBIT, TransactionType.BALANCE_ADJUSTMENT)

            val result = transactionRepository.getMonthTotals(financialAccountId, 2024, 10)

            // totalIncome: only SALARY (200)
            assertThat(result.totalIncome).isEqualByComparingTo("200.00")
            // totalExpenses: only OTHER_EXPENSE (75)
            assertThat(result.totalExpenses).isEqualByComparingTo("75.00")
            // totalCredit: SALARY + TRANSFER (200 + 50)
            assertThat(result.totalCredit).isEqualByComparingTo("250.00")
            // totalDebit: OTHER_EXPENSE + BALANCE_ADJUSTMENT (75 + 25)
            assertThat(result.totalDebit).isEqualByComparingTo("100.00")
        }

        @Test
        fun `FUTURE transactions go into totalFutureCredit and totalFutureDebit only`() {
            val month = LocalDate.of(2024, 11, 1)
            saveTx(
                month,
                BigDecimal("100.00"),
                CreditDebitType.CREDIT,
                TransactionType.SALARY,
                TransactionStatus.POSTED,
            )
            saveTx(
                month,
                BigDecimal("60.00"),
                CreditDebitType.DEBIT,
                TransactionType.OTHER_EXPENSE,
                TransactionStatus.FUTURE,
            )

            val result = transactionRepository.getMonthTotals(financialAccountId, 2024, 11)

            assertThat(result.totalCredit).isEqualByComparingTo("100.00") // FUTURE excluded from totalCredit
            assertThat(result.totalDebit).isEqualByComparingTo(BigDecimal.ZERO) // FUTURE excluded from totalDebit
            assertThat(result.totalFutureDebit).isEqualByComparingTo("60.00")
            assertThat(result.totalFutureCredit).isEqualByComparingTo(BigDecimal.ZERO)
        }
    }

    @Nested
    @DisplayName("TransactionRepository.getMonthTransferTotals")
    inner class GetMonthTransferTotalsTests {
        @Test
        fun `returns zeros when no transfer transactions in month`() {
            val result = transactionRepository.getMonthTransferTotals(financialAccountId, 2030, 1)

            assertThat(result.transfersIn).isEqualByComparingTo(BigDecimal.ZERO)
            assertThat(result.transfersOut).isEqualByComparingTo(BigDecimal.ZERO)
        }

        @Test
        fun `sums only TRANSFER type transactions for the month`() {
            val month = LocalDate.of(2024, 12, 1)
            saveTx(month, BigDecimal("500.00"), CreditDebitType.CREDIT, TransactionType.TRANSFER)
            saveTx(month, BigDecimal("200.00"), CreditDebitType.DEBIT, TransactionType.TRANSFER)
            saveTx(month, BigDecimal("100.00"), CreditDebitType.CREDIT, TransactionType.SALARY)

            val result = transactionRepository.getMonthTransferTotals(financialAccountId, 2024, 12)

            assertThat(result.transfersIn).isEqualByComparingTo("500.00")
            assertThat(result.transfersOut).isEqualByComparingTo("200.00")
        }
    }
}
