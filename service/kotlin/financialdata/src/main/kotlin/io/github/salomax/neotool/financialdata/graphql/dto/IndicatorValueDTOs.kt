package io.github.salomax.neotool.financialdata.graphql.dto

import io.micronaut.core.annotation.Introspected
import java.math.BigDecimal
import java.time.LocalDate

@Introspected
data class EconomicIndicatorVariationSeriesDTO(
    val unit: String?,
    val values: List<EconomicIndicatorPointDTO>,
)

@Introspected
data class EconomicIndicatorPointDTO(
    val referenceDate: LocalDate,
    val value: BigDecimal,
)

/** Root-level series result for indicatorSeries query. */
@Introspected
data class EconomicIndicatorSeriesRootDTO(
    val indicator: String,
    val variation: String,
    val unit: String?,
    val values: List<EconomicIndicatorPointDTO>,
)

/** Root-level latest result for indicatorsLatest query. */
@Introspected
data class EconomicIndicatorLatestRootDTO(
    val indicator: String,
    val variation: String,
    val unit: String,
    val latest: EconomicIndicatorPointDTO?,
)
