package io.github.salomax.neotool.banking.domain.transaction

import io.github.salomax.neotool.banking.service.transaction.*
import io.github.salomax.neotool.banking.repo.transaction.MonthlyBalanceSnapshotRepository
import io.github.salomax.neotool.banking.repo.transaction.TransactionRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import java.util.UUID

@DisplayName("SnapshotComputationService")
class SnapshotComputationServiceTest {
    private lateinit var transactionRepository: TransactionRepository
    private lateinit var snapshotRepository: MonthlyBalanceSnapshotRepository
    private lateinit var service: SnapshotComputationService

    private val financialAccountId = UUID.randomUUID()
    private val accountId = UUID.randomUUID()
    private val now = Instant.now()

    @BeforeEach
    fun setUp() {
        transactionRepository = mock()
        snapshotRepository = mock()
        service = SnapshotComputationService(transactionRepository, snapshotRepository)
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private fun totals(
        totalCredit: BigDecimal = BigDecimal.ZERO,
        totalDebit: BigDecimal = BigDecimal.ZERO,
        totalIncome: BigDecimal = BigDecimal.ZERO,
        totalExpenses: BigDecimal = BigDecimal.ZERO,
        year: Int = 2024,
        month: Int = 1,
    ) = MonthTransactionTotals(
        financialAccountId = financialAccountId,
        year = year,
        month = month,
        totalCredit = totalCredit,
        totalDebit = totalDebit,
        totalIncome = totalIncome,
        totalExpenses = totalExpenses,
        totalFutureCredit = BigDecimal.ZERO,
        totalFutureDebit = BigDecimal.ZERO,
        totalFutureIncome = BigDecimal.ZERO,
        totalFutureExpenses = BigDecimal.ZERO,
    )

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
        computedAt = now,
        createdAt = now,
        updatedAt = now,
    )

    /** Returns the number of months from [from] to the current month (inclusive). */
    private fun monthsFromToCurrent(from: YearMonth): Int {
        val current = YearMonth.now()
        return (from.until(current, ChronoUnit.MONTHS) + 1).toInt()
    }

    // ── recomputeSnapshot ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("recomputeSnapshot")
    inner class RecomputeSnapshotTests {
        @Test
        fun `closingBalance = openingBalance + totalCredit - totalDebit`() {
            val opening = BigDecimal("1000.00")
            whenever(transactionRepository.getMonthTotals(financialAccountId, 2024, 3))
                .thenReturn(
                    totals(
                        totalCredit = BigDecimal("500.00"),
                        totalDebit = BigDecimal("200.00"),
                        year = 2024,
                        month = 3,
                    ),
                )
            val saved = snapshot(2024, 3, openingBalance = opening, closingBalance = BigDecimal("1300.00"))
            whenever(snapshotRepository.upsert(any())).thenReturn(saved)

            val result = service.recomputeSnapshot(financialAccountId, accountId, 2024, 3, opening)

            val captor = argumentCaptor<MonthlyBalanceSnapshot>()
            verify(snapshotRepository).upsert(captor.capture())
            assertThat(captor.firstValue.openingBalance).isEqualByComparingTo("1000.00")
            assertThat(captor.firstValue.closingBalance).isEqualByComparingTo("1300.00") // 1000 + 500 - 200
            assertThat(captor.firstValue.isDirty).isFalse()
            assertThat(result).isSameAs(saved)
        }

        @Test
        fun `totalIncome and totalExpenses come from the aggregate, not derived from closing`() {
            whenever(transactionRepository.getMonthTotals(financialAccountId, 2024, 1))
                .thenReturn(
                    totals(
                        totalCredit = BigDecimal("300.00"),
                        totalDebit = BigDecimal("100.00"),
                        totalIncome = BigDecimal("250.00"),
                        totalExpenses = BigDecimal("80.00"),
                    ),
                )
            val saved = snapshot(2024, 1, totalIncome = BigDecimal("250.00"), totalExpenses = BigDecimal("80.00"))
            whenever(snapshotRepository.upsert(any())).thenReturn(saved)

            service.recomputeSnapshot(financialAccountId, accountId, 2024, 1, BigDecimal.ZERO)

            val captor = argumentCaptor<MonthlyBalanceSnapshot>()
            verify(snapshotRepository).upsert(captor.capture())
            assertThat(captor.firstValue.totalIncome).isEqualByComparingTo("250.00")
            assertThat(captor.firstValue.totalExpenses).isEqualByComparingTo("80.00")
        }

        @Test
        fun `zero-activity month leaves balance unchanged`() {
            whenever(transactionRepository.getMonthTotals(financialAccountId, 2024, 2))
                .thenReturn(totals(year = 2024, month = 2))
            val opening = BigDecimal("500.00")
            val saved = snapshot(2024, 2, openingBalance = opening, closingBalance = opening)
            whenever(snapshotRepository.upsert(any())).thenReturn(saved)

            service.recomputeSnapshot(financialAccountId, accountId, 2024, 2, opening)

            val captor = argumentCaptor<MonthlyBalanceSnapshot>()
            verify(snapshotRepository).upsert(captor.capture())
            assertThat(captor.firstValue.closingBalance).isEqualByComparingTo(opening)
        }
    }

