package io.github.salomax.neotool.banking.graphql.transaction

data class MonthSummaryDTO(
    val year: Int,
    val month: Int,
    val totalIncome: Double,
    val totalExpenses: Double,
    val closingBalance: Double,
    val openingBalance: Double,
    val transfersIn: Double,
    val transfersOut: Double,
    val projectedClosingBalance: Double?,
    val isCurrentMonth: Boolean,
    val isFutureMonth: Boolean,
    val expensesByCategory: List<CategoryTotalDTO>,
    val incomeByCategory: List<CategoryTotalDTO>,
    val tagsSummary: List<TagTotalDTO>,
)
