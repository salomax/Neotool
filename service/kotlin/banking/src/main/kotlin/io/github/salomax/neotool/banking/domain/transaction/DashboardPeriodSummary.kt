package io.github.salomax.neotool.banking.domain.transaction

data class DashboardPeriodSummary(
    val totalIncome: Double,
    val totalExpenses: Double,
    val monthCount: Int,
    val avgMonthlyIncome: Double,
    val avgMonthlyExpenses: Double,
    val avgMonthlySavings: Double,
    val savingsRate: Double?,
    val closingCashBalance: Double,
    val closingPortfolioValue: Double,
    val totalNetWorth: Double,
    val totalInvested: Double,
    val avgMonthlyInvested: Double,
    val totalReturn: Double,
    val avgMonthlyReturn: Double,
    val openingPortfolioValue: Double,
    val returnPct: Double?,
    val openingBalance: Double,
) {
    val net: Double get() = totalIncome - totalExpenses
    val closingBalance: Double get() = closingCashBalance
}
