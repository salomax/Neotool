package io.github.salomax.neotool.assets.graphql

import graphql.schema.DataFetchingEnvironment
import graphql.schema.idl.RuntimeWiring
import graphql.schema.idl.TypeRuntimeWiring
import io.github.salomax.neotool.assets.domain.AssetResourceType
import io.github.salomax.neotool.assets.domain.AssetStatus
import io.github.salomax.neotool.assets.graphql.dto.AssetDTO
import io.github.salomax.neotool.assets.graphql.dto.ConfirmAssetUploadInput
import io.github.salomax.neotool.assets.graphql.dto.CreateAssetUploadInput
import io.github.salomax.neotool.assets.graphql.mapper.AssetGraphQLMapper
import io.github.salomax.neotool.assets.graphql.resolver.AssetMutationResolver
import io.github.salomax.neotool.assets.graphql.resolver.AssetQueryResolver
import io.github.salomax.neotool.common.graphql.GraphQLArgumentUtils.createValidatedDataFetcher
import io.github.salomax.neotool.common.graphql.GraphQLArgumentUtils.getRequiredString
import io.github.salomax.neotool.common.graphql.GraphQLPayloadDataFetcher.createMutationDataFetcher
import io.github.salomax.neotool.common.graphql.GraphQLResolverRegistry
import io.github.salomax.neotool.common.graphql.GraphQLWiringFactory
import io.github.salomax.neotool.security.service.RequestPrincipalProvider
import jakarta.inject.Singleton
import java.util.UUID

/**
 * Asset module wiring factory for GraphQL resolvers
 */
