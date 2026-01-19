package io.github.salomax.neotool.assets.graphql

import com.apollographql.federation.graphqljava.Federation
import graphql.analysis.MaxQueryComplexityInstrumentation
import graphql.analysis.MaxQueryDepthInstrumentation
import graphql.schema.idl.TypeDefinitionRegistry
import io.github.salomax.neotool.assets.domain.AssetVisibility
import io.github.salomax.neotool.assets.graphql.dto.AssetDTO
import io.github.salomax.neotool.assets.graphql.mapper.AssetGraphQLMapper
import io.github.salomax.neotool.assets.repository.AssetRepository
import io.micronaut.context.annotation.Factory
import jakarta.inject.Singleton
import mu.KotlinLogging
import java.util.UUID

@Factory
class AssetGraphQLFactory(
    private val registry: TypeDefinitionRegistry,
    private val wiringFactory: AssetWiringFactory,
    private val assetRepository: AssetRepository,
    private val assetMapper: AssetGraphQLMapper,
) {
    private val logger = KotlinLogging.logger {}

    @Singleton
    fun graphQL(): graphql.GraphQL {
        val runtimeWiring = wiringFactory.build()

        // Federation requires fetchEntities and resolveEntityType even if not actively used.
        // Implements batch loading to avoid N+1 queries when resolving multiple Asset references.
        val federatedSchema =
            Federation.transform(registry, runtimeWiring)
                .fetchEntities { env ->
                    val reps = env.getArgument<List<Map<String, Any>>>("representations")
                    if (reps.isNullOrEmpty()) {
                        return@fetchEntities emptyList<AssetDTO?>()
                    }

                    // Extract all Asset IDs for batch loading
                    val assetIds = mutableListOf<UUID>()
                    val assetIdToIndex = mutableMapOf<UUID, Int>()

                    reps.forEachIndexed { index, rep ->
                        if (rep["__typename"] == "Asset") {
                            val id = rep["id"]
                            if (id != null) {
                                try {
                                    val assetId = UUID.fromString(id.toString())
                                    assetIds.add(assetId)
                                    assetIdToIndex[assetId] = index
                                } catch (e: Exception) {
                                    logger.debug("Failed to parse asset ID for federation: {}", id, e)
                                }
                            }
                        }
                    }

                    // Batch load all assets in one query
                    val assetsMap =
                        if (assetIds.isNotEmpty()) {
                            assetRepository.findByIdIn(assetIds)
                                .mapNotNull { entity ->
                                    // For federation, only return PUBLIC assets (no authorization required)
                                    // PRIVATE assets require ownership checks which we can't do in federation context
                                    if (entity.visibility == AssetVisibility.PUBLIC) {
                                        val asset = entity.toDomain()
                                        asset.id?.let { assetId ->
                                            assetId to assetMapper.toAssetDTO(asset)
                                        }
                                    } else {
                                        logger.debug(
                                            "Skipping PRIVATE asset in federation: ${entity.id}",
                                        )
                                        null
                                    }
                                }
                                .toMap()
                        } else {
                            emptyMap()
                        }

                    // Map results back to original representation order
                    reps.mapIndexed { index, rep ->
                        if (rep["__typename"] == "Asset") {
                            val id = rep["id"]
                            if (id != null) {
                                try {
                                    val assetId = UUID.fromString(id.toString())
                                    assetsMap[assetId]
                                } catch (e: Exception) {
                                    logger.debug(
                                        "Failed to fetch entity for federation: ${rep["__typename"]} with id: $id",
                                        e,
                                    )
                                    null
                                }
                            } else {
                                null
                            }
                        } else {
                            null
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
