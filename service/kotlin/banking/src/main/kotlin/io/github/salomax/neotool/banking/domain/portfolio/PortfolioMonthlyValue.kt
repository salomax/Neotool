package io.github.salomax.neotool.banking.domain.portfolio

data class PortfolioMonthlyValue(
    val year: Int,
    val month: Int,
    val grossValue: Double,
    val isEstimated: Boolean,
)
