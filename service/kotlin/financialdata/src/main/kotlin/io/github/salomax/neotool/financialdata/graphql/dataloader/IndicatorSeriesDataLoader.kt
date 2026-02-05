package io.github.salomax.neotool.financialdata.graphql.dataloader

import io.github.salomax.neotool.financialdata.graphql.dto.EconomicIndicatorVariationSeriesDTO
import io.github.salomax.neotool.financialdata.service.IndicatorValueService
import org.dataloader.DataLoader
import org.dataloader.DataLoaderFactory
import org.dataloader.DataLoaderOptions
import java.time.LocalDate
import java.util.concurrent.CompletableFuture

object IndicatorSeriesDataLoader {
    const val KEY = "indicatorSeries"

    data class Key(
        val indicator: String,
        val variation: String,
        val from: LocalDate,
        val to: LocalDate,
        val limit: Int?,
    )

    fun create(service: IndicatorValueService): DataLoader<Key, EconomicIndicatorVariationSeriesDTO?> {
        return DataLoaderFactory.newDataLoader(
            { keys: List<Key> ->
                val results = mutableMapOf<Key, EconomicIndicatorVariationSeriesDTO?>()

                // Group keys by range parameters to batch requests
                val groupedByParams = keys.groupBy { Triple(it.from, it.to, it.limit) }

                groupedByParams.forEach { (params, groupKeys) ->
                    val (from, to, limit) = params
                    // Fetch all series for this range/limit configuration
                    // This avoids N+1 by fetching once per unique range configuration
                    val allData = service.getSeriesGrouped(from, to, limit)

                    groupKeys.forEach { key ->
                        results[key] = allData[key.indicator to key.variation]
                    }
                }

                CompletableFuture.completedFuture(
                    keys.map { results[it] },
                )
            },
            DataLoaderOptions.newOptions().setBatchingEnabled(true),
        )
    }
}
