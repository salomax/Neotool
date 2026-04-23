package io.github.salomax.neotool.banking.graphql.transaction

data class DashboardPeriodSummaryDTO(
    val totalIncome: Double,
    val totalExpenses: Double,
    val net: Double,
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
    val closingBalance: Double,
    val openingBalance: Double,
)
