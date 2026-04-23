package io.github.salomax.neotool.banking.service.transaction

import io.github.salomax.neotool.banking.domain.portfolio.PortfolioMonthlyValue
import io.github.salomax.neotool.banking.domain.transaction.CashFlowDataPoint
import io.github.salomax.neotool.banking.domain.transaction.CategoryApplicableTo
import io.github.salomax.neotool.banking.domain.transaction.CategoryTotal
import io.github.salomax.neotool.banking.domain.transaction.DashboardPeriodSummary
import io.github.salomax.neotool.banking.repo.transaction.TransactionRepository
import io.micronaut.transaction.annotation.Transactional
import jakarta.inject.Singleton
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.UUID

/**
 * Provides aggregated analytics queries for the personal-finance dashboard.
 *
 * Query-layer facade for dashboard analytics.
 *
 * KPI and series computations live in [DashboardAnalyticsCalculator] so dashboard and
 * investments can share the same source of truth.
 */
@Singleton
open class DashboardAnalyticsService(
    private val transactionRepository: TransactionRepository,
    private val dashboardAnalyticsCalculator: DashboardAnalyticsCalculator,
) {
    /**
     * Returns a monthly cash-flow series for the given range.
     *
     * One [CashFlowDataPoint] per snapshot row found.  Months with no snapshot
     * (e.g. account didn't exist yet) are silently omitted — the frontend
     * renders whatever points come back.
     *
     * The aggregate (all-accounts) path sums snapshots for the same (year, month)
     * across all financial accounts belonging to [accountId].
     */
    @Transactional(readOnly = true)
    open fun getCashFlowSeries(
        accountId: UUID,
        financialAccountId: UUID?,
        startYear: Int,
        startMonth: Int,
        endYear: Int,
        endMonth: Int,
    ): List<CashFlowDataPoint> =
        dashboardAnalyticsCalculator.getCashFlowSeries(
            accountId = accountId,
            financialAccountId = financialAccountId,
            startYear = startYear,
            startMonth = startMonth,
            endYear = endYear,
            endMonth = endMonth,
        )

    /**
     * Returns monthly portfolio gross-value snapshots for the requested range.
     *
     * For each account and month we keep the latest available quota_date, then aggregate by month.
     * `grossValue` comes from `investment_portfolio_quota.total_portfolio_value`, which tracks holdings
     * market value (cash ledger is excluded from this quota series).
     */
    @Transactional(readOnly = true)
    open fun getPortfolioNetWorthSeries(
        accountId: UUID,
        financialAccountId: UUID?,
        startYear: Int,
        startMonth: Int,
        endYear: Int,
        endMonth: Int,
    ): List<PortfolioMonthlyValue> =
        dashboardAnalyticsCalculator.getPortfolioNetWorthSeries(
            accountId = accountId,
            financialAccountId = financialAccountId,
            startYear = startYear,
            startMonth = startMonth,
            endYear = endYear,
            endMonth = endMonth,
        )

    /**
     * Returns aggregate income/expenses/balance summary for the date range.
     *
     * Executes 3 fixed queries:
     * 1) monthly balance snapshots (cash flow + cash balances + month count)
     * 2) opening/closing portfolio values
     * 3) all-time investment headline summary (same source of truth as investments summary screen)
     */
    @Transactional(readOnly = true)
    open fun getDashboardPeriodSummary(
        accountId: UUID,
        financialAccountId: UUID?,
        startYear: Int,
        startMonth: Int,
        endYear: Int,
        endMonth: Int,
    ): DashboardPeriodSummary =
        dashboardAnalyticsCalculator.getDashboardPeriodSummary(
            accountId = accountId,
            financialAccountId = financialAccountId,
            startYear = startYear,
            startMonth = startMonth,
            endYear = endYear,
            endMonth = endMonth,
        )

    /**
     * Returns category breakdown aggregated across the full date range (single DB query).
     *
     * Delegates to [TransactionRepository.getCategoryBreakdownForRange] which issues one
     * GROUP BY query across all months in [startYear/startMonth, endYear/endMonth].
     */
    @Transactional(readOnly = true)
    open fun getCategoryBreakdownForRange(
        accountId: UUID,
        financialAccountId: UUID?,
        startYear: Int,
        startMonth: Int,
        endYear: Int,
        endMonth: Int,
        applicableTo: CategoryApplicableTo?,
    ): List<CategoryTotal> {
        val rows = transactionRepository.getCategoryBreakdownForRange(
            accountId = accountId,
            financialAccountId = financialAccountId,
            startYear = startYear,
            startMonth = startMonth,
            endYear = endYear,
            endMonth = endMonth,
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
}
