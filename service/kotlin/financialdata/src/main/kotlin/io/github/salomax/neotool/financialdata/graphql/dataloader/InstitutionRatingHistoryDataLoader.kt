package io.github.salomax.neotool.financialdata.graphql.dataloader

import io.github.salomax.neotool.financialdata.graphql.dto.RatingHistoryDTO
import io.github.salomax.neotool.financialdata.graphql.resolver.FinancialInstitutionRatingResolver
import org.dataloader.DataLoader
import org.dataloader.DataLoaderFactory
import org.dataloader.DataLoaderOptions
import java.util.concurrent.CompletableFuture

/**
 * DataLoader for batch loading institution rating history by Bacen code and limit.
 */
object InstitutionRatingHistoryDataLoader {
    const val KEY = "institutionRatingHistory"

    data class RatingHistoryKey(
        val bacenCodInst: String,
        val limit: Int,
    )

    fun create(
        ratingResolver: FinancialInstitutionRatingResolver,
    ): DataLoader<RatingHistoryKey, List<RatingHistoryDTO>> {
        return DataLoaderFactory.newDataLoader<RatingHistoryKey, List<RatingHistoryDTO>>(
            { keys ->
                val results = mutableMapOf<RatingHistoryKey, List<RatingHistoryDTO>>()
                val groupedByLimit = keys.groupBy { it.limit }
                groupedByLimit.forEach { (limit, groupKeys) ->
                    val bacenCodes = groupKeys.map { it.bacenCodInst }
                    val batchResult =
                        ratingResolver.resolveRatingHistoryBatch(
                            bacenCodes,
                            limit,
                        )
                    groupKeys.forEach { key ->
                        results[key] = batchResult[key.bacenCodInst] ?: emptyList()
                    }
                }
                CompletableFuture.completedFuture(
                    keys.map { key -> results[key] ?: emptyList() },
                )
            },
            DataLoaderOptions.newOptions().setBatchingEnabled(true),
        )
    }
}
