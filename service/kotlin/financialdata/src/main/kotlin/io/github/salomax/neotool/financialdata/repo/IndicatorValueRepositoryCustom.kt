package io.github.salomax.neotool.financialdata.repo

import io.github.salomax.neotool.common.graphql.pagination.CompositeCursor
import io.github.salomax.neotool.financialdata.domain.EconomicIndicatorPointSyncItem
import java.math.BigDecimal
import java.time.Instant
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

    fun findPointsForSync(
        from: LocalDate,
        to: LocalDate,
        indicators: Collection<String>?,
        variations: Collection<String>?,
        updatedSince: Instant?,
        first: Int,
        after: CompositeCursor?,
    ): List<EconomicIndicatorPointSyncItem>
}
