package io.github.salomax.neotool.banking.domain.transaction

data class CashFlowDataPoint(
    val year: Int,
    val month: Int,
    val totalIncome: Double,
    val totalExpenses: Double,
    val closingBalance: Double,
) {
    val net: Double get() = totalIncome - totalExpenses
}
