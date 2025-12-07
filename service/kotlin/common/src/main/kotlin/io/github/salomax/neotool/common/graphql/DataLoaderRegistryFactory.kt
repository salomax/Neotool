package io.github.salomax.neotool.common.graphql

import org.dataloader.DataLoaderRegistry

/**
 * Factory interface for creating DataLoader registries.
 * Allows modules to register their DataLoaders without coupling GraphQLControllerBase to specific modules.
 */
interface DataLoaderRegistryFactory {
    /**
     * Creates a DataLoaderRegistry with all DataLoaders registered by this factory.
     * This is called per-request to create a fresh registry for each GraphQL execution.
     */
    fun createDataLoaderRegistry(): DataLoaderRegistry
}
