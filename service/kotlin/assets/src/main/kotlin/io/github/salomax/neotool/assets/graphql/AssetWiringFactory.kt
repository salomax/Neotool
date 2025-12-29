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
import io.github.salomax.neotool.assets.storage.StorageClient
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
    private val storageClient: StorageClient,
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
                    "visibility",
                    createValidatedDataFetcher { env: DataFetchingEnvironment ->
                        val asset = env.getSource<AssetDTO>()
                        asset?.visibility?.name
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
                        // For PUBLIC assets, publicUrl is generated in DTO
                        // For PRIVATE assets, publicUrl is null (they use downloadUrl instead)
                        asset?.publicUrl
                    },
                )
                type.dataFetcher(
                    "downloadUrl",
                    createValidatedDataFetcher { env: DataFetchingEnvironment ->
                        val asset = env.getSource<AssetDTO>()
                        if (asset == null) {
                            return@createValidatedDataFetcher null
                        }

                        // downloadUrl is only for PRIVATE assets
                        // PUBLIC assets should use publicUrl instead
                        if (asset.visibility.name == "PUBLIC") {
                            return@createValidatedDataFetcher null
                        }

                        // Get TTL parameter (default: 3600 seconds)
                        val ttlSeconds = env.getArgument<Int?>("ttlSeconds") ?: 3600

                        // Generate presigned download URL
                        storageClient.generatePresignedDownloadUrl(
                            asset.storageKey,
                            ttlSeconds.toLong()
                        )
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

