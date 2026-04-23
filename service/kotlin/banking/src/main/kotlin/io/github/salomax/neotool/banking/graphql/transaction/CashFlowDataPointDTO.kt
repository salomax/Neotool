package io.github.salomax.neotool.banking.graphql.transaction

data class CashFlowDataPointDTO(
    val year: Int,
    val month: Int,
    val totalIncome: Double,
    val totalExpenses: Double,
    val net: Double,
    val closingBalance: Double,
)
