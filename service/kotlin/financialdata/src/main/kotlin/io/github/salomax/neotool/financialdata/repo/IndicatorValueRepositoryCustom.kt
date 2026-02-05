package io.github.salomax.neotool.financialdata.repo

import java.math.BigDecimal
import java.time.LocalDate

data class IndicatorValuePoint(
    val referenceDate: LocalDate,
    val value: BigDecimal,
)

data class IndicatorSeriesData(
    val unit: String?,
    val points: List<IndicatorValuePoint>,
)

interface IndicatorValueRepositoryCustom {
    fun findSeriesGrouped(
        indicatorLabel: String?,
        variationLabel: String?,
        from: LocalDate,
        to: LocalDate,
        limit: Int?,
    ): Map<Pair<String, String>, IndicatorSeriesData>
}
