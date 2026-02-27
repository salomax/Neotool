package io.github.salomax.neotool.financialdata.service

import io.github.salomax.neotool.common.tracing.TraceExecution

import io.github.salomax.neotool.financialdata.domain.Indicator
import io.github.salomax.neotool.financialdata.domain.IndicatorVariation
import io.github.salomax.neotool.financialdata.repo.IndicatorReadRepository
import jakarta.inject.Singleton
import jakarta.transaction.Transactional
import org.slf4j.LoggerFactory

@TraceExecution
@Singleton
open class IndicatorService(
    private val indicatorReadRepository: IndicatorReadRepository,
) {
    private val log = LoggerFactory.getLogger(IndicatorService::class.java)

    @Transactional
    open fun getActiveIndicators(): Map<Indicator, List<IndicatorVariation>> {
        log.debug("Fetching active indicators")

        val indicators = indicatorReadRepository.findActiveIndicatorsWithVariations()
        if (indicators.isEmpty()) {
            log.debug("No active indicators found")
            return emptyMap()
        }
        val totalVariations = indicators.values.sumOf { it.size }
        log.debug("Found {} indicators with {} variations", indicators.size, totalVariations)
        return indicators
    }
}