    // ── recomputeChain ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("recomputeChain")
    inner class RecomputeChainTests {
        /**
         * Stubs all months from [from] through current month with zero-activity totals
         * and returns the upserted snapshot via identity (returns the argument).
         */
        private fun stubZeroActivityChain(from: YearMonth) {
            val current = YearMonth.now()
            var cursor = from
            while (!cursor.isAfter(current)) {
                whenever(transactionRepository.getMonthTotals(financialAccountId, cursor.year, cursor.monthValue))
                    .thenReturn(totals(year = cursor.year, month = cursor.monthValue))
                cursor = cursor.plusMonths(1)
            }
            whenever(snapshotRepository.upsert(any())).thenAnswer { inv ->
                inv.getArgument(0) as MonthlyBalanceSnapshot
            }
        }

        @Test
        fun `future fromMonth is a no-op — no snapshots are computed`() {
            val futureYear = LocalDate.now().year + 1

            service.recomputeChain(financialAccountId, accountId, futureYear, 1)

            verify(transactionRepository, never()).getMonthTotals(any(), any(), any())
            verify(snapshotRepository, never()).upsert(any())
        }

        @Test
        fun `chain with no prior snapshot uses zero as opening balance for fromMonth`() {
            val from = YearMonth.now()
            whenever(snapshotRepository.findLatestCleanBefore(financialAccountId, from.year, from.monthValue))
                .thenReturn(null)
            stubZeroActivityChain(from)

            service.recomputeChain(financialAccountId, accountId, from.year, from.monthValue)

            val captor = argumentCaptor<MonthlyBalanceSnapshot>()
            verify(snapshotRepository).upsert(captor.capture())
            assertThat(captor.firstValue.openingBalance).isEqualByComparingTo(BigDecimal.ZERO)
        }

        @Test
        fun `chain uses prior snapshot closingBalance as opening for fromMonth`() {
            val from = YearMonth.now()
            val priorClosing = BigDecimal("800.00")
            whenever(snapshotRepository.findLatestCleanBefore(financialAccountId, from.year, from.monthValue))
                .thenReturn(snapshot(from.year, from.minusMonths(1).monthValue, closingBalance = priorClosing))
            stubZeroActivityChain(from)

            service.recomputeChain(financialAccountId, accountId, from.year, from.monthValue)

            val captor = argumentCaptor<MonthlyBalanceSnapshot>()
            verify(snapshotRepository).upsert(captor.capture())
            assertThat(captor.firstValue.openingBalance).isEqualByComparingTo(priorClosing)
        }

        @Test
        fun `closingBalance of one month becomes openingBalance of the next (two-month chain)`() {
            // Use a two-month past window we know has passed: Jan–Feb 2024
            val jan = YearMonth.of(2024, 1)
            val feb = YearMonth.of(2024, 2)
            val current = YearMonth.now()
            // This test only makes sense when current >= Feb 2024 (which it always will be by now)
            if (current < feb) return

            whenever(snapshotRepository.findLatestCleanBefore(financialAccountId, 2024, 1))
                .thenReturn(null)

            // Jan: credit 500, debit 100 → closing = 400
            whenever(transactionRepository.getMonthTotals(financialAccountId, 2024, 1))
                .thenReturn(
                    totals(
                        totalCredit = BigDecimal("500.00"),
                        totalDebit = BigDecimal("100.00"),
                        year = 2024,
                        month = 1,
                    ),
                )

            // All remaining months (Feb 2024 → current): zero activity
            var cursor = feb
            while (!cursor.isAfter(current)) {
                whenever(transactionRepository.getMonthTotals(financialAccountId, cursor.year, cursor.monthValue))
                    .thenReturn(totals(year = cursor.year, month = cursor.monthValue))
                cursor = cursor.plusMonths(1)
            }
            whenever(snapshotRepository.upsert(any())).thenAnswer { inv ->
                inv.getArgument(0) as MonthlyBalanceSnapshot
            }

            service.recomputeChain(financialAccountId, accountId, 2024, 1)

            val captor = argumentCaptor<MonthlyBalanceSnapshot>()
            val expectedMonths = monthsFromToCurrent(jan)
            verify(snapshotRepository, times(expectedMonths)).upsert(captor.capture())

            val janSnap = captor.allValues.first { it.year == 2024 && it.month == 1 }
            assertThat(janSnap.openingBalance).isEqualByComparingTo(BigDecimal.ZERO)
            assertThat(janSnap.closingBalance).isEqualByComparingTo("400.00")

            val febSnap = captor.allValues.first { it.year == 2024 && it.month == 2 }
            assertThat(febSnap.openingBalance).isEqualByComparingTo("400.00")
        }

        @Test
        fun `marks dirty from failure point and rethrows on exception`() {
            val current = YearMonth.now()
            whenever(snapshotRepository.findLatestCleanBefore(financialAccountId, current.year, current.monthValue))
                .thenReturn(null)
            whenever(transactionRepository.getMonthTotals(financialAccountId, current.year, current.monthValue))
                .thenThrow(RuntimeException("DB failure"))

            assertThrows<RuntimeException> {
                service.recomputeChain(financialAccountId, accountId, current.year, current.monthValue)
            }

            verify(snapshotRepository).markDirtyFromMonth(financialAccountId, current.year, current.monthValue)
        }
    }
}
