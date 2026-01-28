package io.github.salomax.neotool.financialdata.graphql.resolver

import graphql.schema.DataFetchingEnvironment
import io.github.salomax.neotool.financialdata.graphql.dto.EconomicIndicatorDTO
import io.github.salomax.neotool.financialdata.graphql.dto.EconomicIndicatorVariationDTO
import io.github.salomax.neotool.financialdata.service.IndicatorService
import jakarta.inject.Singleton

@Singleton
class IndicatorResolver(
    private val indicatorService: IndicatorService,
) {
    fun economicIndicators(env: DataFetchingEnvironment): List<EconomicIndicatorDTO> {
        return indicatorService.getActiveIndicators().mapNotNull { (indicator, variations) ->
            val indicatorId = indicator.id ?: return@mapNotNull null
            val variationDTOs =
                variations.mapNotNull { variation ->
                    val variationId = variation.id ?: return@mapNotNull null
                    EconomicIndicatorVariationDTO(
                        id = variationId,
                        label = variation.label,
                        unit = variation.unit,
                        isDefault = variation.isDefault,
                        incrementalMonths = variation.incrementalMonths,
                        incrementalYears = variation.incrementalYears,
                        incrementalDays = variation.incrementalDays,
                    )
                }
            EconomicIndicatorDTO(
                id = indicatorId,
                countryCode = indicator.countryCode,
                label = indicator.label,
                issuer = indicator.issuer,
                description = indicator.description,
                frequency = indicator.frequency,
                unit = indicator.unit,
                isActive = indicator.isActive,
                createdAt = indicator.createdAt,
                updatedAt = indicator.updatedAt,
                variations = variationDTOs,
            )
        }
    }
}
