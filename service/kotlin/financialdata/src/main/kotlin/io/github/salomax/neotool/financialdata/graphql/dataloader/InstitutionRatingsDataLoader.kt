package io.github.salomax.neotool.financialdata.graphql.dataloader

import io.github.salomax.neotool.financialdata.graphql.dto.InstitutionRatingDTO
import io.github.salomax.neotool.financialdata.graphql.resolver.FinancialInstitutionRatingResolver
import org.dataloader.DataLoader
import org.dataloader.DataLoaderFactory
import org.dataloader.DataLoaderOptions
import java.util.concurrent.CompletableFuture

/**
 * DataLoader for batch loading institution ratings by Bacen code.
 */
object InstitutionRatingsDataLoader {
    const val KEY = "institutionRatings"

    fun create(ratingResolver: FinancialInstitutionRatingResolver): DataLoader<String, List<InstitutionRatingDTO>> {
        return DataLoaderFactory.newDataLoader<String, List<InstitutionRatingDTO>>(
            { bacenCodes ->
                val batchResult = ratingResolver.resolveRatingsBatch(bacenCodes)
                CompletableFuture.completedFuture(
                    bacenCodes.map { bacenCode -> batchResult[bacenCode] ?: emptyList() },
                )
            },
            DataLoaderOptions.newOptions().setBatchingEnabled(true),
        )
    }
}
