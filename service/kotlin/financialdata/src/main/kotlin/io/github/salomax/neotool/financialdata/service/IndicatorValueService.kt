package io.github.salomax.neotool.financialdata.service

import io.github.salomax.neotool.common.tracing.TraceExecution
import io.github.salomax.neotool.financialdata.graphql.dto.EconomicIndicatorLatestRootDTO
import io.github.salomax.neotool.financialdata.graphql.dto.EconomicIndicatorPointDTO
import io.github.salomax.neotool.financialdata.graphql.dto.EconomicIndicatorSeriesRootDTO
import io.github.salomax.neotool.financialdata.graphql.dto.EconomicIndicatorVariationSeriesDTO
import io.github.salomax.neotool.financialdata.repo.IndicatorValueRepositoryCustom
import io.micronaut.transaction.annotation.Transactional
import jakarta.inject.Singleton
import org.slf4j.LoggerFactory
import java.time.LocalDate

@TraceExecution
@Singleton
open class IndicatorValueService(
    private val repository: IndicatorValueRepositoryCustom,
) {
    private val log = LoggerFactory.getLogger(IndicatorValueService::class.java)

    @Transactional(readOnly = true)
    open fun getSeriesGrouped(
        from: LocalDate,
        to: LocalDate,
        limit: Int?,
    ): Map<Pair<String, String>, EconomicIndicatorVariationSeriesDTO> {
        log.debug(
            "Fetching grouped series for from={}, to={}, limit={}",
            from,
            to,
            limit,
        )

        val results = repository.findSeriesGrouped(null, null, from, to, limit)

        return results.mapValues { (key, data) ->
            val points =
                data.points.map {
                    EconomicIndicatorPointDTO(
                        it.referenceDate,
                        it.value,
                    )
                }
            EconomicIndicatorVariationSeriesDTO(
                unit = data.unit,
                values = points,
            )
        }
    }

    /** Returns series for a single indicator+variation (root query indicatorSeries). */
    @Transactional(readOnly = true)
    open fun getSeries(
        indicator: String,
        variation: String,
        from: LocalDate,
        to: LocalDate,
        limit: Int?,
    ): EconomicIndicatorSeriesRootDTO? {
        validateInputs(indicator, variation)
        validateLimit(limit)
        val results = repository.findSeriesGrouped(indicator, variation, from, to, limit)
        val key = indicator to variation
        val data = results[key] ?: return null
        val points = data.points.map { EconomicIndicatorPointDTO(it.referenceDate, it.value) }
        return EconomicIndicatorSeriesRootDTO(
            indicator = indicator,
            variation = variation,
            unit = data.unit,
            values = points,
        )
    }

    /** Returns latest value for a single indicator+variation (root query indicatorLatest). */
    @Transactional(readOnly = true)
    open fun getLatest(
        indicator: String,
        variation: String,
    ): EconomicIndicatorPointDTO? {
        validateInputs(indicator, variation)
        val now = LocalDate.now()
        val from = now.minusYears(100)
        val series = getSeries(indicator, variation, from, now, 1)
        return series?.values?.lastOrNull()
    }

    /** Returns latest value per indicator+variation (root query indicatorsLatest). Includes variations with no values (latest=null). */
    @Transactional(readOnly = true)
    open fun getAllLatest(
        activeIndicators: Map<
            io.github.salomax.neotool.financialdata.domain.Indicator,
            List<io.github.salomax.neotool.financialdata.domain.IndicatorVariation>,
        >,
    ): List<EconomicIndicatorLatestRootDTO> {
        val now = LocalDate.now()
        val from = now.minusYears(100)
        val resultsWithData = repository.findSeriesGrouped(null, null, from, now, 1)
        return activeIndicators.flatMap { (indicator, variations) ->
            val indicatorLabel = indicator.label
            variations.map { variation ->
                val key = indicatorLabel to variation.label
                val data = resultsWithData[key]
                val point = data?.points?.lastOrNull()
                val unit = data?.unit ?: variation.unit ?: indicator.unit ?: ""
                EconomicIndicatorLatestRootDTO(
                    indicator = indicatorLabel,
                    variation = variation.label,
                    unit = unit,
                    latest = point?.let { EconomicIndicatorPointDTO(it.referenceDate, it.value) },
                )
            }
        }
    }

    private fun validateInputs(
        indicator: String,
        variation: String,
    ) {
        require(indicator.isNotBlank()) { "indicator must not be blank" }
        require(variation.isNotBlank()) { "variation must not be blank" }
        require(indicator.length <= 100) { "indicator must not exceed 100 characters" }
        require(variation.length <= 100) { "variation must not exceed 100 characters" }
    }

    private fun validateLimit(limit: Int?) {
        if (limit == null) return
        require(limit in 1..100) { "limit must be between 1 and 100" }
    }
}
