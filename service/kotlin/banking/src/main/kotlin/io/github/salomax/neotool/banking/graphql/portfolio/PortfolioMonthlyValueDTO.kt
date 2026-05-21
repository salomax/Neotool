package io.github.salomax.neotool.banking.graphql.portfolio

data class PortfolioMonthlyValueDTO(
    val year: Int,
    val month: Int,
    val grossValue: Double,
    val isEstimated: Boolean,
)
