package io.github.salomax.neotool.security.graphql

import graphql.schema.DataFetchingEnvironment
import graphql.schema.idl.RuntimeWiring
import graphql.schema.idl.TypeRuntimeWiring
import io.github.salomax.neotool.security.graphql.dto.SignInPayloadDTO
import io.github.salomax.neotool.security.graphql.dto.SignUpPayloadDTO
import io.github.salomax.neotool.security.graphql.dto.UserDTO
import io.github.salomax.neotool.common.graphql.GraphQLArgumentUtils.createValidatedDataFetcher
import io.github.salomax.neotool.common.graphql.GraphQLPayloadDataFetcher.createMutationDataFetcher
import io.github.salomax.neotool.common.graphql.GraphQLWiringFactory
import io.github.salomax.neotool.common.graphql.GraphQLResolverRegistry
import jakarta.inject.Singleton

/**
 * Security module wiring factory for GraphQL resolvers
 */
@Singleton
class SecurityWiringFactory(
    private val authResolver: SecurityAuthResolver,
    resolverRegistry: GraphQLResolverRegistry
) : GraphQLWiringFactory() {
    
    init {
        // Register resolvers in the registry for cross-module access
        resolverRegistry.register("auth", authResolver)
    }
    
    override fun registerQueryResolvers(type: TypeRuntimeWiring.Builder): TypeRuntimeWiring.Builder {
        return type
            .dataFetcher("currentUser", createValidatedDataFetcher { env ->
                // Extract token from GraphQL context
                val token = try {
                    env.graphQlContext.get<String?>("token")
                } catch (e: Exception) {
                    null
                }
                authResolver.getCurrentUser(token)
            })
    }
    
    override fun registerMutationResolvers(type: TypeRuntimeWiring.Builder): TypeRuntimeWiring.Builder {
        return type
            .dataFetcher("signIn", createMutationDataFetcher<SignInPayloadDTO>("signIn") { input ->
                authResolver.signIn(input)
            })
            .dataFetcher("signUp", createMutationDataFetcher<SignUpPayloadDTO>("signUp") { input ->
                authResolver.signUp(input)
            })
    }
    
    override fun registerSubscriptionResolvers(type: TypeRuntimeWiring.Builder): TypeRuntimeWiring.Builder {
        return type
    }

    override fun registerCustomTypeResolvers(builder: RuntimeWiring.Builder): RuntimeWiring.Builder {
        return builder
            .type("User") { type ->
                type.dataFetcher("id", createValidatedDataFetcher { env: DataFetchingEnvironment ->
                    val user = env.getSource<UserDTO>()
                    user?.id
                })
                type.dataFetcher("email", createValidatedDataFetcher { env: DataFetchingEnvironment ->
                    val user = env.getSource<UserDTO>()
                    user?.email
                })
                type.dataFetcher("displayName", createValidatedDataFetcher { env: DataFetchingEnvironment ->
                    val user = env.getSource<UserDTO>()
                    user?.displayName
                })
            }
            .type("SignInPayload") { type ->
                type.dataFetcher("token", createValidatedDataFetcher { env: DataFetchingEnvironment ->
                    val payload = env.getSource<SignInPayloadDTO>()
                    payload?.token
                })
                type.dataFetcher("refreshToken", createValidatedDataFetcher { env: DataFetchingEnvironment ->
                    val payload = env.getSource<SignInPayloadDTO>()
                    payload?.refreshToken
                })
                type.dataFetcher("user", createValidatedDataFetcher { env: DataFetchingEnvironment ->
                    val payload = env.getSource<SignInPayloadDTO>()
                    payload?.user
                })
            }
            .type("SignUpPayload") { type ->
                type.dataFetcher("token", createValidatedDataFetcher { env: DataFetchingEnvironment ->
                    val payload = env.getSource<SignUpPayloadDTO>()
                    payload?.token
                })
                type.dataFetcher("refreshToken", createValidatedDataFetcher { env: DataFetchingEnvironment ->
                    val payload = env.getSource<SignUpPayloadDTO>()
                    payload?.refreshToken
                })
                type.dataFetcher("user", createValidatedDataFetcher { env: DataFetchingEnvironment ->
                    val payload = env.getSource<SignUpPayloadDTO>()
                    payload?.user
                })
            }
    }
}

