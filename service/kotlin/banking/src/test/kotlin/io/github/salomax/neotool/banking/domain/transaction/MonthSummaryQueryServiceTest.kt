package io.github.salomax.neotool.banking.domain.transaction

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
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

@DisplayName("MonthSummaryQueryService")
class MonthSummaryQueryServiceTest {
    private lateinit var snapshotRepository: MonthlyBalanceSnapshotRepository
    private lateinit var transactionRepository: TransactionRepository
    private lateinit var snapshotComputationService: SnapshotComputationService
    private lateinit var service: MonthSummaryQueryService

    private val financialAccountId = UUID.randomUUID()
    private val accountId = UUID.randomUUID()
    private val now = Instant.now()

    // Use a past month so isCurrentMonth is reliably false in deterministic tests
    private val pastYear = 2024
    private val pastMonth = 1

    @BeforeEach
    fun setUp() {
        snapshotRepository = mock()
        transactionRepository = mock()
        snapshotComputationService = mock()
        service = MonthSummaryQueryService(snapshotRepository, transactionRepository, snapshotComputationService)
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private fun snapshot(
        year: Int = pastYear,
        month: Int = pastMonth,
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
        computedAt = now,
        createdAt = now,
        updatedAt = now,
    )

    private fun totals(
        totalCredit: BigDecimal = BigDecimal.ZERO,
        totalDebit: BigDecimal = BigDecimal.ZERO,
        totalIncome: BigDecimal = BigDecimal.ZERO,
        totalExpenses: BigDecimal = BigDecimal.ZERO,
        totalFutureCredit: BigDecimal = BigDecimal.ZERO,
        totalFutureDebit: BigDecimal = BigDecimal.ZERO,
    ) = MonthTransactionTotals(
        financialAccountId = financialAccountId,
        year = pastYear,
        month = pastMonth,
        totalCredit = totalCredit,
        totalDebit = totalDebit,
        totalIncome = totalIncome,
        totalExpenses = totalExpenses,
        totalFutureCredit = totalFutureCredit,
        totalFutureDebit = totalFutureDebit,
    )

    private fun transferTotals(
        transfersIn: BigDecimal = BigDecimal.ZERO,
        transfersOut: BigDecimal = BigDecimal.ZERO,
    ) = MonthTransferTotals(transfersIn = transfersIn, transfersOut = transfersOut)

    // ── clean snapshot path ───────────────────────────────────────────────────

    @Nested
    @DisplayName("when snapshot is clean")
    inner class CleanSnapshotTests {
        @Test
        fun `returns values from snapshot without recomputation`() {
            val snap =
                snapshot(
                    openingBalance = BigDecimal("1000.00"),
                    closingBalance = BigDecimal("1200.00"),
                    totalIncome = BigDecimal("300.00"),
                    totalExpenses = BigDecimal("100.00"),
                )
            whenever(snapshotRepository.findByYearMonth(financialAccountId, pastYear, pastMonth)).thenReturn(snap)
            whenever(transactionRepository.getMonthTransferTotals(financialAccountId, pastYear, pastMonth))
                .thenReturn(transferTotals(transfersIn = BigDecimal("50.00")))
            whenever(transactionRepository.getMonthTotals(financialAccountId, pastYear, pastMonth))
                .thenReturn(totals())

            val result = service.getMonthSummary(financialAccountId, accountId, pastYear, pastMonth)

            verify(snapshotComputationService, never()).recomputeChain(any(), any(), any(), any())
            assertThat(result.openingBalance).isEqualByComparingTo("1000.00")
            assertThat(result.closingBalance).isEqualByComparingTo("1200.00")
            assertThat(result.totalIncome).isEqualByComparingTo("300.00")
            assertThat(result.totalExpenses).isEqualByComparingTo("100.00")
            assertThat(result.transfersIn).isEqualByComparingTo("50.00")
        }

        @Test
        fun `isCurrentMonth is true when year and month match today`() {
            val today = LocalDate.now()
            val snap = snapshot(year = today.year, month = today.monthValue)
            whenever(
                snapshotRepository.findByYearMonth(financialAccountId, today.year, today.monthValue),
            ).thenReturn(snap)
            whenever(transactionRepository.getMonthTransferTotals(financialAccountId, today.year, today.monthValue))
                .thenReturn(transferTotals())
            whenever(transactionRepository.getMonthTotals(financialAccountId, today.year, today.monthValue))
                .thenReturn(totals())

            val result = service.getMonthSummary(financialAccountId, accountId, today.year, today.monthValue)

            assertThat(result.isCurrentMonth).isTrue()
        }

        @Test
        fun `isCurrentMonth is false for a past month`() {
            val snap = snapshot()
            whenever(snapshotRepository.findByYearMonth(financialAccountId, pastYear, pastMonth)).thenReturn(snap)
            whenever(transactionRepository.getMonthTransferTotals(any(), any(), any())).thenReturn(transferTotals())
            whenever(transactionRepository.getMonthTotals(any(), any(), any())).thenReturn(totals())

            val result = service.getMonthSummary(financialAccountId, accountId, pastYear, pastMonth)

            assertThat(result.isCurrentMonth).isFalse()
        }

        @Test
        fun `projectedClosingBalance is null when no FUTURE transactions`() {
            val snap = snapshot(closingBalance = BigDecimal("500.00"))
            whenever(snapshotRepository.findByYearMonth(financialAccountId, pastYear, pastMonth)).thenReturn(snap)
            whenever(transactionRepository.getMonthTransferTotals(any(), any(), any())).thenReturn(transferTotals())
            whenever(transactionRepository.getMonthTotals(any(), any(), any()))
                .thenReturn(totals(totalFutureCredit = BigDecimal.ZERO, totalFutureDebit = BigDecimal.ZERO))

            val result = service.getMonthSummary(financialAccountId, accountId, pastYear, pastMonth)

            assertThat(result.projectedClosingBalance).isNull()
        }

        @Test
        fun `projectedClosingBalance = closingBalance + futureCredit - futureDebit`() {
            val snap = snapshot(closingBalance = BigDecimal("1000.00"))
            whenever(snapshotRepository.findByYearMonth(financialAccountId, pastYear, pastMonth)).thenReturn(snap)
            whenever(transactionRepository.getMonthTransferTotals(any(), any(), any())).thenReturn(transferTotals())
            whenever(transactionRepository.getMonthTotals(any(), any(), any()))
                .thenReturn(totals(totalFutureCredit = BigDecimal("200.00"), totalFutureDebit = BigDecimal("50.00")))

            val result = service.getMonthSummary(financialAccountId, accountId, pastYear, pastMonth)

            // 1000 + 200 - 50 = 1150
            assertThat(result.projectedClosingBalance).isEqualByComparingTo("1150.00")
        }
    }

    // ── dirty snapshot path ───────────────────────────────────────────────────

    @Nested
    @DisplayName("when snapshot is dirty")
    inner class DirtySnapshotTests {
        @Test
        fun `heals snapshot via recomputeChain and returns healed values`() {
            val dirty = snapshot(isDirty = true, closingBalance = BigDecimal("999.99"))
            val healed =
                snapshot(isDirty = false, closingBalance = BigDecimal("900.00"), totalIncome = BigDecimal("150.00"))

            whenever(snapshotRepository.findByYearMonth(financialAccountId, pastYear, pastMonth))
                .thenReturn(dirty) // first call: dirty
                .thenReturn(healed) // second call after healing
            whenever(transactionRepository.getMonthTransferTotals(any(), any(), any())).thenReturn(transferTotals())
            whenever(transactionRepository.getMonthTotals(any(), any(), any())).thenReturn(totals())

            val result = service.getMonthSummary(financialAccountId, accountId, pastYear, pastMonth)

            verify(snapshotComputationService).recomputeChain(financialAccountId, accountId, pastYear, pastMonth)
            assertThat(result.closingBalance).isEqualByComparingTo("900.00")
            assertThat(result.totalIncome).isEqualByComparingTo("150.00")
        }
    }

    // ── missing snapshot path ─────────────────────────────────────────────────

    @Nested
    @DisplayName("when snapshot does not exist")
    inner class MissingSnapshotTests {
        @Test
        fun `computes snapshot on demand and returns it`() {
            val computed = snapshot(closingBalance = BigDecimal("250.00"), totalIncome = BigDecimal("300.00"))

            whenever(snapshotRepository.findByYearMonth(financialAccountId, pastYear, pastMonth))
                .thenReturn(null) // first call: missing
                .thenReturn(computed) // second call after computation
            whenever(transactionRepository.getMonthTransferTotals(any(), any(), any())).thenReturn(transferTotals())
            whenever(transactionRepository.getMonthTotals(any(), any(), any())).thenReturn(totals())

            val result = service.getMonthSummary(financialAccountId, accountId, pastYear, pastMonth)

            verify(snapshotComputationService).recomputeChain(financialAccountId, accountId, pastYear, pastMonth)
            assertThat(result.closingBalance).isEqualByComparingTo("250.00")
        }

        @Test
        fun `returns zero-balance summary when snapshot computation yields nothing`() {
            whenever(snapshotRepository.findByYearMonth(financialAccountId, pastYear, pastMonth))
                .thenReturn(null)
                .thenReturn(null) // still null after recompute (edge case: empty account)
            whenever(transactionRepository.getMonthTransferTotals(any(), any(), any())).thenReturn(transferTotals())
            whenever(transactionRepository.getMonthTotals(any(), any(), any())).thenReturn(totals())

            val result = service.getMonthSummary(financialAccountId, accountId, pastYear, pastMonth)

            assertThat(result.closingBalance).isEqualByComparingTo(BigDecimal.ZERO)
            assertThat(result.openingBalance).isEqualByComparingTo(BigDecimal.ZERO)
        }
    }

    // ── aggregate (all accounts) ──────────────────────────────────────────────

    @Nested
    @DisplayName("getAggregateMonthlySummary")
    inner class AggregateMonthlySummaryTests {
        @Test
        fun `no accounts returns zero summary`() {
            whenever(transactionRepository.findFinancialAccountIdsByAccount(accountId)).thenReturn(emptyList())

            val result = service.getAggregateMonthlySummary(accountId, pastYear, pastMonth)

            assertThat(result.closingBalance).isEqualByComparingTo(BigDecimal.ZERO)
            assertThat(result.openingBalance).isEqualByComparingTo(BigDecimal.ZERO)
            assertThat(result.totalIncome).isEqualByComparingTo(BigDecimal.ZERO)
            assertThat(result.totalExpenses).isEqualByComparingTo(BigDecimal.ZERO)
            assertThat(result.projectedClosingBalance).isNull()
            assertThat(result.isCurrentMonth).isFalse()
        }

        @Test
        fun `sums totals across multiple accounts`() {
            val faId1 = UUID.randomUUID()
            val faId2 = UUID.randomUUID()
            whenever(transactionRepository.findFinancialAccountIdsByAccount(accountId))
                .thenReturn(listOf(faId1, faId2))

            // Account 1: income=300, expenses=100, closing=1200, opening=1000
            val snap1 =
                MonthlyBalanceSnapshot(
                    financialAccountId = faId1, accountId = accountId,
                    year = pastYear, month = pastMonth,
                    openingBalance = BigDecimal("1000.00"), closingBalance = BigDecimal("1200.00"),
                    totalIncome = BigDecimal("300.00"), totalExpenses = BigDecimal("100.00"),
                    computedAt = now, createdAt = now, updatedAt = now,
                )
            // Account 2: income=500, expenses=200, closing=2300, opening=2000
            val snap2 =
                MonthlyBalanceSnapshot(
                    financialAccountId = faId2, accountId = accountId,
                    year = pastYear, month = pastMonth,
                    openingBalance = BigDecimal("2000.00"), closingBalance = BigDecimal("2300.00"),
                    totalIncome = BigDecimal("500.00"), totalExpenses = BigDecimal("200.00"),
                    computedAt = now, createdAt = now, updatedAt = now,
                )
            whenever(snapshotRepository.findByYearMonth(faId1, pastYear, pastMonth)).thenReturn(snap1)
            whenever(snapshotRepository.findByYearMonth(faId2, pastYear, pastMonth)).thenReturn(snap2)
            whenever(transactionRepository.getMonthTransferTotals(any(), any(), any())).thenReturn(transferTotals())
            whenever(transactionRepository.getMonthTotals(any(), any(), any())).thenReturn(totals())

            val result = service.getAggregateMonthlySummary(accountId, pastYear, pastMonth)

            assertThat(result.totalIncome).isEqualByComparingTo("800.00")
            assertThat(result.totalExpenses).isEqualByComparingTo("300.00")
            assertThat(result.closingBalance).isEqualByComparingTo("3500.00")
            assertThat(result.openingBalance).isEqualByComparingTo("3000.00")
        }

        @Test
        fun `projectedClosingBalance is null when all accounts have no future transactions`() {
            val faId1 = UUID.randomUUID()
            whenever(transactionRepository.findFinancialAccountIdsByAccount(accountId))
                .thenReturn(listOf(faId1))
            val snap =
                MonthlyBalanceSnapshot(
                    financialAccountId = faId1, accountId = accountId,
                    year = pastYear, month = pastMonth,
                    openingBalance = BigDecimal.ZERO, closingBalance = BigDecimal("500.00"),
                    totalIncome = BigDecimal.ZERO, totalExpenses = BigDecimal.ZERO,
                    computedAt = now, createdAt = now, updatedAt = now,
                )
            whenever(snapshotRepository.findByYearMonth(faId1, pastYear, pastMonth)).thenReturn(snap)
            whenever(transactionRepository.getMonthTransferTotals(any(), any(), any())).thenReturn(transferTotals())
            whenever(transactionRepository.getMonthTotals(any(), any(), any()))
                .thenReturn(totals(totalFutureCredit = BigDecimal.ZERO, totalFutureDebit = BigDecimal.ZERO))

            val result = service.getAggregateMonthlySummary(accountId, pastYear, pastMonth)

            assertThat(result.projectedClosingBalance).isNull()
        }

        @Test
        fun `projectedClosingBalance is summed when at least one account has future transactions`() {
            val faId1 = UUID.randomUUID()
            val faId2 = UUID.randomUUID()
            whenever(transactionRepository.findFinancialAccountIdsByAccount(accountId))
                .thenReturn(listOf(faId1, faId2))

            val snap1 =
                MonthlyBalanceSnapshot(
                    financialAccountId = faId1, accountId = accountId,
                    year = pastYear, month = pastMonth,
                    openingBalance = BigDecimal.ZERO, closingBalance = BigDecimal("1000.00"),
                    totalIncome = BigDecimal.ZERO, totalExpenses = BigDecimal.ZERO,
                    computedAt = now, createdAt = now, updatedAt = now,
                )
            val snap2 =
                MonthlyBalanceSnapshot(
                    financialAccountId = faId2, accountId = accountId,
                    year = pastYear, month = pastMonth,
                    openingBalance = BigDecimal.ZERO, closingBalance = BigDecimal("2000.00"),
                    totalIncome = BigDecimal.ZERO, totalExpenses = BigDecimal.ZERO,
                    computedAt = now, createdAt = now, updatedAt = now,
                )
            whenever(snapshotRepository.findByYearMonth(faId1, pastYear, pastMonth)).thenReturn(snap1)
            whenever(snapshotRepository.findByYearMonth(faId2, pastYear, pastMonth)).thenReturn(snap2)
            whenever(transactionRepository.getMonthTransferTotals(any(), any(), any())).thenReturn(transferTotals())
            // faId1 has future, faId2 does not
            whenever(transactionRepository.getMonthTotals(eq(faId1), any(), any()))
                .thenReturn(totals(totalFutureCredit = BigDecimal("200.00")))
            whenever(transactionRepository.getMonthTotals(eq(faId2), any(), any()))
                .thenReturn(totals())

            val result = service.getAggregateMonthlySummary(accountId, pastYear, pastMonth)

            // faId1 projected = 1000 + 200 = 1200; faId2 projected = null (no future)
            // aggregate projected = 1200 (only non-null values summed)
            assertThat(result.projectedClosingBalance).isEqualByComparingTo("1200.00")
        }
    }
}
