package io.github.salomax.neotool.security.graphql

import graphql.schema.DataFetchingEnvironment
import graphql.schema.idl.RuntimeWiring
import graphql.schema.idl.TypeRuntimeWiring
import io.github.salomax.neotool.common.graphql.GraphQLArgumentUtils.createValidatedDataFetcher
import io.github.salomax.neotool.common.graphql.GraphQLArgumentUtils.getRequiredString
import io.github.salomax.neotool.common.graphql.GraphQLPayloadDataFetcher.createMutationDataFetcher
import io.github.salomax.neotool.common.graphql.GraphQLResolverRegistry
import io.github.salomax.neotool.common.graphql.GraphQLWiringFactory
import io.github.salomax.neotool.security.graphql.dto.AuthorizationResultDTO
import io.github.salomax.neotool.security.graphql.dto.PermissionDTO
import io.github.salomax.neotool.security.graphql.dto.RequestPasswordResetPayloadDTO
import io.github.salomax.neotool.security.graphql.dto.ResetPasswordPayloadDTO
import io.github.salomax.neotool.security.graphql.dto.RoleDTO
import io.github.salomax.neotool.security.graphql.dto.SignInPayloadDTO
import io.github.salomax.neotool.security.graphql.dto.SignUpPayloadDTO
import io.github.salomax.neotool.security.graphql.dto.UserDTO
import jakarta.inject.Singleton

/**
 * Security module wiring factory for GraphQL resolvers
 */
