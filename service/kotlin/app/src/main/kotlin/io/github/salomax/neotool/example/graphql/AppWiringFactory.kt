package io.github.salomax.neotool.example.graphql

import graphql.schema.DataFetchingEnvironment
import graphql.schema.idl.RuntimeWiring
import graphql.schema.idl.TypeRuntimeWiring
import io.github.salomax.neotool.example.graphql.resolvers.CustomerResolver
import io.github.salomax.neotool.example.graphql.resolvers.ProductResolver
import io.github.salomax.neotool.example.graphql.auth.AuthResolver
import io.github.salomax.neotool.graphql.GraphQLArgumentUtils.createValidatedDataFetcher
import io.github.salomax.neotool.graphql.GraphQLArgumentUtils.createCrudDataFetcher
import io.github.salomax.neotool.graphql.GraphQLPayloadDataFetcher.createMutationDataFetcher
import io.github.salomax.neotool.graphql.GraphQLPayloadDataFetcher.createUpdateMutationDataFetcher
import io.github.salomax.neotool.graphql.GraphQLPayloadDataFetcher.createCrudDataFetcher as createPayloadCrudDataFetcher
import io.github.salomax.neotool.graphql.GraphQLWiringFactory
import io.github.salomax.neotool.graphql.GraphQLResolverRegistry
import jakarta.inject.Singleton

/**
 * Application-specific wiring factory following the standard pattern
 */
@Singleton
class AppWiringFactory(
    private val customerResolver: CustomerResolver,
    private val productResolver: ProductResolver,
    private val authResolver: AuthResolver,
    resolverRegistry: GraphQLResolverRegistry
) : GraphQLWiringFactory() {
    
    init {
        // Register resolvers in the registry for cross-module access
        resolverRegistry.register("customer", customerResolver)
        resolverRegistry.register("product", productResolver)
        resolverRegistry.register("auth", authResolver)
    }
    
    override fun registerQueryResolvers(type: TypeRuntimeWiring.Builder): TypeRuntimeWiring.Builder {
        return type
            .dataFetcher("hello", createValidatedDataFetcher { _ ->
                "Hello from GraphQL!"
            })
            .dataFetcher("currentUser", createValidatedDataFetcher { _ ->
                "user@example.com"
            })
            .dataFetcher("products", createValidatedDataFetcher { _ ->
                productResolver.list()
            })
            .dataFetcher("product", createCrudDataFetcher("getProductById") { id ->
                productResolver.getById(id)
            })
            .dataFetcher("customers", createValidatedDataFetcher { _ ->
                customerResolver.list()
            })
            .dataFetcher("customer", createCrudDataFetcher("getCustomerById") { id ->
                customerResolver.getById(id)
            })
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
            .dataFetcher("createProduct", createMutationDataFetcher("createProduct") { input ->
                productResolver.create(input)
            })
            .dataFetcher("updateProduct", createUpdateMutationDataFetcher("updateProduct") { id, input ->
                productResolver.update(id, input)
            })
            .dataFetcher("deleteProduct", createCrudDataFetcher("deleteProduct") { id ->
                productResolver.delete(id)
            })
            .dataFetcher("createCustomer", createMutationDataFetcher("createCustomer") { input ->
                customerResolver.create(input)
            })
            .dataFetcher("updateCustomer", createUpdateMutationDataFetcher("updateCustomer") { id, input ->
                customerResolver.update(id, input)
            })
            .dataFetcher("deleteCustomer", createCrudDataFetcher("deleteCustomer") { id ->
                customerResolver.delete(id)
            })
            .dataFetcher("signIn", createMutationDataFetcher<io.github.salomax.neotool.example.graphql.dto.SignInPayloadDTO>("signIn") { input ->
                authResolver.signIn(input)
            })
    }
    
    override fun registerSubscriptionResolvers(type: TypeRuntimeWiring.Builder): TypeRuntimeWiring.Builder {
        return type
            .dataFetcher("productUpdated", createValidatedDataFetcher { _ ->
                // TODO: Implement subscription logic
                null
            })
            .dataFetcher("customerUpdated", createValidatedDataFetcher { _ ->
                // TODO: Implement subscription logic
                null
            })
    }

    override fun registerCustomTypeResolvers(builder: RuntimeWiring.Builder): RuntimeWiring.Builder {
        return builder
            .type("Customer") { type ->
                type.dataFetcher("id", createValidatedDataFetcher { env: DataFetchingEnvironment ->
                    val customer = env.getSource<io.github.salomax.neotool.example.domain.Customer>()
                    customer?.id?.toString() ?: throw IllegalStateException("Customer ID is null")
                })
                type.dataFetcher("version", createValidatedDataFetcher { env: DataFetchingEnvironment ->
                    val customer = env.getSource<io.github.salomax.neotool.example.domain.Customer>()
                    customer?.version?.toInt() ?: 0
                })
            }
            .type("Product") { type ->
                type.dataFetcher("id", createValidatedDataFetcher { env: DataFetchingEnvironment ->
                    val product = env.getSource<io.github.salomax.neotool.example.domain.Product>()
                    product?.id?.toString() ?: throw IllegalStateException("Product ID is null")
                })
                type.dataFetcher("version", createValidatedDataFetcher { env: DataFetchingEnvironment ->
                    val product = env.getSource<io.github.salomax.neotool.example.domain.Product>()
                    product?.version?.toInt() ?: 0
                })
            }
            .type("User") { type ->
                type.dataFetcher("id", createValidatedDataFetcher { env: DataFetchingEnvironment ->
                    val user = env.getSource<io.github.salomax.neotool.example.graphql.dto.UserDTO>()
                    user?.id
                })
                type.dataFetcher("email", createValidatedDataFetcher { env: DataFetchingEnvironment ->
                    val user = env.getSource<io.github.salomax.neotool.example.graphql.dto.UserDTO>()
                    user?.email
                })
                type.dataFetcher("displayName", createValidatedDataFetcher { env: DataFetchingEnvironment ->
                    val user = env.getSource<io.github.salomax.neotool.example.graphql.dto.UserDTO>()
                    user?.displayName
                })
            }
            .type("SignInPayload") { type ->
                type.dataFetcher("token", createValidatedDataFetcher { env: DataFetchingEnvironment ->
                    val payload = env.getSource<io.github.salomax.neotool.example.graphql.dto.SignInPayloadDTO>()
                    payload?.token
                })
                type.dataFetcher("refreshToken", createValidatedDataFetcher { env: DataFetchingEnvironment ->
                    val payload = env.getSource<io.github.salomax.neotool.example.graphql.dto.SignInPayloadDTO>()
                    payload?.refreshToken
                })
                type.dataFetcher("user", createValidatedDataFetcher { env: DataFetchingEnvironment ->
                    val payload = env.getSource<io.github.salomax.neotool.example.graphql.dto.SignInPayloadDTO>()
                    payload?.user
                })
            }
    }
}
