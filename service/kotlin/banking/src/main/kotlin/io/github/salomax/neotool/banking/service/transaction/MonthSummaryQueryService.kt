package io.github.salomax.neotool.banking.service.transaction

import io.github.salomax.neotool.banking.domain.transaction.*
import io.micronaut.transaction.annotation.Transactional
import jakarta.inject.Singleton
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID

/**
 * Composes the monthly summary header from snapshot data and on-demand aggregates.
 *
 * ## Read path — past / current month (single account)
 * 1. Look up the [MonthlyBalanceSnapshot] for (financialAccountId, year, month).
 *    - If the snapshot exists and is clean → use its cached values.
 *    - If the snapshot is dirty → heal it synchronously via [SnapshotComputationService], then use the healed values.
 *    - If no snapshot exists → compute it on-demand via [SnapshotComputationService].
 * 2. Fetch transfer totals on-demand (never cached in the snapshot).
 * 3. Compute projectedClosingBalance on-demand when FUTURE transactions exist.
 *
 * ## Read path — future month (single account)
 * No snapshot is persisted for future months (Option B). Instead:
 * 1. Resolve the current month's snapshot as the "anchor" closing balance.
 * 2. Chain-project any intermediate future months (between current and requested) by summing
 *    their FUTURE transaction totals.
 * 3. Compute openingBalance for the requested month from the projected chain.
 * 4. Return a [MonthSummary] with [MonthSummary.isFutureMonth] = true, closingBalance = openingBalance
 *    (no real transactions), and projectedClosingBalance = openingBalance + FUTURE totals for the month.
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
     * Future months are handled without snapshot persistence — projections are computed on-the-fly.
     * Past/current months heal dirty or missing snapshots on-the-fly.
     */
    @Transactional
    open fun getMonthSummary(
        financialAccountId: UUID,
        accountId: UUID,
        year: Int,
        month: Int,
    ): MonthSummary {
        val today = LocalDate.now()
        val currentYearMonth = YearMonth.of(today.year, today.monthValue)
        val requestedYearMonth = YearMonth.of(year, month)

        return if (requestedYearMonth.isAfter(currentYearMonth)) {
            getFutureMonthSummary(financialAccountId, accountId, year, month, currentYearMonth)
        } else {
            getRealMonthSummary(financialAccountId, accountId, year, month, today)
        }
    }

    /**
     * Returns category totals for the given period (CAT-14). Percentage is computed from sum of amounts.
     * Used by `transactionsByCategory` query and by MonthSummary category breakdown (CAT-15).
     */
    fun getCategoryBreakdown(
        accountId: UUID,
        financialAccountId: UUID?,
        year: Int,
        month: Int,
        applicableTo: CategoryApplicableTo?,
    ): List<CategoryTotal> {
        val rows =
            transactionRepository.getCategoryBreakdown(
                accountId = accountId,
                financialAccountId = financialAccountId,
                year = year,
                month = month,
                applicableTo = applicableTo,
            )
        val totalAmount = rows.fold(BigDecimal.ZERO) { acc, r -> acc + r.totalAmount }
        return rows.map { row ->
            val pct =
                if (totalAmount > BigDecimal.ZERO) {
                    (row.totalAmount.toDouble() / totalAmount.toDouble() * 100.0)
                        .toBigDecimal().setScale(2, RoundingMode.HALF_UP).toDouble()
                } else {
                    0.0
                }
            CategoryTotal.fromRow(row, pct)
        }
    }

    /**
     * Returns tag totals for the given period. Percentage is computed from sum of grouped tag amounts.
     * Used by MonthSummary tag breakdown.
     */
    fun getTagBreakdown(
        accountId: UUID,
        financialAccountId: UUID?,
        year: Int,
        month: Int,
        applicableTo: CategoryApplicableTo?,
    ): List<TagTotal> {
        val rows =
            transactionRepository.getTagBreakdown(
                accountId = accountId,
                financialAccountId = financialAccountId,
                year = year,
                month = month,
                applicableTo = applicableTo,
            )
        val totalAmount = rows.fold(BigDecimal.ZERO) { acc, r -> acc + r.totalAmount }
        return rows.map { row ->
            val pct =
                if (totalAmount > BigDecimal.ZERO) {
                    (row.totalAmount.toDouble() / totalAmount.toDouble() * 100.0)
                        .toBigDecimal().setScale(2, RoundingMode.HALF_UP).toDouble()
                } else {
                    0.0
                }
            TagTotal.fromRow(row, pct)
        }
    }

    /**
     * Returns the aggregated [MonthSummary] across **all** financial accounts for [accountId].
     *
     * Each account's summary is computed independently via [getMonthSummary] and the
     * numeric fields are summed. When no accounts exist, a zero-value summary is returned.
     */
    @Transactional
    open fun getAggregateMonthlySummary(
        accountId: UUID,
        year: Int,
        month: Int,
    ): MonthSummary {
        val financialAccountIds = transactionRepository.findFinancialAccountIdsByAccount(accountId)
        val today = LocalDate.now()
        val currentYearMonth = YearMonth.of(today.year, today.monthValue)
        val isFuture = YearMonth.of(year, month).isAfter(currentYearMonth)

        if (financialAccountIds.isEmpty()) {
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
                isFutureMonth = isFuture,
                expensesByCategory = emptyList(),
                incomeByCategory = emptyList(),
                tagsSummary = emptyList(),
            )
        }

        val summaries = financialAccountIds.map { faId -> getMonthSummary(faId, accountId, year, month) }

        val projectedClosingBalances = summaries.mapNotNull { it.projectedClosingBalance }
        val expensesByCategory = getCategoryBreakdown(accountId, null, year, month, CategoryApplicableTo.EXPENSE)
        val incomeByCategory = getCategoryBreakdown(accountId, null, year, month, CategoryApplicableTo.INCOME)
        val tagsSummary = getTagBreakdown(accountId, null, year, month, null)

        return MonthSummary(
            year = year,
            month = month,
            totalIncome = summaries.fold(BigDecimal.ZERO) { acc, s -> acc + s.totalIncome },
            totalExpenses = summaries.fold(BigDecimal.ZERO) { acc, s -> acc + s.totalExpenses },
            closingBalance = summaries.fold(BigDecimal.ZERO) { acc, s -> acc + s.closingBalance },
            openingBalance = summaries.fold(BigDecimal.ZERO) { acc, s -> acc + s.openingBalance },
            transfersIn = summaries.fold(BigDecimal.ZERO) { acc, s -> acc + s.transfersIn },
            transfersOut = summaries.fold(BigDecimal.ZERO) { acc, s -> acc + s.transfersOut },
            projectedClosingBalance =
                if (projectedClosingBalances.isEmpty()) {
                    null
                } else {
                    projectedClosingBalances.fold(BigDecimal.ZERO, BigDecimal::add)
                },
            isCurrentMonth = year == today.year && month == today.monthValue,
            isFutureMonth = isFuture,
            expensesByCategory = expensesByCategory,
            incomeByCategory = incomeByCategory,
            tagsSummary = tagsSummary,
        )
    }

    // ── private helpers ───────────────────────────────────────────────────────

    private fun getRealMonthSummary(
        financialAccountId: UUID,
        accountId: UUID,
        year: Int,
        month: Int,
        today: LocalDate,
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

        val expensesByCategory =
            getCategoryBreakdown(accountId, financialAccountId, year, month, CategoryApplicableTo.EXPENSE)
        val incomeByCategory =
            getCategoryBreakdown(accountId, financialAccountId, year, month, CategoryApplicableTo.INCOME)
        val tagsSummary = getTagBreakdown(accountId, financialAccountId, year, month, null)

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
            isCurrentMonth = year == today.year && month == today.monthValue,
            isFutureMonth = false,
            expensesByCategory = expensesByCategory,
            incomeByCategory = incomeByCategory,
            tagsSummary = tagsSummary,
        )
    }

    /**
     * Computes a projected [MonthSummary] for a month strictly after the current calendar month.
     *
     * Algorithm:
     * 1. Resolve the current month's snapshot as the anchor closing balance.
     * 2. For each intermediate future month (current+1 until requested-1), project the balance
     *    forward by adding FUTURE credits and subtracting FUTURE debits.
     * 3. Use the resulting balance as openingBalance for the requested month.
     * 4. Compute projectedClosingBalance = openingBalance + requested month's FUTURE totals.
     *
     * No snapshot rows are created or persisted for future months.
     */
    private fun getFutureMonthSummary(
        financialAccountId: UUID,
        accountId: UUID,
        year: Int,
        month: Int,
        currentYearMonth: YearMonth,
    ): MonthSummary {
        // Anchor: closing balance of the current (real) month
        val currentSnapshot =
            resolveSnapshot(
                financialAccountId,
                accountId,
                currentYearMonth.year,
                currentYearMonth.monthValue,
            )

        // Project through intermediate future months (if any)
        var openingBalance = currentSnapshot.closingBalance
        var cursor = currentYearMonth.plusMonths(1)
        val requestedYearMonth = YearMonth.of(year, month)
        while (cursor.isBefore(requestedYearMonth)) {
            val intermediateTotals =
                transactionRepository.getMonthTotals(
                    financialAccountId,
                    cursor.year,
                    cursor.monthValue,
                )
            openingBalance += intermediateTotals.totalFutureCredit - intermediateTotals.totalFutureDebit
            cursor = cursor.plusMonths(1)
        }

        // Totals for the requested future month
        val txTotals = transactionRepository.getMonthTotals(financialAccountId, year, month)

        val projectedClosingBalance =
            if (txTotals.totalFutureCredit > BigDecimal.ZERO || txTotals.totalFutureDebit > BigDecimal.ZERO) {
                openingBalance + txTotals.totalFutureCredit - txTotals.totalFutureDebit
            } else {
                null
            }

        return MonthSummary(
            year = year,
            month = month,
            totalIncome = txTotals.totalFutureIncome,
            totalExpenses = txTotals.totalFutureExpenses,
            closingBalance = openingBalance,
            openingBalance = openingBalance,
            transfersIn = BigDecimal.ZERO,
            transfersOut = BigDecimal.ZERO,
            projectedClosingBalance = projectedClosingBalance,
            isCurrentMonth = false,
            isFutureMonth = true,
            expensesByCategory = emptyList(),
            incomeByCategory = emptyList(),
            tagsSummary = emptyList(),
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