@Singleton
class SecurityWiringFactory(
    private val authResolver: SecurityAuthResolver,
    private val authorizationResolver: io.github.salomax.neotool.security.graphql.resolver.AuthorizationResolver,
    resolverRegistry: GraphQLResolverRegistry,
) : GraphQLWiringFactory() {
    init {
        // Register resolvers in the registry for cross-module access
        resolverRegistry.register("auth", authResolver)
        resolverRegistry.register("authorization", authorizationResolver)
    }

    override fun registerQueryResolvers(type: TypeRuntimeWiring.Builder): TypeRuntimeWiring.Builder {
        return type
            .dataFetcher(
                "currentUser",
                createValidatedDataFetcher { env ->
                    // Extract token from GraphQL context
                    val token =
                        try {
                            env.graphQlContext.get<String?>("token")
                        } catch (e: Exception) {
                            null
                        }
                    authResolver.getCurrentUser(token)
                },
            )
            .dataFetcher(
                "checkPermission",
                createValidatedDataFetcher { env ->
                    val userId = getRequiredString(env, "userId")
                    val permission = getRequiredString(env, "permission")
                    val resourceId = env.getArgument<String?>("resourceId")
                    authorizationResolver.checkPermission(userId, permission, resourceId)
                },
            )
            .dataFetcher(
                "getUserPermissions",
                createValidatedDataFetcher { env ->
                    val userId = getRequiredString(env, "userId")
                    authorizationResolver.getUserPermissions(userId)
                },
            )
            .dataFetcher(
                "getUserRoles",
                createValidatedDataFetcher { env ->
                    val userId = getRequiredString(env, "userId")
                    authorizationResolver.getUserRoles(userId)
                },
            )
    }

    override fun registerMutationResolvers(type: TypeRuntimeWiring.Builder): TypeRuntimeWiring.Builder {
        return type
            .dataFetcher(
                "signIn",
                createMutationDataFetcher<SignInPayloadDTO>("signIn") { input ->
                    authResolver.signIn(input)
                },
            )
            .dataFetcher(
                "signInWithOAuth",
                createMutationDataFetcher<SignInPayloadDTO>("signInWithOAuth") { input ->
                    authResolver.signInWithOAuth(input)
                },
            )
            .dataFetcher(
                "signUp",
                createMutationDataFetcher<SignUpPayloadDTO>("signUp") { input ->
                    authResolver.signUp(input)
                },
            )
            .dataFetcher(
                "requestPasswordReset",
                createMutationDataFetcher<RequestPasswordResetPayloadDTO>("requestPasswordReset") { input ->
                    authResolver.requestPasswordReset(input)
                },
            )
            .dataFetcher(
                "resetPassword",
                createMutationDataFetcher<ResetPasswordPayloadDTO>("resetPassword") { input ->
                    authResolver.resetPassword(input)
                },
            )
    }

    override fun registerSubscriptionResolvers(type: TypeRuntimeWiring.Builder): TypeRuntimeWiring.Builder {
        return type
    }

    override fun registerCustomTypeResolvers(builder: RuntimeWiring.Builder): RuntimeWiring.Builder {
        return builder
            .type("User") { type ->
                type.dataFetcher(
                    "id",
                    createValidatedDataFetcher { env: DataFetchingEnvironment ->
                        val user = env.getSource<UserDTO>()
                        user?.id
                    },
                )
                type.dataFetcher(
                    "email",
                    createValidatedDataFetcher { env: DataFetchingEnvironment ->
                        val user = env.getSource<UserDTO>()
                        user?.email
                    },
                )
                type.dataFetcher(
                    "displayName",
                    createValidatedDataFetcher { env: DataFetchingEnvironment ->
                        val user = env.getSource<UserDTO>()
                        user?.displayName
                    },
                )
            }
            .type("SignInPayload") { type ->
                type.dataFetcher(
                    "token",
                    createValidatedDataFetcher { env: DataFetchingEnvironment ->
                        val payload = env.getSource<SignInPayloadDTO>()
                        payload?.token
                    },
                )
                type.dataFetcher(
                    "refreshToken",
                    createValidatedDataFetcher { env: DataFetchingEnvironment ->
                        val payload = env.getSource<SignInPayloadDTO>()
                        payload?.refreshToken
                    },
                )
                type.dataFetcher(
                    "user",
                    createValidatedDataFetcher { env: DataFetchingEnvironment ->
                        val payload = env.getSource<SignInPayloadDTO>()
                        payload?.user
                    },
                )
            }
            .type("SignUpPayload") { type ->
                type.dataFetcher(
                    "token",
                    createValidatedDataFetcher { env: DataFetchingEnvironment ->
                        val payload = env.getSource<SignUpPayloadDTO>()
                        payload?.token
                    },
                )
                type.dataFetcher(
                    "refreshToken",
                    createValidatedDataFetcher { env: DataFetchingEnvironment ->
                        val payload = env.getSource<SignUpPayloadDTO>()
                        payload?.refreshToken
                    },
                )
                type.dataFetcher(
                    "user",
                    createValidatedDataFetcher { env: DataFetchingEnvironment ->
                        val payload = env.getSource<SignUpPayloadDTO>()
                        payload?.user
                    },
                )
            }
            .type("RequestPasswordResetPayload") { type ->
                type.dataFetcher(
                    "success",
                    createValidatedDataFetcher { env: DataFetchingEnvironment ->
                        val payload = env.getSource<RequestPasswordResetPayloadDTO>()
                        payload?.success
                    },
                )
                type.dataFetcher(
                    "message",
                    createValidatedDataFetcher { env: DataFetchingEnvironment ->
                        val payload = env.getSource<RequestPasswordResetPayloadDTO>()
                        payload?.message
                    },
                )
            }
            .type("ResetPasswordPayload") { type ->
                type.dataFetcher(
                    "success",
                    createValidatedDataFetcher { env: DataFetchingEnvironment ->
                        val payload = env.getSource<ResetPasswordPayloadDTO>()
                        payload?.success
                    },
                )
                type.dataFetcher(
                    "message",
                    createValidatedDataFetcher { env: DataFetchingEnvironment ->
                        val payload = env.getSource<ResetPasswordPayloadDTO>()
                        payload?.message
                    },
                )
            }
            .type("AuthorizationResult") { type ->
                type.dataFetcher(
                    "allowed",
                    createValidatedDataFetcher { env: DataFetchingEnvironment ->
                        val result = env.getSource<AuthorizationResultDTO>()
                        result?.allowed
                    },
                )
                type.dataFetcher(
                    "reason",
                    createValidatedDataFetcher { env: DataFetchingEnvironment ->
                        val result = env.getSource<AuthorizationResultDTO>()
                        result?.reason
                    },
                )
            }
            .type("Permission") { type ->
                type.dataFetcher(
                    "id",
                    createValidatedDataFetcher { env: DataFetchingEnvironment ->
                        val permission = env.getSource<PermissionDTO>()
                        permission?.id
                    },
                )
                type.dataFetcher(
                    "name",
                    createValidatedDataFetcher { env: DataFetchingEnvironment ->
                        val permission = env.getSource<PermissionDTO>()
                        permission?.name
                    },
                )
            }
            .type("Role") { type ->
                type.dataFetcher(
                    "id",
                    createValidatedDataFetcher { env: DataFetchingEnvironment ->
                        val role = env.getSource<RoleDTO>()
                        role?.id
                    },
                )
                type.dataFetcher(
                    "name",
                    createValidatedDataFetcher { env: DataFetchingEnvironment ->
                        val role = env.getSource<RoleDTO>()
                        role?.name
                    },
                )
            }
    }
}
