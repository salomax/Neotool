package io.github.salomax.neotool.financialdata.graphql.dataloader

import io.github.salomax.neotool.common.graphql.DataLoaderRegistryFactory
import io.github.salomax.neotool.financialdata.graphql.resolver.FinancialInstitutionRatingResolver
import jakarta.inject.Named
import jakarta.inject.Singleton
import org.dataloader.DataLoaderRegistry

/**
 * Factory for creating DataLoader registries in the financial data module.
 */
@Singleton
@Named("financialdata")
class FinancialDataDataLoaderRegistryFactory(
    private val ratingResolver: FinancialInstitutionRatingResolver,
) : DataLoaderRegistryFactory {
    override fun createDataLoaderRegistry(): DataLoaderRegistry {
        val registry = DataLoaderRegistry()
        registry.register(
            InstitutionRatingsDataLoader.KEY,
            InstitutionRatingsDataLoader.create(ratingResolver),
        )
        registry.register(
            InstitutionRatingHistoryDataLoader.KEY,
            InstitutionRatingHistoryDataLoader.create(ratingResolver),
        )
        return registry
    }
}
