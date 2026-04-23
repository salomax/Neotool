package io.github.salomax.neotool.banking.domain.transaction

import io.github.salomax.neotool.banking.service.transaction.*
import io.github.salomax.neotool.banking.repo.transaction.MonthlyBalanceSnapshotRepository
import io.github.salomax.neotool.banking.repo.transaction.TransactionRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
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
import java.time.YearMonth
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
        service = MonthSummaryQueryService(snapshotRepository, transactionRepository, snapshotComputationService, mock())
        whenever(transactionRepository.getCategoryBreakdownForAccounts(any(), any(), any(), any(), any()))
            .thenReturn(emptyList())
        whenever(transactionRepository.getTagBreakdownForAccounts(any(), any(), any(), any(), any()))
            .thenReturn(emptyList())
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
        totalCreditExclTransfer: BigDecimal = totalCredit,
        totalDebitExclTransfer: BigDecimal = totalDebit,
        totalFutureCredit: BigDecimal = BigDecimal.ZERO,
        totalFutureDebit: BigDecimal = BigDecimal.ZERO,
        totalFutureIncome: BigDecimal = BigDecimal.ZERO,
        totalFutureExpenses: BigDecimal = BigDecimal.ZERO,
    ) = MonthTransactionTotals(
        financialAccountId = financialAccountId,
        year = pastYear,
        month = pastMonth,
        totalCredit = totalCredit,
        totalDebit = totalDebit,
        totalIncome = totalIncome,
        totalExpenses = totalExpenses,
        totalCreditExclTransfer = totalCreditExclTransfer,
        totalDebitExclTransfer = totalDebitExclTransfer,
        totalFutureCredit = totalFutureCredit,
        totalFutureDebit = totalFutureDebit,
        totalFutureIncome = totalFutureIncome,
        totalFutureExpenses = totalFutureExpenses,
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
            whenever(snapshotRepository.findLatestCleanBefore(financialAccountId, pastYear, pastMonth))
                .thenReturn(null)
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
            whenever(snapshotRepository.findLatestCleanBefore(financialAccountId, pastYear, pastMonth))
                .thenReturn(null)
            whenever(transactionRepository.findEarliestTransactionDate(financialAccountId)).thenReturn(null)
            whenever(transactionRepository.getMonthTransferTotals(any(), any(), any())).thenReturn(transferTotals())
            whenever(transactionRepository.getMonthTotals(any(), any(), any())).thenReturn(totals())

            val result = service.getMonthSummary(financialAccountId, accountId, pastYear, pastMonth)

            verify(snapshotComputationService).recomputeChain(financialAccountId, accountId, pastYear, pastMonth)
            assertThat(result.closingBalance).isEqualByComparingTo("250.00")
        }

        @Test
        fun `throws when snapshot computation yields nothing`() {
            whenever(snapshotRepository.findByYearMonth(financialAccountId, pastYear, pastMonth))
                .thenReturn(null)
                .thenReturn(null) // still null after recompute (edge case: empty account)
            whenever(snapshotRepository.findLatestCleanBefore(financialAccountId, pastYear, pastMonth))
                .thenReturn(null)
            whenever(transactionRepository.findEarliestTransactionDate(financialAccountId)).thenReturn(null)
            whenever(transactionRepository.getMonthTransferTotals(any(), any(), any())).thenReturn(transferTotals())
            whenever(transactionRepository.getMonthTotals(any(), any(), any())).thenReturn(totals())

            assertThatThrownBy {
                service.getMonthSummary(financialAccountId, accountId, pastYear, pastMonth)
            }
                .isInstanceOf(IllegalStateException::class.java)
                .hasMessageContaining("Snapshot recompute completed without materializing row")
        }

        @Test
        fun `missing snapshot recomputes from earliest transaction month when no clean snapshot exists`() {
            val requested = YearMonth.of(2026, 1)
            val computed = snapshot(year = requested.year, month = requested.monthValue, closingBalance = BigDecimal("123.45"))

            whenever(snapshotRepository.findByYearMonth(financialAccountId, requested.year, requested.monthValue))
                .thenReturn(null)
                .thenReturn(computed)
            whenever(snapshotRepository.findLatestCleanBefore(financialAccountId, requested.year, requested.monthValue))
                .thenReturn(null)
            whenever(transactionRepository.findEarliestTransactionDate(financialAccountId))
                .thenReturn(LocalDate.of(2025, 1, 15))
            whenever(transactionRepository.getMonthTransferTotals(any(), any(), any())).thenReturn(transferTotals())
            whenever(transactionRepository.getMonthTotals(any(), any(), any())).thenReturn(totals())

            service.getMonthSummary(financialAccountId, accountId, requested.year, requested.monthValue)

            verify(snapshotComputationService).recomputeChain(financialAccountId, accountId, 2025, 1)
        }

        @Test
        fun `missing snapshot recomputes from requested month when it is before earliest transaction`() {
            val requested = YearMonth.of(2025, 12)
            val computed = snapshot(year = requested.year, month = requested.monthValue, closingBalance = BigDecimal.ZERO)

            whenever(snapshotRepository.findByYearMonth(financialAccountId, requested.year, requested.monthValue))
                .thenReturn(null)
                .thenReturn(computed)
            whenever(snapshotRepository.findLatestCleanBefore(financialAccountId, requested.year, requested.monthValue))
                .thenReturn(null)
            whenever(transactionRepository.findEarliestTransactionDate(financialAccountId))
                .thenReturn(LocalDate.of(2026, 4, 10))
            whenever(transactionRepository.getMonthTransferTotals(any(), any(), any())).thenReturn(transferTotals())
            whenever(transactionRepository.getMonthTotals(any(), any(), any())).thenReturn(totals())

            service.getMonthSummary(financialAccountId, accountId, requested.year, requested.monthValue)

            verify(snapshotComputationService).recomputeChain(financialAccountId, accountId, 2025, 12)
        }

        @Test
        fun `dirty snapshot recomputes from month after latest clean snapshot`() {
            val requested = YearMonth.of(2026, 1)
            val dirty = snapshot(year = requested.year, month = requested.monthValue, isDirty = true)
            val healed = snapshot(year = requested.year, month = requested.monthValue, closingBalance = BigDecimal("88.00"))
            val priorClean = snapshot(year = 2025, month = 10, closingBalance = BigDecimal("77.00"))

            whenever(snapshotRepository.findByYearMonth(financialAccountId, requested.year, requested.monthValue))
                .thenReturn(dirty)
                .thenReturn(healed)
            whenever(snapshotRepository.findLatestCleanBefore(financialAccountId, requested.year, requested.monthValue))
                .thenReturn(priorClean)
            whenever(transactionRepository.getMonthTransferTotals(any(), any(), any())).thenReturn(transferTotals())
            whenever(transactionRepository.getMonthTotals(any(), any(), any())).thenReturn(totals())

            service.getMonthSummary(financialAccountId, accountId, requested.year, requested.monthValue)

            verify(snapshotComputationService).recomputeChain(financialAccountId, accountId, 2025, 11)
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
        fun `aggregate breakdown queries are scoped to included financial account ids`() {
            val faId1 = UUID.randomUUID()
            val faId2 = UUID.randomUUID()
            whenever(transactionRepository.findFinancialAccountIdsByAccount(accountId))
                .thenReturn(listOf(faId1, faId2))
            whenever(snapshotRepository.findByYearMonth(any(), eq(pastYear), eq(pastMonth)))
                .thenReturn(
                    MonthlyBalanceSnapshot(
                        financialAccountId = faId1,
                        accountId = accountId,
                        year = pastYear,
                        month = pastMonth,
                        openingBalance = BigDecimal.ZERO,
                        closingBalance = BigDecimal.ZERO,
                        totalIncome = BigDecimal.ZERO,
                        totalExpenses = BigDecimal.ZERO,
                        computedAt = now,
                        createdAt = now,
                        updatedAt = now,
                    ),
                )
            whenever(transactionRepository.getMonthTransferTotals(any(), any(), any())).thenReturn(transferTotals())
            whenever(transactionRepository.getMonthTotals(any(), any(), any())).thenReturn(totals())

            service.getAggregateMonthlySummary(accountId, pastYear, pastMonth)

            verify(transactionRepository).getCategoryBreakdownForAccounts(
                accountId,
                listOf(faId1, faId2),
                pastYear,
                pastMonth,
                CategoryApplicableTo.EXPENSE,
            )
            verify(transactionRepository).getCategoryBreakdownForAccounts(
                accountId,
                listOf(faId1, faId2),
                pastYear,
                pastMonth,
                CategoryApplicableTo.INCOME,
            )
            verify(transactionRepository).getTagBreakdownForAccounts(
                accountId,
                listOf(faId1, faId2),
                pastYear,
                pastMonth,
                null,
            )
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

    // ── future month path ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("when requested month is in the future")
    inner class FutureMonthTests {
        private val today = LocalDate.now()
        private val currentYearMonth = YearMonth.of(today.year, today.monthValue)

        @BeforeEach
        fun stubFutureMonthRepositoryReads() {
            whenever(transactionRepository.getMonthTransferTotals(any(), any(), any()))
                .thenReturn(transferTotals())
            whenever(transactionRepository.getCategoryBreakdown(any(), any(), any(), any(), any()))
                .thenReturn(emptyList())
            whenever(transactionRepository.getTagBreakdown(any(), any(), any(), any(), any()))
                .thenReturn(emptyList())
        }

        // The immediately next month
        private val nextMonth = currentYearMonth.plusMonths(1)

        // Two months ahead
        private val twoMonthsAhead = currentYearMonth.plusMonths(2)

        private fun currentSnapshot(closingBalance: BigDecimal) =
            MonthlyBalanceSnapshot(
                financialAccountId = financialAccountId,
                accountId = accountId,
                year = currentYearMonth.year,
                month = currentYearMonth.monthValue,
                openingBalance = BigDecimal.ZERO,
                closingBalance = closingBalance,
                totalIncome = BigDecimal.ZERO,
                totalExpenses = BigDecimal.ZERO,
                computedAt = now,
                createdAt = now,
                updatedAt = now,
            )

        private fun futureTotals(
            futureCredit: BigDecimal = BigDecimal.ZERO,
            futureDebit: BigDecimal = BigDecimal.ZERO,
            futureIncome: BigDecimal = BigDecimal.ZERO,
            futureExpenses: BigDecimal = BigDecimal.ZERO,
        ) = MonthTransactionTotals(
            financialAccountId = financialAccountId,
            year = nextMonth.year,
            month = nextMonth.monthValue,
            totalCredit = BigDecimal.ZERO,
            totalDebit = BigDecimal.ZERO,
            totalIncome = BigDecimal.ZERO,
            totalExpenses = BigDecimal.ZERO,
            totalCreditExclTransfer = BigDecimal.ZERO,
            totalDebitExclTransfer = BigDecimal.ZERO,
            totalFutureCredit = futureCredit,
            totalFutureDebit = futureDebit,
            totalFutureIncome = futureIncome,
            totalFutureExpenses = futureExpenses,
        )

        @Test
        fun `isFutureMonth is true for next month`() {
            whenever(
                snapshotRepository.findByYearMonth(
                    financialAccountId,
                    currentYearMonth.year,
                    currentYearMonth.monthValue,
                ),
            )
                .thenReturn(currentSnapshot(BigDecimal("10000.00")))
            whenever(transactionRepository.getMonthTotals(financialAccountId, nextMonth.year, nextMonth.monthValue))
                .thenReturn(futureTotals())

            val result =
                service.getMonthSummary(
                    financialAccountId,
                    accountId,
                    nextMonth.year,
                    nextMonth.monthValue,
                )

            assertThat(result.isFutureMonth).isTrue()
            assertThat(result.isCurrentMonth).isFalse()
        }

        @Test
        fun `does not persist snapshot for future month`() {
            whenever(
                snapshotRepository.findByYearMonth(
                    financialAccountId,
                    currentYearMonth.year,
                    currentYearMonth.monthValue,
                ),
            )
                .thenReturn(currentSnapshot(BigDecimal("10000.00")))
            whenever(transactionRepository.getMonthTotals(financialAccountId, nextMonth.year, nextMonth.monthValue))
                .thenReturn(futureTotals())

            service.getMonthSummary(
                financialAccountId,
                accountId,
                nextMonth.year,
                nextMonth.monthValue,
            )

            verify(
                snapshotComputationService,
                never(),
            ).recomputeChain(any(), any(), eq(nextMonth.year), eq(nextMonth.monthValue))
            verify(snapshotRepository, never()).upsert(any())
        }

        @Test
        fun `openingBalance equals current month closingBalance for immediately next month`() {
            whenever(
                snapshotRepository.findByYearMonth(
                    financialAccountId,
                    currentYearMonth.year,
                    currentYearMonth.monthValue,
                ),
            )
                .thenReturn(currentSnapshot(BigDecimal("10000.00")))
            whenever(transactionRepository.getMonthTotals(financialAccountId, nextMonth.year, nextMonth.monthValue))
                .thenReturn(futureTotals())

            val result =
                service.getMonthSummary(
                    financialAccountId,
                    accountId,
                    nextMonth.year,
                    nextMonth.monthValue,
                )

            assertThat(result.openingBalance).isEqualByComparingTo("10000.00")
            assertThat(result.closingBalance).isEqualByComparingTo("10000.00")
        }

        @Test
        fun `projectedClosingBalance = openingBalance + futureCredit - futureDebit`() {
            whenever(
                snapshotRepository.findByYearMonth(
                    financialAccountId,
                    currentYearMonth.year,
                    currentYearMonth.monthValue,
                ),
            )
                .thenReturn(currentSnapshot(BigDecimal("10000.00")))
            whenever(transactionRepository.getMonthTotals(financialAccountId, nextMonth.year, nextMonth.monthValue))
                .thenReturn(futureTotals(futureCredit = BigDecimal("3000.00"), futureDebit = BigDecimal("500.00")))

            val result =
                service.getMonthSummary(
                    financialAccountId,
                    accountId,
                    nextMonth.year,
                    nextMonth.monthValue,
                )

            // 10000 + 3000 - 500 = 12500
            assertThat(result.projectedClosingBalance).isEqualByComparingTo("12500.00")
        }

        @Test
        fun `projectedClosingBalance is null when no future transactions in month`() {
            whenever(
                snapshotRepository.findByYearMonth(
                    financialAccountId,
                    currentYearMonth.year,
                    currentYearMonth.monthValue,
                ),
            )
                .thenReturn(currentSnapshot(BigDecimal("10000.00")))
            whenever(transactionRepository.getMonthTotals(financialAccountId, nextMonth.year, nextMonth.monthValue))
                .thenReturn(futureTotals())

            val result =
                service.getMonthSummary(
                    financialAccountId,
                    accountId,
                    nextMonth.year,
                    nextMonth.monthValue,
                )

            assertThat(result.projectedClosingBalance).isNull()
        }

        @Test
        fun `totalIncome and totalExpenses reflect future planned transactions`() {
            whenever(
                snapshotRepository.findByYearMonth(
                    financialAccountId,
                    currentYearMonth.year,
                    currentYearMonth.monthValue,
                ),
            )
                .thenReturn(currentSnapshot(BigDecimal("10000.00")))
            whenever(transactionRepository.getMonthTotals(financialAccountId, nextMonth.year, nextMonth.monthValue))
                .thenReturn(
                    futureTotals(
                        futureCredit = BigDecimal("5000.00"),
                        futureDebit = BigDecimal("1200.00"),
                        futureIncome = BigDecimal("5000.00"),
                        futureExpenses = BigDecimal("1200.00"),
                    ),
                )

            val result =
                service.getMonthSummary(
                    financialAccountId,
                    accountId,
                    nextMonth.year,
                    nextMonth.monthValue,
                )

            assertThat(result.totalIncome).isEqualByComparingTo("5000.00")
            assertThat(result.totalExpenses).isEqualByComparingTo("1200.00")
        }

        @Test
        fun `transfersIn and transfersOut are zero for future months`() {
            whenever(
                snapshotRepository.findByYearMonth(
                    financialAccountId,
                    currentYearMonth.year,
                    currentYearMonth.monthValue,
                ),
            )
                .thenReturn(currentSnapshot(BigDecimal("10000.00")))
            whenever(transactionRepository.getMonthTotals(financialAccountId, nextMonth.year, nextMonth.monthValue))
                .thenReturn(futureTotals())

            val result =
                service.getMonthSummary(
                    financialAccountId,
                    accountId,
                    nextMonth.year,
                    nextMonth.monthValue,
                )

            assertThat(result.transfersIn).isEqualByComparingTo(BigDecimal.ZERO)
            assertThat(result.transfersOut).isEqualByComparingTo(BigDecimal.ZERO)
        }

        @Test
        fun `chains through intermediate future months when requesting month two ahead`() {
            // Current month closing = 10000
            // Intermediate (nextMonth) has 2000 credit, 500 debit → projected = 11500
            // Requested (twoMonthsAhead) has 1000 credit, 200 debit → projected = 12300
            whenever(
                snapshotRepository.findByYearMonth(
                    financialAccountId,
                    currentYearMonth.year,
                    currentYearMonth.monthValue,
                ),
            )
                .thenReturn(currentSnapshot(BigDecimal("10000.00")))
            whenever(transactionRepository.getMonthTotals(financialAccountId, nextMonth.year, nextMonth.monthValue))
                .thenReturn(futureTotals(futureCredit = BigDecimal("2000.00"), futureDebit = BigDecimal("500.00")))
            whenever(
                transactionRepository.getMonthTotals(
                    financialAccountId,
                    twoMonthsAhead.year,
                    twoMonthsAhead.monthValue,
                ),
            )
                .thenReturn(
                    MonthTransactionTotals(
                        financialAccountId = financialAccountId,
                        year = twoMonthsAhead.year, month = twoMonthsAhead.monthValue,
                        totalCredit = BigDecimal.ZERO, totalDebit = BigDecimal.ZERO,
                        totalIncome = BigDecimal.ZERO, totalExpenses = BigDecimal.ZERO,
                        totalCreditExclTransfer = BigDecimal.ZERO,
                        totalDebitExclTransfer = BigDecimal.ZERO,
                        totalFutureCredit = BigDecimal("1000.00"),
                        totalFutureDebit = BigDecimal("200.00"),
                        totalFutureIncome = BigDecimal("1000.00"),
                        totalFutureExpenses = BigDecimal("200.00"),
                    ),
                )

            val result =
                service.getMonthSummary(
                    financialAccountId,
                    accountId,
                    twoMonthsAhead.year,
                    twoMonthsAhead.monthValue,
                )

            // openingBalance = 10000 + (2000 - 500) = 11500
            assertThat(result.openingBalance).isEqualByComparingTo("11500.00")
            // projectedClosingBalance = 11500 + 1000 - 200 = 12300
            assertThat(result.projectedClosingBalance).isEqualByComparingTo("12300.00")
            assertThat(result.isFutureMonth).isTrue()
        }

        @Test
        fun `getAggregateMonthlySummary returns isFutureMonth true for future months`() {
            whenever(transactionRepository.findFinancialAccountIdsByAccount(accountId)).thenReturn(emptyList())

            val result = service.getAggregateMonthlySummary(accountId, nextMonth.year, nextMonth.monthValue)

            assertThat(result.isFutureMonth).isTrue()
            assertThat(result.isCurrentMonth).isFalse()
        }

        @Test
        fun `getAggregateMonthlySummary sums projected balances across accounts for future month`() {
            val faId1 = UUID.randomUUID()
            val faId2 = UUID.randomUUID()
            whenever(transactionRepository.findFinancialAccountIdsByAccount(accountId))
                .thenReturn(listOf(faId1, faId2))

            val snap1 =
                MonthlyBalanceSnapshot(
                    financialAccountId = faId1, accountId = accountId,
                    year = currentYearMonth.year, month = currentYearMonth.monthValue,
                    openingBalance = BigDecimal.ZERO, closingBalance = BigDecimal("5000.00"),
                    totalIncome = BigDecimal.ZERO, totalExpenses = BigDecimal.ZERO,
                    computedAt = now, createdAt = now, updatedAt = now,
                )
            val snap2 =
                MonthlyBalanceSnapshot(
                    financialAccountId = faId2, accountId = accountId,
                    year = currentYearMonth.year, month = currentYearMonth.monthValue,
                    openingBalance = BigDecimal.ZERO, closingBalance = BigDecimal("3000.00"),
                    totalIncome = BigDecimal.ZERO, totalExpenses = BigDecimal.ZERO,
                    computedAt = now, createdAt = now, updatedAt = now,
                )
            whenever(
                snapshotRepository.findByYearMonth(faId1, currentYearMonth.year, currentYearMonth.monthValue),
            ).thenReturn(snap1)
            whenever(
                snapshotRepository.findByYearMonth(faId2, currentYearMonth.year, currentYearMonth.monthValue),
            ).thenReturn(snap2)

            // faId1: +1000 future credit; faId2: +500 future credit
            whenever(transactionRepository.getMonthTotals(eq(faId1), eq(nextMonth.year), eq(nextMonth.monthValue)))
                .thenReturn(
                    MonthTransactionTotals(
                        financialAccountId = faId1, year = nextMonth.year, month = nextMonth.monthValue,
                        totalCredit = BigDecimal.ZERO, totalDebit = BigDecimal.ZERO,
                        totalIncome = BigDecimal.ZERO, totalExpenses = BigDecimal.ZERO,
                        totalCreditExclTransfer = BigDecimal.ZERO,
                        totalDebitExclTransfer = BigDecimal.ZERO,
                        totalFutureCredit = BigDecimal("1000.00"), totalFutureDebit = BigDecimal.ZERO,
                        totalFutureIncome = BigDecimal("1000.00"), totalFutureExpenses = BigDecimal.ZERO,
                    ),
                )
            whenever(transactionRepository.getMonthTotals(eq(faId2), eq(nextMonth.year), eq(nextMonth.monthValue)))
                .thenReturn(
                    MonthTransactionTotals(
                        financialAccountId = faId2, year = nextMonth.year, month = nextMonth.monthValue,
                        totalCredit = BigDecimal.ZERO, totalDebit = BigDecimal.ZERO,
                        totalIncome = BigDecimal.ZERO, totalExpenses = BigDecimal.ZERO,
                        totalCreditExclTransfer = BigDecimal.ZERO,
                        totalDebitExclTransfer = BigDecimal.ZERO,
                        totalFutureCredit = BigDecimal("500.00"), totalFutureDebit = BigDecimal.ZERO,
                        totalFutureIncome = BigDecimal("500.00"), totalFutureExpenses = BigDecimal.ZERO,
                    ),
                )

            val result = service.getAggregateMonthlySummary(accountId, nextMonth.year, nextMonth.monthValue)

            // closingBalance = openingBalance (no real txs): 5000 + 3000 = 8000
            assertThat(result.closingBalance).isEqualByComparingTo("8000.00")
            // projectedClosingBalance: (5000+1000) + (3000+500) = 9500
            assertThat(result.projectedClosingBalance).isEqualByComparingTo("9500.00")
            assertThat(result.isFutureMonth).isTrue()
        }
    }
}
