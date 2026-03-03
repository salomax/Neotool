package io.github.salomax.neotool.financialdata.domain

/**
 * Domain type for the discovery carousel: one popular institution with its summary
 * and its rank/value for each top metric (PROFIT, BASILEIA_INDEX, MOBILIZATION_INDEX).
 */
data class PopularInstitution(
    val rank: Int,
    val shortDescription: String,
    val quarter: String,
    val year: Int?,
    val month: Int?,
    val institution: BacenInstitutionSummary,
    val popularInstitutionTopMetrics: List<PopularInstitutionTopMetric>,
)
