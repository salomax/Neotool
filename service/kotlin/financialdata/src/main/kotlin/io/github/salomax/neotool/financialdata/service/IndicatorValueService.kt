package io.github.salomax.neotool.financialdata.service

import io.github.salomax.neotool.financialdata.graphql.dto.EconomicIndicatorLatestDTO
import io.github.salomax.neotool.financialdata.graphql.dto.EconomicIndicatorPointDTO
import io.github.salomax.neotool.financialdata.graphql.dto.EconomicIndicatorSeriesDTO
import io.github.salomax.neotool.financialdata.repo.IndicatorValueRepositoryCustom
import io.micronaut.transaction.annotation.Transactional
import jakarta.inject.Singleton
import org.slf4j.LoggerFactory
import java.time.LocalDate

@Singleton
open class IndicatorValueService(
    private val repository: IndicatorValueRepositoryCustom,
) {
    private val log = LoggerFactory.getLogger(IndicatorValueService::class.java)

    @Transactional(readOnly = true)
    open fun getSeries(
        indicator: String,
        variation: String,
        from: LocalDate?,
        to: LocalDate?,
        limit: Int?,
    ): EconomicIndicatorSeriesDTO {
        validateInputs(indicator, variation)
        validateLimit(limit)

        log.debug(
            "Fetching series for indicator={}, variation={}, from={}, to={}, limit={}",
            indicator,
            variation,
            from,
            to,
            limit,
        )

        val metadata =
            repository.findMetadata(indicator, variation)
                ?: throw IllegalArgumentException("Unknown indicator/variation: $indicator/$variation")
        val values = repository.findSeries(indicator, variation, from, to, limit)
        val points =
            values.map {
                EconomicIndicatorPointDTO(
                    it.referenceDate,
                    metadata.valueFormat.normalize(it.value),
                )
            }

        log.debug("Found {} data points for indicator={}, variation={}", points.size, indicator, variation)

        return EconomicIndicatorSeriesDTO(
            indicator = indicator,
            variation = variation,
            unit = metadata.unit,
            values = points,
        )
    }

    @Transactional(readOnly = true)
    open fun getLatest(
        indicator: String,
        variation: String,
    ): EconomicIndicatorPointDTO? {
        validateInputs(indicator, variation)

        log.debug("Fetching latest value for indicator={}, variation={}", indicator, variation)

        val metadata =
            repository.findMetadata(indicator, variation)
                ?: throw IllegalArgumentException("Unknown indicator/variation: $indicator/$variation")
        val latest = repository.findLatest(indicator, variation)

        if (latest == null) {
            log.debug("No data found for indicator={}, variation={}", indicator, variation)
            return null
        }

        log.debug(
            "Found latest value for indicator={}, variation={}, date={}",
            indicator,
            variation,
            latest.referenceDate,
        )
        return EconomicIndicatorPointDTO(
            latest.referenceDate,
            metadata.valueFormat.normalize(latest.value),
        )
    }

    @Transactional(readOnly = true)
    open fun getAllLatest(): List<EconomicIndicatorLatestDTO> {
        log.debug("Fetching all latest indicator values")

        val results = repository.findAllLatest()

        log.debug("Found {} indicator/variation pairs with latest values", results.size)

        return results.map { result ->
            EconomicIndicatorLatestDTO(
                indicator = result.indicator,
                variation = result.variation,
                unit = result.unit,
                latest =
                    if (result.referenceDate != null && result.value != null) {
                        EconomicIndicatorPointDTO(
                            result.referenceDate,
                            result.valueFormat.normalize(result.value),
                        )
                    } else {
                        null
                    },
            )
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
