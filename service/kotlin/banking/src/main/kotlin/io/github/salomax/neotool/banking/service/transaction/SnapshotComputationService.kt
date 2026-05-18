package io.github.salomax.neotool.banking.service.transaction

import io.github.salomax.neotool.banking.domain.transaction.*
import io.github.salomax.neotool.banking.repo.transaction.MonthlyBalanceSnapshotRepository
import io.github.salomax.neotool.banking.repo.transaction.TransactionRepository
import io.micronaut.transaction.annotation.Transactional
import jakarta.inject.Singleton
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID

/**
 * Computes and persists monthly balance snapshots for a financial account.
 *
 * ## Write flow (eager synchronous)
 * When a transaction is created, updated, or deleted, the caller must invoke
 * [recomputeChain] with the affected (year, month) so that the snapshot for
 * that month and every subsequent month up to the current month are recomputed
 * within the same request/transaction.
 *
 * ## Safety net
 * If a chain recompute fails partway through, [MonthlyBalanceSnapshotRepository.markDirtyFromMonth]
 * is called so that the read path can heal dirty rows on demand.
 */
@Singleton
open class SnapshotComputationService(
    private val transactionRepository: TransactionRepository,
    private val snapshotRepository: MonthlyBalanceSnapshotRepository,
) {
    /**
     * Recomputes the snapshot for a single (financialAccountId, year, month).
     *
     * The [openingBalance] must be provided by the caller (it is the closing balance
     * of the preceding month, or [BigDecimal.ZERO] at account genesis).
     *
     * @return the upserted [MonthlyBalanceSnapshot]
     */
    fun recomputeSnapshot(
        financialAccountId: UUID,
        accountId: UUID,
        year: Int,
        month: Int,
        openingBalance: BigDecimal,
    ): MonthlyBalanceSnapshot {
        val totals = transactionRepository.getMonthTotals(financialAccountId, year, month)
        val closingBalance = openingBalance + totals.totalCreditExclTransfer - totals.totalDebitExclTransfer

        val snapshot =
            MonthlyBalanceSnapshot(
                financialAccountId = financialAccountId,
                accountId = accountId,
                year = year,
                month = month,
                openingBalance = openingBalance,
                closingBalance = closingBalance,
                totalIncome = totals.totalIncome,
                totalExpenses = totals.totalExpenses,
                isDirty = false,
            )
        return snapshotRepository.upsert(snapshot)
    }

    /**
     * Recomputes snapshots for every month from [fromYear]/[fromMonth] through the current month
     * for the given [financialAccountId].
     *
     * Algorithm:
     * 1. Find the opening balance for [fromYear]/[fromMonth] — use the closing balance of the
     *    nearest clean snapshot strictly before that month, falling back to [BigDecimal.ZERO].
     * 2. Walk forward month by month, calling [recomputeSnapshot] and using each snapshot's
     *    [MonthlyBalanceSnapshot.closingBalance] as the next month's opening balance.
     *
     * If an exception is thrown partway through the chain, all remaining months from the
     * failure point onward are marked dirty so the read path can heal them.
     */
    @Transactional
    open fun recomputeChain(
        financialAccountId: UUID,
        accountId: UUID,
        fromYear: Int,
        fromMonth: Int,
    ) {
        val today = LocalDate.now()
        val currentYearMonth = YearMonth.of(today.year, today.monthValue)
        val startYearMonth = YearMonth.of(fromYear, fromMonth)

        if (startYearMonth.isAfter(currentYearMonth)) return

        val priorSnapshot = snapshotRepository.findLatestCleanBefore(financialAccountId, fromYear, fromMonth)
        var openingBalance = priorSnapshot?.closingBalance ?: BigDecimal.ZERO

        var cursor = startYearMonth
        while (!cursor.isAfter(currentYearMonth)) {
            try {
                val snapshot =
                    recomputeSnapshot(
                        financialAccountId = financialAccountId,
                        accountId = accountId,
                        year = cursor.year,
                        month = cursor.monthValue,
                        openingBalance = openingBalance,
                    )
                openingBalance = snapshot.closingBalance
                cursor = cursor.plusMonths(1)
            } catch (ex: Exception) {
                snapshotRepository.markDirtyFromMonth(financialAccountId, cursor.year, cursor.monthValue)
                throw ex
            }
        }
    }
}
