package io.github.salomax.neotool.banking.domain.transaction

import jakarta.inject.Singleton
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

/**
 * Composes the monthly summary header from snapshot data and on-demand aggregates.
 *
 * ## Read path (single account)
 * 1. Look up the [MonthlyBalanceSnapshot] for (financialAccountId, year, month).
 *    - If the snapshot exists and is clean → use its cached values.
 *    - If the snapshot is dirty → heal it synchronously via [SnapshotComputationService], then use the healed values.
 *    - If no snapshot exists → compute it on-demand via [SnapshotComputationService].
 * 2. Fetch transfer totals on-demand (never cached in the snapshot).
 * 3. Compute projectedClosingBalance on-demand when FUTURE transactions exist.
 *
 * ## Read path (aggregate — all accounts)
 * When no financialAccountId is provided, [getAggregateMonthlySummary] fetches each
 * account's summary via [getMonthSummary] and sums all numeric fields.
 * Internal transfers (same-user A→B) cancel out naturally in the aggregate totals.
 */
@Singleton
open class MonthSummaryQueryService(
    private val snapshotRepository: MonthlyBalanceSnapshotRepository,
    private val transactionRepository: TransactionRepository,
    private val snapshotComputationService: SnapshotComputationService,
) {

    /**
     * Returns the [MonthSummary] for the given [financialAccountId], [year], and [month].
     *
     * Heals dirty or missing snapshots on-the-fly.
     */
    fun getMonthSummary(
        financialAccountId: UUID,
        accountId: UUID,
        year: Int,
        month: Int,
    ): MonthSummary {
        val snapshot = resolveSnapshot(financialAccountId, accountId, year, month)
        val transferTotals = transactionRepository.getMonthTransferTotals(financialAccountId, year, month)
        val txTotals = transactionRepository.getMonthTotals(financialAccountId, year, month)

        val projectedClosingBalance =
            if (txTotals.totalFutureCredit > BigDecimal.ZERO || txTotals.totalFutureDebit > BigDecimal.ZERO) {
                snapshot.closingBalance + txTotals.totalFutureCredit - txTotals.totalFutureDebit
            } else {
                null
            }

        val today = LocalDate.now()
        val isCurrentMonth = year == today.year && month == today.monthValue

        return MonthSummary(
            year = year,
            month = month,
            totalIncome = snapshot.totalIncome,
            totalExpenses = snapshot.totalExpenses,
            closingBalance = snapshot.closingBalance,
            openingBalance = snapshot.openingBalance,
            transfersIn = transferTotals.transfersIn,
            transfersOut = transferTotals.transfersOut,
            projectedClosingBalance = projectedClosingBalance,
            isCurrentMonth = isCurrentMonth,
        )
    }

    /**
     * Returns the aggregated [MonthSummary] across **all** financial accounts for [accountId].
     *
     * Each account's summary is computed independently via [getMonthSummary] and the
     * numeric fields are summed. When no accounts exist, a zero-value summary is returned.
     */
    fun getAggregateMonthlySummary(accountId: UUID, year: Int, month: Int): MonthSummary {
        val financialAccountIds = transactionRepository.findFinancialAccountIdsByAccount(accountId)
        if (financialAccountIds.isEmpty()) {
            val today = LocalDate.now()
            return MonthSummary(
                year = year,
                month = month,
                totalIncome = BigDecimal.ZERO,
                totalExpenses = BigDecimal.ZERO,
                closingBalance = BigDecimal.ZERO,
                openingBalance = BigDecimal.ZERO,
                transfersIn = BigDecimal.ZERO,
                transfersOut = BigDecimal.ZERO,
                projectedClosingBalance = null,
                isCurrentMonth = year == today.year && month == today.monthValue,
            )
        }

        val summaries = financialAccountIds.map { faId -> getMonthSummary(faId, accountId, year, month) }

        val projectedClosingBalances = summaries.mapNotNull { it.projectedClosingBalance }
        val today = LocalDate.now()
        return MonthSummary(
            year = year,
            month = month,
            totalIncome = summaries.fold(BigDecimal.ZERO) { acc, s -> acc + s.totalIncome },
            totalExpenses = summaries.fold(BigDecimal.ZERO) { acc, s -> acc + s.totalExpenses },
            closingBalance = summaries.fold(BigDecimal.ZERO) { acc, s -> acc + s.closingBalance },
            openingBalance = summaries.fold(BigDecimal.ZERO) { acc, s -> acc + s.openingBalance },
            transfersIn = summaries.fold(BigDecimal.ZERO) { acc, s -> acc + s.transfersIn },
            transfersOut = summaries.fold(BigDecimal.ZERO) { acc, s -> acc + s.transfersOut },
            projectedClosingBalance = if (projectedClosingBalances.isEmpty()) null
                                      else projectedClosingBalances.fold(BigDecimal.ZERO, BigDecimal::add),
            isCurrentMonth = year == today.year && month == today.monthValue,
        )
    }

    private fun resolveSnapshot(
        financialAccountId: UUID,
        accountId: UUID,
        year: Int,
        month: Int,
    ): MonthlyBalanceSnapshot {
        val existing = snapshotRepository.findByYearMonth(financialAccountId, year, month)
        return when {
            existing == null -> {
                // No snapshot yet — compute the chain from this month forward.
                snapshotComputationService.recomputeChain(financialAccountId, accountId, year, month)
                snapshotRepository.findByYearMonth(financialAccountId, year, month)
                    ?: MonthlyBalanceSnapshot(
                        financialAccountId = financialAccountId,
                        accountId = accountId,
                        year = year,
                        month = month,
                        openingBalance = BigDecimal.ZERO,
                        closingBalance = BigDecimal.ZERO,
                        totalIncome = BigDecimal.ZERO,
                        totalExpenses = BigDecimal.ZERO,
                    )
            }
            existing.isDirty -> {
                // Snapshot exists but is dirty — heal it synchronously.
                snapshotComputationService.recomputeChain(financialAccountId, accountId, year, month)
                snapshotRepository.findByYearMonth(financialAccountId, year, month) ?: existing
            }
            else -> existing
        }
    }
}
