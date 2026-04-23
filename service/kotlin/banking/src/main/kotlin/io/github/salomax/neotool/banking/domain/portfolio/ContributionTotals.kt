package io.github.salomax.neotool.banking.domain.portfolio

import java.math.BigDecimal

data class ContributionTotals(
    val totalInvested: BigDecimal,
    val totalWithdrawn: BigDecimal,
) {
    val netContributions: BigDecimal
        get() = totalInvested - totalWithdrawn
}
