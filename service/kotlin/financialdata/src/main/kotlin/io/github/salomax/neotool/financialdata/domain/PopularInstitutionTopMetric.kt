package io.github.salomax.neotool.financialdata.domain

/**
 * Domain type for one metric's ranking info for a popular institution.
 * Contains the metric type and the single item (rank + value) for this institution.
 */
data class PopularInstitutionTopMetric(
    val metric: TopInstitutionMetric,
    val popularInstitutionTopMetricItem: PopularInstitutionTopMetricItem,
)
