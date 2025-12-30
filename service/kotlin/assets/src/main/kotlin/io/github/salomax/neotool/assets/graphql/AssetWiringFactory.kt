package io.github.salomax.neotool.assets.graphql

import graphql.schema.DataFetchingEnvironment
import graphql.schema.idl.RuntimeWiring
import graphql.schema.idl.TypeRuntimeWiring
import io.github.salomax.neotool.assets.domain.rbac.AssetPermissions
import io.github.salomax.neotool.assets.graphql.dto.AssetDTO
import io.github.salomax.neotool.assets.graphql.mapper.AssetGraphQLMapper
import io.github.salomax.neotool.assets.graphql.resolver.AssetMutationResolver
import io.github.salomax.neotool.assets.graphql.resolver.AssetQueryResolver
import io.github.salomax.neotool.assets.storage.StorageClient
import io.github.salomax.neotool.common.graphql.AuthenticatedGraphQLWiringFactory
import io.github.salomax.neotool.common.graphql.GraphQLArgumentUtils.createValidatedDataFetcher
import io.github.salomax.neotool.common.graphql.GraphQLArgumentUtils.getRequiredString
import io.github.salomax.neotool.common.graphql.GraphQLResolverRegistry
import io.github.salomax.neotool.security.service.AuthorizationChecker
import io.github.salomax.neotool.security.service.RequestPrincipalProvider
import jakarta.inject.Singleton
import java.time.ZoneOffset
import java.util.UUID

/**
 * Asset module wiring factory for GraphQL resolvers with authentication and authorization.
 */
@Singleton
class AssetWiringFactory(
    private val queryResolver: AssetQueryResolver,
    private val mutationResolver: AssetMutationResolver,
    private val mapper: AssetGraphQLMapper,
    requestPrincipalProvider: RequestPrincipalProvider,
    authorizationChecker: AuthorizationChecker,
    private val storageClient: StorageClient,
    resolverRegistry: GraphQLResolverRegistry,
) : AuthenticatedGraphQLWiringFactory(requestPrincipalProvider, authorizationChecker) {
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
                    env.withPermission(AssetPermissions.ASSETS_ASSET_VIEW) { principal ->
                        val id = getRequiredString(env, "id")
                        val requesterId = principal.userId.toString()
                        queryResolver.asset(id, requesterId)
                    }
                },
            )

    override fun registerMutationResolvers(type: TypeRuntimeWiring.Builder): TypeRuntimeWiring.Builder =
        type
            .dataFetcher(
                "createAssetUpload",
                createValidatedDataFetcher { env ->
                    env.withPermission(AssetPermissions.ASSETS_ASSET_UPLOAD) { principal ->
                        val inputMap =
                            env.getArgument<Map<String, Any?>>("input")
                                ?: throw IllegalArgumentException("input is required")
                        val input = mapper.mapToCreateAssetUploadInput(inputMap)
                        val userId = principal.userId.toString()
                        mutationResolver.createAssetUpload(input, userId)
                    }
                },
            )
            .dataFetcher(
                "confirmAssetUpload",
                createValidatedDataFetcher { env ->
                    env.withPermission(AssetPermissions.ASSETS_ASSET_UPLOAD) { principal ->
                        val inputMap =
                            env.getArgument<Map<String, Any?>>("input")
                                ?: throw IllegalArgumentException("input is required")
                        val input = mapper.mapToConfirmAssetUploadInput(inputMap)
                        val userId = principal.userId.toString()
                        mutationResolver.confirmAssetUpload(input, userId)
                    }
                },
            )
            .dataFetcher(
                "deleteAsset",
                createValidatedDataFetcher { env ->
                    env.withPermission(AssetPermissions.ASSETS_ASSET_DELETE) { principal ->
                        val assetId = getRequiredString(env, "assetId")
                        val userId = principal.userId.toString()
                        mutationResolver.deleteAsset(assetId, userId)
                    }
                },
            )

    override fun registerSubscriptionResolvers(type: TypeRuntimeWiring.Builder): TypeRuntimeWiring.Builder = type

    override fun registerCustomTypeResolvers(builder: RuntimeWiring.Builder): RuntimeWiring.Builder =
        builder
            // Register Asset type resolvers
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
                        // Convert Instant to OffsetDateTime for GraphQL DateTime scalar
                        asset?.uploadExpiresAt?.atOffset(ZoneOffset.UTC)
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
                            ttlSeconds.toLong(),
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
                        // Convert Instant to OffsetDateTime for GraphQL DateTime scalar
                        asset?.createdAt?.atOffset(ZoneOffset.UTC)
                    },
                )
                type.dataFetcher(
                    "updatedAt",
                    createValidatedDataFetcher { env: DataFetchingEnvironment ->
                        val asset = env.getSource<AssetDTO>()
                        // Convert Instant to OffsetDateTime for GraphQL DateTime scalar
                        asset?.updatedAt?.atOffset(ZoneOffset.UTC)
                    },
                )
                type.dataFetcher(
                    "deletedAt",
                    createValidatedDataFetcher { env: DataFetchingEnvironment ->
                        val asset = env.getSource<AssetDTO>()
                        // Convert Instant to OffsetDateTime for GraphQL DateTime scalar
                        asset?.deletedAt?.atOffset(ZoneOffset.UTC)
                    },
                )
            }
}
