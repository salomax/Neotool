package io.github.salomax.neotool.financialdata.graphql.dto

import io.github.salomax.neotool.financialdata.domain.TopInstitutionMetric
import io.micronaut.core.annotation.Introspected
import io.micronaut.serde.annotation.Serdeable

@Introspected
@Serdeable
data class PopularInstitutionDTO(
    val rank: Int,
    val shortDescription: String,
    val quarter: String,
    val year: Int?,
    val month: Int?,
    val institution: BacenInstitutionSummaryItemDTO,
    val popularInstitutionTopMetrics: List<PopularInstitutionTopMetricDTO>,
)

@Introspected
@Serdeable
data class PopularInstitutionTopMetricDTO(
    val metric: TopInstitutionMetric,
    val popularInstitutionTopMetricItem: PopularInstitutionTopMetricItemDTO,
)

@Introspected
@Serdeable
data class PopularInstitutionTopMetricItemDTO(
    val rank: Int,
    val institution: BacenInstitutionSummaryItemDTO,
    val metricValue: Double?,
)
