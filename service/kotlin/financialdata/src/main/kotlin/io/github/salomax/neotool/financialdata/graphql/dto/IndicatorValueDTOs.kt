package io.github.salomax.neotool.financialdata.graphql.dto

import io.micronaut.core.annotation.Introspected
import java.math.BigDecimal
import java.time.LocalDate

@Introspected
data class EconomicIndicatorSeriesDTO(
    val indicator: String,
    val variation: String,
    val unit: String?,
    val values: List<EconomicIndicatorPointDTO>,
)

@Introspected
data class EconomicIndicatorPointDTO(
    val referenceDate: LocalDate,
    val value: BigDecimal,
)

@Introspected
data class EconomicIndicatorLatestDTO(
    val indicator: String,
    val variation: String,
    val unit: String,
    val latest: EconomicIndicatorPointDTO?,
)
