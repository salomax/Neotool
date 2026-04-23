package io.github.salomax.neotool.banking.domain.portfolio

import java.math.BigDecimal

data class PortfolioValueWindow(
    val openingPortfolioValue: BigDecimal,
    val closingPortfolioValue: BigDecimal,
)
