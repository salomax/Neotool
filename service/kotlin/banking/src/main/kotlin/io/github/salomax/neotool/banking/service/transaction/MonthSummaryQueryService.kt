package io.github.salomax.neotool.banking.service.transaction

import io.github.salomax.neotool.banking.domain.transaction.CategoryApplicableTo
import io.github.salomax.neotool.banking.domain.transaction.CategoryBreakdownRow
import io.github.salomax.neotool.banking.domain.transaction.CategoryTotal
import io.github.salomax.neotool.banking.domain.transaction.MonthSummary
import io.github.salomax.neotool.banking.domain.transaction.MonthlyBalanceSnapshot
import io.github.salomax.neotool.banking.domain.transaction.TagBreakdownRow
import io.github.salomax.neotool.banking.domain.transaction.TagTotal
import io.github.salomax.neotool.banking.repo.transaction.MonthlyBalanceSnapshotRepository
import io.github.salomax.neotool.banking.repo.transaction.TransactionRepository
import io.github.salomax.neotool.banking.service.account.FinancialAccountGuard
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
    private val financialAccountGuard: FinancialAccountGuard,
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
        financialAccountGuard.requireOwnership(financialAccountId, accountId)
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
     * Uncategorized transactions are exposed as an explicit row with category = null,
     * uncategorized = true, ordered after real categories.
     */
    fun getCategoryBreakdown(
        accountId: UUID,
        financialAccountId: UUID?,
        year: Int,
        month: Int,
        applicableTo: CategoryApplicableTo?,
    ): List<CategoryTotal> {
        return toCategoryTotals(
            transactionRepository.getCategoryBreakdown(
                accountId = accountId,
                financialAccountId = financialAccountId,
                year = year,
                month = month,
                applicableTo = applicableTo,
            ),
        )
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
        return toTagTotals(
            transactionRepository.getTagBreakdown(
                accountId = accountId,
                financialAccountId = financialAccountId,
                year = year,
                month = month,
                applicableTo = applicableTo,
            ),
        )
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
        val expensesByCategory =
            toCategoryTotals(
                transactionRepository.getCategoryBreakdownForAccounts(
                    accountId = accountId,
                    includedFinancialAccountIds = financialAccountIds,
                    year = year,
                    month = month,
                    applicableTo = CategoryApplicableTo.EXPENSE,
                ),
            )
        val incomeByCategory =
            toCategoryTotals(
                transactionRepository.getCategoryBreakdownForAccounts(
                    accountId = accountId,
                    includedFinancialAccountIds = financialAccountIds,
                    year = year,
                    month = month,
                    applicableTo = CategoryApplicableTo.INCOME,
                ),
            )
        val tagsSummary =
            toTagTotals(
                transactionRepository.getTagBreakdownForAccounts(
                    accountId = accountId,
                    includedFinancialAccountIds = financialAccountIds,
                    year = year,
                    month = month,
                    applicableTo = null,
                ),
            )

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
     * 2. For each intermediate future month (current+1 until requested-1), step the balance forward
     *    using POSTED/PENDING net (totalCredit − totalDebit) plus FUTURE net, same as a real snapshot chain.
     * 3. [openingBalance] for the requested month is the balance after those intermediate steps.
     * 4. [closingBalance] = openingBalance + requested month's POSTED/PENDING net.
     * 5. [totalIncome] / [totalExpenses] combine POSTED/PENDING and FUTURE buckets (matches how users see
     *    “next month” after posting a dated transaction or planning FUTURE rows).
     * 6. [projectedClosingBalance] = closingBalance + requested month's FUTURE credit/debit (when any).
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
            openingBalance += intermediateTotals.totalCredit - intermediateTotals.totalDebit
            openingBalance += intermediateTotals.totalFutureCredit - intermediateTotals.totalFutureDebit
            cursor = cursor.plusMonths(1)
        }

        val txTotals = transactionRepository.getMonthTotals(financialAccountId, year, month)
        val transferTotals = transactionRepository.getMonthTransferTotals(financialAccountId, year, month)

        val monthClosingBalance = openingBalance + txTotals.totalCredit - txTotals.totalDebit

        val projectedClosingBalance =
            if (txTotals.totalFutureCredit > BigDecimal.ZERO || txTotals.totalFutureDebit > BigDecimal.ZERO) {
                monthClosingBalance + txTotals.totalFutureCredit - txTotals.totalFutureDebit
            } else {
                null
            }

        val expensesByCategory =
            getCategoryBreakdown(accountId, financialAccountId, year, month, CategoryApplicableTo.EXPENSE)
        val incomeByCategory =
            getCategoryBreakdown(accountId, financialAccountId, year, month, CategoryApplicableTo.INCOME)
        val tagsSummary = getTagBreakdown(accountId, financialAccountId, year, month, null)

        val totalIncome = txTotals.totalIncome + txTotals.totalFutureIncome
        val totalExpenses = txTotals.totalExpenses + txTotals.totalFutureExpenses

        return MonthSummary(
            year = year,
            month = month,
            totalIncome = totalIncome,
            totalExpenses = totalExpenses,
            closingBalance = monthClosingBalance,
            openingBalance = openingBalance,
            transfersIn = transferTotals.transfersIn,
            transfersOut = transferTotals.transfersOut,
            projectedClosingBalance = projectedClosingBalance,
            isCurrentMonth = false,
            isFutureMonth = true,
            expensesByCategory = expensesByCategory,
            incomeByCategory = incomeByCategory,
            tagsSummary = tagsSummary,
        )
    }

    private fun resolveSnapshot(
        financialAccountId: UUID,
        accountId: UUID,
        year: Int,
        month: Int,
    ): MonthlyBalanceSnapshot {
        val requestedYearMonth = YearMonth.of(year, month)
        val existing = snapshotRepository.findByYearMonth(financialAccountId, year, month)
        return when {
            existing == null -> {
                val recomputeFrom = resolveRecomputeStart(financialAccountId, requestedYearMonth)
                snapshotComputationService.recomputeChain(
                    financialAccountId,
                    accountId,
                    recomputeFrom.year,
                    recomputeFrom.monthValue,
                )
                snapshotRepository.findByYearMonth(financialAccountId, year, month)
                    ?: error(
                        "Snapshot recompute completed without materializing row for " +
                            "financialAccountId=$financialAccountId year=$year month=$month",
                    )
            }
            existing.isDirty -> {
                val recomputeFrom = resolveRecomputeStart(financialAccountId, requestedYearMonth)
                snapshotComputationService.recomputeChain(
                    financialAccountId,
                    accountId,
                    recomputeFrom.year,
                    recomputeFrom.monthValue,
                )
                snapshotRepository.findByYearMonth(financialAccountId, year, month) ?: existing
            }
            else -> existing
        }
    }

    private fun resolveRecomputeStart(
        financialAccountId: UUID,
        requestedYearMonth: YearMonth,
    ): YearMonth {
        val priorClean =
            snapshotRepository.findLatestCleanBefore(
                financialAccountId,
                requestedYearMonth.year,
                requestedYearMonth.monthValue,
            )
        if (priorClean != null) {
            return YearMonth.of(priorClean.year, priorClean.month).plusMonths(1)
        }

        val earliestTransactionDate = transactionRepository.findEarliestTransactionDate(financialAccountId)
        val earliestYm =
            earliestTransactionDate?.let { YearMonth.of(it.year, it.monthValue) }
                ?: return requestedYearMonth
        // If the user asks for a month before any transaction (e.g. last month while the account
        // only has activity this month), we must still materialize that month. Starting the chain
        // at earliestYm alone would skip the requested month and leave findByYearMonth null.
        return minOf(requestedYearMonth, earliestYm)
    }

    private fun toCategoryTotals(rows: List<CategoryBreakdownRow>): List<CategoryTotal> {
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

    private fun toTagTotals(rows: List<TagBreakdownRow>): List<TagTotal> {
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
}
