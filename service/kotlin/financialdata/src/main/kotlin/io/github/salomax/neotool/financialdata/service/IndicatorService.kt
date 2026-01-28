package io.github.salomax.neotool.financialdata.service

import io.github.salomax.neotool.financialdata.domain.Indicator
import io.github.salomax.neotool.financialdata.domain.IndicatorVariation
import io.github.salomax.neotool.financialdata.repo.IndicatorRepository
import io.github.salomax.neotool.financialdata.repo.IndicatorVariationRepository
import jakarta.inject.Singleton
import org.slf4j.LoggerFactory

@Singleton
open class IndicatorService(
    private val indicatorRepository: IndicatorRepository,
    private val variationRepository: IndicatorVariationRepository,
) {
    private val log = LoggerFactory.getLogger(IndicatorService::class.java)

    fun getActiveIndicators(): Map<Indicator, List<IndicatorVariation>> {
        log.debug("Fetching active indicators")

        val indicators = indicatorRepository.findByIsActiveTrueOrderByLabelAsc()
        if (indicators.isEmpty()) {
            log.debug("No active indicators found")
            return emptyMap()
        }

        val ids = indicators.mapNotNull { it.id }
        if (ids.isEmpty()) {
            log.warn("Found indicators but all have null IDs")
            return emptyMap()
        }

        val variations = variationRepository.findByIndicatorIdIn(ids)
        log.debug("Found {} indicators with {} variations", indicators.size, variations.size)

        val variationsByIndicator =
            variations.groupBy { it.indicatorId }.mapValues { entry ->
                entry.value.map { it.toDomain() }
            }

        return indicators
            .mapNotNull { indicator ->
                val id = indicator.id ?: return@mapNotNull null
                indicator.toDomain() to variationsByIndicator[id].orEmpty()
            }
            .toMap()
    }
}
