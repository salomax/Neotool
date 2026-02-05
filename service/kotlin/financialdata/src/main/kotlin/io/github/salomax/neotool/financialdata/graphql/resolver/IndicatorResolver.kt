package io.github.salomax.neotool.financialdata.graphql.resolver

import graphql.schema.DataFetchingEnvironment
import io.github.salomax.neotool.financialdata.graphql.dto.EconomicIndicatorDTO
import io.github.salomax.neotool.financialdata.graphql.dto.EconomicIndicatorLatestRootDTO
import io.github.salomax.neotool.financialdata.graphql.dto.EconomicIndicatorPointDTO
import io.github.salomax.neotool.financialdata.graphql.dto.EconomicIndicatorSeriesRootDTO
import io.github.salomax.neotool.financialdata.graphql.dto.EconomicIndicatorVariationDTO
import io.github.salomax.neotool.financialdata.service.IndicatorService
import io.github.salomax.neotool.financialdata.service.IndicatorValueService
import jakarta.inject.Singleton
import java.time.LocalDate

@Singleton
class IndicatorResolver(
    private val indicatorService: IndicatorService,
    private val indicatorValueService: IndicatorValueService,
) {
    fun economicIndicators(env: DataFetchingEnvironment): List<EconomicIndicatorDTO> {
        val labelFilter = env.getArgument<String?>("label")
        return indicatorService.getActiveIndicators()
            .filter { (indicator, _) -> labelFilter == null || indicator.label == labelFilter }
            .mapNotNull { (indicator, variations) ->
                val indicatorId = indicator.id ?: return@mapNotNull null
                val variationDTOs =
                    variations.mapNotNull { variation ->
                        val variationId = variation.id ?: return@mapNotNull null
                        EconomicIndicatorVariationDTO(
                            id = variationId,
                            label = variation.label,
                            indicatorLabel = indicator.label,
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

    fun variations(env: DataFetchingEnvironment): List<EconomicIndicatorVariationDTO> {
        val source = env.getSource<EconomicIndicatorDTO>() ?: throw IllegalStateException("Source is null")
        val label = env.getArgument<String?>("label")
        return if (label != null) {
            source.variations.filter { it.label == label }
        } else {
            source.variations
        }
    }

    fun indicatorSeries(env: DataFetchingEnvironment): EconomicIndicatorSeriesRootDTO? {
        val indicator = env.getArgument<String>("indicator") ?: throw IllegalArgumentException("indicator is required")
        val variation = env.getArgument<String>("variation") ?: throw IllegalArgumentException("variation is required")
        val range = env.getArgument<Map<String, Any>?>("range")
        val limit = env.getArgument<Int?>("limit")
        val fromDateStr = range?.get("from")
        val toDateStr = range?.get("to")
        val (from, to) =
            if (fromDateStr == null && toDateStr == null) {
                val now = LocalDate.now()
                now.minusYears(5) to now
            } else {
                val f =
                    if (fromDateStr is String) {
                        LocalDate.parse(fromDateStr.toString())
                    } else {
                        LocalDate.of(1900, 1, 1)
                    }
                val t =
                    if (toDateStr is String) {
                        LocalDate.parse(toDateStr.toString())
                    } else {
                        LocalDate.now()
                    }
                f to t
            }
        return indicatorValueService.getSeries(indicator, variation, from, to, limit)
    }

    fun indicatorLatest(env: DataFetchingEnvironment): EconomicIndicatorPointDTO? {
        val indicator = env.getArgument<String>("indicator") ?: throw IllegalArgumentException("indicator is required")
        val variation = env.getArgument<String>("variation") ?: throw IllegalArgumentException("variation is required")
        val activeIndicators = indicatorService.getActiveIndicators()
        val hasVariation =
            activeIndicators.any { (ind, vars) ->
                ind.label == indicator && vars.any { it.label == variation }
            }
        if (!hasVariation) {
            throw IllegalArgumentException(
                "Unknown indicator or variation: indicator='$indicator', variation='$variation'",
            )
        }
        return indicatorValueService.getLatest(indicator, variation)
    }

    fun indicatorsLatest(env: DataFetchingEnvironment): List<EconomicIndicatorLatestRootDTO> {
        val activeIndicators = indicatorService.getActiveIndicators()
        return indicatorValueService.getAllLatest(activeIndicators)
    }
}