@Singleton
class AssetWiringFactory(
    private val queryResolver: AssetQueryResolver,
    private val mutationResolver: AssetMutationResolver,
    private val mapper: AssetGraphQLMapper,
    private val requestPrincipalProvider: RequestPrincipalProvider,
    resolverRegistry: GraphQLResolverRegistry,
) : GraphQLWiringFactory() {
    init {
        // Register resolvers in the registry for cross-module access
        resolverRegistry.register("assetQuery", queryResolver)
        resolverRegistry.register("assetMutation", mutationResolver)
    }

    override fun registerQueryResolvers(type: TypeRuntimeWiring.Builder): TypeRuntimeWiring.Builder =
        type
            .dataFetcher(
                "asset",
                createValidatedDataFetcher { env ->
                    val id = getRequiredString(env, "id")
                    val principal = requestPrincipalProvider.fromGraphQl(env)
                    val requesterId = principal.userId.toString()

                    val asset = queryResolver.asset(id, requesterId)
                    asset
                },
            )
            .dataFetcher(
                "assetsByResource",
                createValidatedDataFetcher { env ->
                    val resourceTypeString = getRequiredString(env, "resourceType")
                    val resourceType = AssetResourceType.valueOf(resourceTypeString)
                    val resourceId = getRequiredString(env, "resourceId")

                    queryResolver.assetsByResource(resourceType, resourceId)
                },
            )
            .dataFetcher(
                "assetsByOwner",
                createValidatedDataFetcher { env ->
                    val ownerId = getRequiredString(env, "ownerId")
                    val statusString = env.getArgument<String?>("status")
                    val status = statusString?.let { AssetStatus.valueOf(it) }
                    val limit = env.getArgument<Int?>("limit") ?: 50
                    val offset = env.getArgument<Int?>("offset") ?: 0

                    queryResolver.assetsByOwner(ownerId, status, limit, offset)
                },
            )
            .dataFetcher(
                "assetsByNamespace",
                createValidatedDataFetcher { env ->
                    val namespace = getRequiredString(env, "namespace")
                    val statusString = env.getArgument<String?>("status")
                    val status = statusString?.let { AssetStatus.valueOf(it) }
                    val limit = env.getArgument<Int?>("limit") ?: 50
                    val offset = env.getArgument<Int?>("offset") ?: 0

                    queryResolver.assetsByNamespace(namespace, status, limit, offset)
                },
            )

    override fun registerMutationResolvers(type: TypeRuntimeWiring.Builder): TypeRuntimeWiring.Builder =
        type
            .dataFetcher(
                "createAssetUpload",
                createValidatedDataFetcher { env ->
                    val inputMap = env.getArgument<Map<String, Any?>>("input")
                        ?: throw IllegalArgumentException("input is required")
                    val input = mapper.mapToCreateAssetUploadInput(inputMap)
                    val principal = requestPrincipalProvider.fromGraphQl(env)
                    val userId = principal.userId.toString()

                    mutationResolver.createAssetUpload(input, userId)
                },
            )
            .dataFetcher(
                "confirmAssetUpload",
                createValidatedDataFetcher { env ->
                    val inputMap = env.getArgument<Map<String, Any?>>("input")
                        ?: throw IllegalArgumentException("input is required")
                    val input = mapper.mapToConfirmAssetUploadInput(inputMap)
                    val principal = requestPrincipalProvider.fromGraphQl(env)
                    val userId = principal.userId.toString()

                    mutationResolver.confirmAssetUpload(input, userId)
                },
            )
            .dataFetcher(
                "deleteAsset",
                createValidatedDataFetcher { env ->
                    val assetId = getRequiredString(env, "assetId")
                    val principal = requestPrincipalProvider.fromGraphQl(env)
                    val userId = principal.userId.toString()

                    mutationResolver.deleteAsset(assetId, userId)
                },
            )

    override fun registerSubscriptionResolvers(type: TypeRuntimeWiring.Builder): TypeRuntimeWiring.Builder = type

    override fun registerCustomTypeResolvers(builder: RuntimeWiring.Builder): RuntimeWiring.Builder =
        builder
            .type("Asset") { type ->
                type.dataFetcher(
                    "id",
                    createValidatedDataFetcher { env: DataFetchingEnvironment ->
                        val asset = env.getSource<AssetDTO>()
                        asset?.id?.toString()
                    },
                )
                type.dataFetcher(
                    "ownerId",
                    createValidatedDataFetcher { env: DataFetchingEnvironment ->
                        val asset = env.getSource<AssetDTO>()
                        asset?.ownerId
                    },
                )
                type.dataFetcher(
                    "namespace",
                    createValidatedDataFetcher { env: DataFetchingEnvironment ->
                        val asset = env.getSource<AssetDTO>()
                        asset?.namespace
                    },
                )
                type.dataFetcher(
                    "resourceType",
                    createValidatedDataFetcher { env: DataFetchingEnvironment ->
                        val asset = env.getSource<AssetDTO>()
                        asset?.resourceType?.name
                    },
                )
                type.dataFetcher(
                    "resourceId",
                    createValidatedDataFetcher { env: DataFetchingEnvironment ->
                        val asset = env.getSource<AssetDTO>()
                        asset?.resourceId
                    },
                )
                type.dataFetcher(
                    "storageKey",
                    createValidatedDataFetcher { env: DataFetchingEnvironment ->
                        val asset = env.getSource<AssetDTO>()
                        asset?.storageKey
                    },
                )
                type.dataFetcher(
                    "storageRegion",
                    createValidatedDataFetcher { env: DataFetchingEnvironment ->
                        val asset = env.getSource<AssetDTO>()
                        asset?.storageRegion
                    },
                )
                type.dataFetcher(
                    "storageBucket",
                    createValidatedDataFetcher { env: DataFetchingEnvironment ->
                        val asset = env.getSource<AssetDTO>()
                        asset?.storageBucket
                    },
                )
                type.dataFetcher(
                    "mimeType",
                    createValidatedDataFetcher { env: DataFetchingEnvironment ->
                        val asset = env.getSource<AssetDTO>()
                        asset?.mimeType
                    },
                )
                type.dataFetcher(
                    "sizeBytes",
                    createValidatedDataFetcher { env: DataFetchingEnvironment ->
                        val asset = env.getSource<AssetDTO>()
                        asset?.sizeBytes
                    },
                )
                type.dataFetcher(
                    "checksum",
                    createValidatedDataFetcher { env: DataFetchingEnvironment ->
                        val asset = env.getSource<AssetDTO>()
                        asset?.checksum
                    },
                )
                type.dataFetcher(
                    "originalFilename",
                    createValidatedDataFetcher { env: DataFetchingEnvironment ->
                        val asset = env.getSource<AssetDTO>()
                        asset?.originalFilename
                    },
                )
                type.dataFetcher(
                    "uploadUrl",
                    createValidatedDataFetcher { env: DataFetchingEnvironment ->
                        val asset = env.getSource<AssetDTO>()
                        asset?.uploadUrl
                    },
                )
                type.dataFetcher(
                    "uploadExpiresAt",
                    createValidatedDataFetcher { env: DataFetchingEnvironment ->
                        val asset = env.getSource<AssetDTO>()
                        asset?.uploadExpiresAt
                    },
                )
                type.dataFetcher(
                    "publicUrl",
                    createValidatedDataFetcher { env: DataFetchingEnvironment ->
                        val asset = env.getSource<AssetDTO>()
                        // publicUrl is always generated in DTO, so it should never be null
                        // If it is null, something went wrong in DTO creation
                        if (asset == null) {
                            throw IllegalStateException("AssetDTO source is null")
                        }
                        val publicUrl = asset.publicUrl
                        if (publicUrl.isBlank()) {
                            throw IllegalStateException("publicUrl is blank in AssetDTO (storageKey: ${asset.storageKey})")
                        }
                        publicUrl
                    },
                )
                type.dataFetcher(
                    "status",
                    createValidatedDataFetcher { env: DataFetchingEnvironment ->
                        val asset = env.getSource<AssetDTO>()
                        asset?.status?.name
                    },
                )
                type.dataFetcher(
                    "idempotencyKey",
                    createValidatedDataFetcher { env: DataFetchingEnvironment ->
                        val asset = env.getSource<AssetDTO>()
                        asset?.idempotencyKey
                    },
                )
                type.dataFetcher(
                    "createdAt",
                    createValidatedDataFetcher { env: DataFetchingEnvironment ->
                        val asset = env.getSource<AssetDTO>()
                        asset?.createdAt
                    },
                )
                type.dataFetcher(
                    "updatedAt",
                    createValidatedDataFetcher { env: DataFetchingEnvironment ->
                        val asset = env.getSource<AssetDTO>()
                        asset?.updatedAt
                    },
                )
                type.dataFetcher(
                    "deletedAt",
                    createValidatedDataFetcher { env: DataFetchingEnvironment ->
                        val asset = env.getSource<AssetDTO>()
                        asset?.deletedAt
                    },
                )
            }
}

