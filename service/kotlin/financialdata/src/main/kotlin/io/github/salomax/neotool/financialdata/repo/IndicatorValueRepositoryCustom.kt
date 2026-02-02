package io.github.salomax.neotool.financialdata.repo

import io.github.salomax.neotool.financialdata.model.ValueFormat
import java.math.BigDecimal
import java.time.LocalDate

data class IndicatorValuePoint(
    val referenceDate: LocalDate,
    val value: BigDecimal,
)

data class IndicatorMetadata(
    val unit: String,
    val valueFormat: ValueFormat,
)

data class IndicatorLatestValue(
    val indicator: String,
    val variation: String,
    val unit: String,
    val valueFormat: ValueFormat,
    val referenceDate: LocalDate?,
    val value: BigDecimal?,
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

    fun findMetadata(
        indicatorLabel: String,
        variationLabel: String,
    ): IndicatorMetadata?

    fun findAllLatest(): List<IndicatorLatestValue>
}
