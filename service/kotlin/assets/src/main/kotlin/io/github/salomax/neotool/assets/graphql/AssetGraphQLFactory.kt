package io.github.salomax.neotool.assets.graphql

import com.apollographql.federation.graphqljava.Federation
import graphql.analysis.MaxQueryComplexityInstrumentation
import graphql.analysis.MaxQueryDepthInstrumentation
import graphql.schema.idl.TypeDefinitionRegistry
import io.github.salomax.neotool.assets.graphql.dto.AssetDTO
import io.micronaut.context.annotation.Factory
import jakarta.inject.Singleton

@Factory
class AssetGraphQLFactory(
    private val registry: TypeDefinitionRegistry,
    private val wiringFactory: AssetWiringFactory,
) {
    @Singleton
    fun graphQL(): graphql.GraphQL {
        val runtimeWiring = wiringFactory.build()

        // Federation requires fetchEntities and resolveEntityType even if not actively used.
        // NOTE: fetchEntities is currently a placeholder. To implement proper federation support:
        // 1. Inject AssetService or AssetRepository into this factory
        // 2. Extract asset ID from representations and fetch from service
        // 3. Convert domain Asset to AssetDTO using AssetGraphQLMapper
        // 4. Return the DTO for federation to resolve references from other services
        // For now, this returns null which means federation references to Asset will not resolve.
        // This is acceptable if the assets service is not part of a federated supergraph.
        val federatedSchema =
            Federation.transform(registry, runtimeWiring)
                .fetchEntities { env ->
                    val reps = env.getArgument<List<Map<String, Any>>>("representations")
                    reps?.map { rep ->
                        val id = rep["id"]
                        if (id == null) {
                            null
                        } else {
                            try {
                                when (rep["__typename"]) {
                                    "Asset" -> {
                                        // TODO: Implement federation entity fetching
                                        // This requires injecting AssetService/AssetRepository and AssetGraphQLMapper
                                        // Example implementation:
                                        // val assetId = UUID.fromString(id.toString())
                                        // val asset = assetService.getAsset(assetId, null) // No requester for federation
                                        // asset?.let { mapper.toAssetDTO(it) }
                                        null
                                    }
                                    else -> null
                                }
                            } catch (e: Exception) {
                                // Log and return null if ID conversion fails
                                val logger = org.slf4j.LoggerFactory.getLogger(AssetGraphQLFactory::class.java)
                                logger.debug(
                                    "Failed to fetch entity for federation: ${rep["__typename"]} with id: $id",
                                    e,
                                )
                                null
                            }
                        }
                    }
                }
                .resolveEntityType { env ->
                    val entity = env.getObject<Any?>()
                    val schema = env.schema

                    if (schema == null) {
                        throw IllegalStateException("GraphQL schema is null in resolveEntityType")
                    }

                    when (entity) {
                        is AssetDTO ->
                            schema.getObjectType("Asset")
                                ?: throw IllegalStateException("Asset type not found in schema")
                        else -> throw IllegalStateException(
                            "Unknown federated type for entity: ${entity?.javaClass?.name}",
                        )
                    }
                }
                .build()

        return graphql.GraphQL.newGraphQL(federatedSchema)
            .instrumentation(MaxQueryComplexityInstrumentation(100))
            .instrumentation(MaxQueryDepthInstrumentation(10))
            .defaultDataFetcherExceptionHandler(AssetGraphQLExceptionHandler())
            .build()
    }
}
