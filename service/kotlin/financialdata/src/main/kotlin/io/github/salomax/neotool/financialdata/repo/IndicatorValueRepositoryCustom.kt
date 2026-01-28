package io.github.salomax.neotool.financialdata.repo

import java.math.BigDecimal
import java.time.LocalDate

data class IndicatorValuePoint(
    val referenceDate: LocalDate,
    val value: BigDecimal,
)

interface IndicatorValueRepositoryCustom {
    fun findSeries(
        indicatorLabel: String,
        variationLabel: String,
        from: LocalDate?,
        to: LocalDate?,
        limit: Int?,
    ): List<IndicatorValuePoint>

    fun findLatest(
        indicatorLabel: String,
        variationLabel: String,
    ): IndicatorValuePoint?

    fun findUnit(
        indicatorLabel: String,
        variationLabel: String,
    ): String?
}
