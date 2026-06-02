package io.github.salomax.neotool.banking.service.transaction

import io.github.salomax.neotool.banking.domain.portfolio.PortfolioDimensionType
import io.github.salomax.neotool.banking.domain.portfolio.PortfolioMonthlyValue
import io.github.salomax.neotool.banking.domain.transaction.CashFlowDataPoint
import io.github.salomax.neotool.banking.domain.transaction.DashboardPeriodSummary
import io.github.salomax.neotool.banking.domain.transaction.MonthlyBalanceSnapshot
import io.github.salomax.neotool.banking.repo.portfolio.PortfolioQuotaRepository
import io.github.salomax.neotool.banking.repo.transaction.MonthlyBalanceSnapshotRepository
import io.github.salomax.neotool.banking.service.investment.InvestmentPortfolioSummaryService
import jakarta.inject.Singleton
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import java.util.UUID

/**
 * Shared dashboard analytics calculator.
 *
 * GraphQL query-specific services delegate here to keep dashboard KPIs and series
 * on a single source of truth while preserving separate query shapes.
 */
@Singleton
open class DashboardAnalyticsCalculator(
    private val snapshotRepository: MonthlyBalanceSnapshotRepository,
    private val portfolioQuotaRepository: PortfolioQuotaRepository,
    private val investmentPortfolioSummaryService: InvestmentPortfolioSummaryService,
) {
    open fun getCashFlowSeries(
        accountId: UUID,
        financialAccountId: UUID?,
        startYear: Int,
        startMonth: Int,
        endYear: Int,
        endMonth: Int,
    ): List<CashFlowDataPoint> {
        val snapshots = findSnapshots(accountId, financialAccountId, startYear, startMonth, endYear, endMonth)

        // When aggregating across accounts, multiple rows share the same (year, month).
        // Fold them into one point per month, summing income and expenses.
        return snapshots
            .groupBy { Pair(it.year, it.month) }
            .entries
            .sortedWith(compareBy({ it.key.first }, { it.key.second }))
            .map { (ym, rows) ->
                CashFlowDataPoint(
                    year = ym.first,
                    month = ym.second,
                    totalIncome = rows.sumOf { it.totalIncome }.toDouble(),
                    totalExpenses = rows.sumOf { it.totalExpenses }.toDouble(),
                    closingBalance = rows.sumOf { it.closingBalance }.toDouble(),
                )
            }
    }

    open fun getPortfolioNetWorthSeries(
        accountId: UUID,
        financialAccountId: UUID?,
        startYear: Int,
        startMonth: Int,
        endYear: Int,
        endMonth: Int,
    ): List<PortfolioMonthlyValue> {
        val from = LocalDate.of(startYear, startMonth, 1)
        val to = LocalDate.of(endYear, endMonth, 1).plusMonths(1).minusDays(1)

        val quotas =
            if (financialAccountId != null) {
                portfolioQuotaRepository.findByAccountAndDateRange(
                    financialAccountId = financialAccountId,
                    dimensionType = PortfolioDimensionType.PORTFOLIO,
                    dimensionKey = null,
                    from = from,
                    to = to,
                )
            } else {
                portfolioQuotaRepository.findByTenantAndDateRange(
                    accountId = accountId,
                    dimensionType = PortfolioDimensionType.PORTFOLIO,
                    dimensionKey = null,
                    from = from,
                    to = to,
                )
            }

        val latestByAccountMonth =
            quotas
                .groupBy { Triple(it.financialAccountId, it.quotaDate.year, it.quotaDate.monthValue) }
                .mapValues { (_, monthRows) -> monthRows.maxBy { it.quotaDate } }
                .values

        return latestByAccountMonth
            .groupBy { Pair(it.quotaDate.year, it.quotaDate.monthValue) }
            .entries
            .sortedWith(compareBy({ it.key.first }, { it.key.second }))
            .map { (ym, rows) ->
                PortfolioMonthlyValue(
                    year = ym.first,
                    month = ym.second,
                    grossValue = rows.sumOf { it.totalPortfolioValue }.toDouble(),
                    isEstimated = rows.any { it.hasEstimatedValues },
                )
            }
    }

    open fun getDashboardPeriodSummary(
        accountId: UUID,
        financialAccountId: UUID?,
        startYear: Int,
        startMonth: Int,
        endYear: Int,
        endMonth: Int,
    ): DashboardPeriodSummary {
        val snapshots = findSnapshots(accountId, financialAccountId, startYear, startMonth, endYear, endMonth)
        val byMonth =
            snapshots
                .groupBy { Pair(it.year, it.month) }
                .entries
                .sortedWith(compareBy({ it.key.first }, { it.key.second }))

        val totalIncome = snapshots.fold(BigDecimal.ZERO) { acc, row -> acc + row.totalIncome }
        val totalExpenses = snapshots.fold(BigDecimal.ZERO) { acc, row -> acc + row.totalExpenses }
        val net = totalIncome - totalExpenses
        val startYearMonth = YearMonth.of(startYear, startMonth)
        val endYearMonth = YearMonth.of(endYear, endMonth)
        val monthCount =
            if (endYearMonth.isBefore(startYearMonth)) {
                0
            } else {
                ChronoUnit.MONTHS.between(startYearMonth, endYearMonth).toInt() + 1
            }

        val openingBalance =
            byMonth.firstOrNull()
                ?.value
                ?.fold(BigDecimal.ZERO) { acc, row -> acc + row.openingBalance }
                ?: BigDecimal.ZERO
        val closingCashBalance =
            byMonth.lastOrNull()
                ?.value
                ?.fold(BigDecimal.ZERO) { acc, row -> acc + row.closingBalance }
                ?: BigDecimal.ZERO

        val portfolioValues =
            portfolioQuotaRepository.findOpeningAndClosingPortfolioValues(
                accountId = accountId,
                financialAccountId = financialAccountId,
                startYear = startYear,
                startMonth = startMonth,
                endYear = endYear,
                endMonth = endMonth,
            )
        val investmentHeadline =
            investmentPortfolioSummaryService.getHeadlineSummary(
                financialAccountId = financialAccountId,
                accountId = accountId,
            )

        val avgMonthlyIncome =
            if (monthCount > 0) totalIncome.toDouble() / monthCount.toDouble() else 0.0
        val avgMonthlyExpenses =
            if (monthCount > 0) totalExpenses.toDouble() / monthCount.toDouble() else 0.0
        val avgMonthlySavings =
            if (monthCount > 0) net.toDouble() / monthCount.toDouble() else 0.0
        val savingsRate =
            if (totalIncome > BigDecimal.ZERO) {
                (net.toDouble() / totalIncome.toDouble()) * 100.0
            } else {
                null
            }

        val totalNetWorth = closingCashBalance + portfolioValues.closingPortfolioValue
        val totalInvested = investmentHeadline.totalContributed
        val totalReturn = investmentHeadline.totalReturn
        val returnPct = investmentHeadline.returnPct
        val avgMonthlyInvested =
            if (monthCount > 0) totalInvested / monthCount.toDouble() else 0.0
        val avgMonthlyReturn =
            if (monthCount > 0) totalReturn / monthCount.toDouble() else 0.0

        return DashboardPeriodSummary(
            totalIncome = totalIncome.toDouble(),
            totalExpenses = totalExpenses.toDouble(),
            monthCount = monthCount,
            avgMonthlyIncome = avgMonthlyIncome,
            avgMonthlyExpenses = avgMonthlyExpenses,
            avgMonthlySavings = avgMonthlySavings,
            savingsRate = savingsRate,
            closingCashBalance = closingCashBalance.toDouble(),
            closingPortfolioValue = portfolioValues.closingPortfolioValue.toDouble(),
            totalNetWorth = totalNetWorth.toDouble(),
            totalInvested = totalInvested,
            avgMonthlyInvested = avgMonthlyInvested,
            totalReturn = totalReturn,
            avgMonthlyReturn = avgMonthlyReturn,
            openingPortfolioValue = portfolioValues.openingPortfolioValue.toDouble(),
            returnPct = returnPct,
            openingBalance = openingBalance.toDouble(),
        )
    }

    private fun findSnapshots(
        accountId: UUID,
        financialAccountId: UUID?,
        startYear: Int,
        startMonth: Int,
        endYear: Int,
        endMonth: Int,
    ): List<MonthlyBalanceSnapshot> =
        if (financialAccountId != null) {
            snapshotRepository.findByRange(financialAccountId, startYear, startMonth, endYear, endMonth)
        } else {
            snapshotRepository.findByRangeForAccount(accountId, startYear, startMonth, endYear, endMonth)
        }
}
