package io.github.salomax.neotool.financialdata.repo

import io.github.salomax.neotool.financialdata.domain.TopInstitutionMetric
import java.math.BigDecimal

/**
 * Rank row for one institution and one discovery metric.
 */
data class TopInstitutionMetricRankRow(
    val metric: TopInstitutionMetric,
    val bacenCodInst: String,
    val rank: Int,
    val metricValue: BigDecimal?,
)
