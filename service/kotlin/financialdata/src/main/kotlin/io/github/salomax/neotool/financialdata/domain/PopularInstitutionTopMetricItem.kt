package io.github.salomax.neotool.financialdata.domain

import java.math.BigDecimal

/**
 * Domain type for one item in a popular institution's top-metric ranking.
 * Shows where this institution ranks for a given metric and its value.
 */
data class PopularInstitutionTopMetricItem(
    val rank: Int,
    val institution: BacenInstitutionSummary,
    val metricValue: BigDecimal?,
)
