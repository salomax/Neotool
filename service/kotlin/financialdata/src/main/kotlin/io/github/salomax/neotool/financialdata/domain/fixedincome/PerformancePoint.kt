package io.github.salomax.neotool.financialdata.domain.fixedincome

import java.math.BigDecimal
import java.time.LocalDate

/**
 * One point on a performance series for the chart (date, net value).
 */
data class PerformancePoint(
    val date: LocalDate,
    val netValue: BigDecimal,
)
