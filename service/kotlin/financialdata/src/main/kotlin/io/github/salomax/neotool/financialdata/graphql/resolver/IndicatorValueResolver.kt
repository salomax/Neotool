package io.github.salomax.neotool.financialdata.graphql.resolver

import graphql.schema.DataFetchingEnvironment
import io.github.salomax.neotool.financialdata.graphql.dto.EconomicIndicatorLatestDTO
import io.github.salomax.neotool.financialdata.graphql.dto.EconomicIndicatorPointDTO
import io.github.salomax.neotool.financialdata.graphql.dto.EconomicIndicatorSeriesDTO
import io.github.salomax.neotool.financialdata.service.IndicatorValueService
import jakarta.inject.Singleton
import java.time.LocalDate

@Singleton
class IndicatorValueResolver(
    private val indicatorValueService: IndicatorValueService,
) {
    fun indicatorSeries(env: DataFetchingEnvironment): EconomicIndicatorSeriesDTO {
        val indicator =
            env.getArgument<String>("indicator")
                ?: throw IllegalArgumentException("indicator is required")
        val variation =
            env.getArgument<String>("variation")
                ?: throw IllegalArgumentException("variation is required")
        val range = env.getArgument<Map<String, Any>?>("range")
        val limit = env.getArgument<Int?>("limit")

        val from = parseDate(range?.get("from"))
        val to = parseDate(range?.get("to"))
        val last = (range?.get("last") as? Number)?.toInt()

        val effectiveLimit = last ?: limit
        return indicatorValueService.getSeries(indicator, variation, from, to, effectiveLimit)
    }

    fun indicatorLatest(env: DataFetchingEnvironment): EconomicIndicatorPointDTO? {
        val indicator =
            env.getArgument<String>("indicator")
                ?: throw IllegalArgumentException("indicator is required")
        val variation =
            env.getArgument<String>("variation")
                ?: throw IllegalArgumentException("variation is required")
        return indicatorValueService.getLatest(indicator, variation)
    }

    fun indicatorsLatest(): List<EconomicIndicatorLatestDTO> {
        return indicatorValueService.getAllLatest()
    }

    private fun parseDate(value: Any?): LocalDate? {
        return when (value) {
            is LocalDate -> value
            is String -> runCatching { LocalDate.parse(value) }.getOrNull()
            else -> null
        }
    }
}
