package io.github.salomax.neotool.example.graphql

import graphql.schema.DataFetchingEnvironment
import graphql.schema.idl.RuntimeWiring
import graphql.schema.idl.TypeRuntimeWiring
import io.github.salomax.neotool.common.graphql.GraphQLArgumentUtils.createCrudDataFetcher
import io.github.salomax.neotool.common.graphql.GraphQLArgumentUtils.createValidatedDataFetcher
import io.github.salomax.neotool.common.graphql.GraphQLPayloadDataFetcher.createMutationDataFetcher
import io.github.salomax.neotool.common.graphql.GraphQLPayloadDataFetcher.createUpdateMutationDataFetcher
import io.github.salomax.neotool.common.graphql.GraphQLResolverRegistry
import io.github.salomax.neotool.common.graphql.GraphQLWiringFactory
import io.github.salomax.neotool.example.graphql.resolvers.CustomerResolver
import io.github.salomax.neotool.example.graphql.resolvers.ProductResolver
import jakarta.inject.Singleton

/**
 * Application-specific wiring factory following the standard pattern
 */
@Singleton
class AppWiringFactory(
    private val customerResolver: CustomerResolver,
    private val productResolver: ProductResolver,
    resolverRegistry: GraphQLResolverRegistry,
) : GraphQLWiringFactory() {
    init {
        // Register resolvers in the registry for cross-module access
        resolverRegistry.register("customer", customerResolver)
        resolverRegistry.register("product", productResolver)
    }

    override fun registerQueryResolvers(type: TypeRuntimeWiring.Builder): TypeRuntimeWiring.Builder {
        return type
            .dataFetcher(
                "hello",
                createValidatedDataFetcher { _ ->
                    "Hello from GraphQL!"
                },
            )
            .dataFetcher(
                "currentUser",
                createValidatedDataFetcher { _ ->
                    "user@example.com"
                },
            )
            .dataFetcher(
                "products",
                createValidatedDataFetcher { _ ->
                    productResolver.list()
                },
            )
            .dataFetcher(
                "product",
                createCrudDataFetcher("getProductById") { id ->
                    productResolver.getById(id)
                },
            )
            .dataFetcher(
                "customers",
                createValidatedDataFetcher { _ ->
                    customerResolver.list()
                },
            )
            .dataFetcher(
                "customer",
                createCrudDataFetcher("getCustomerById") { id ->
                    customerResolver.getById(id)
                },
            )
            .dataFetcher(
                "currentUser",
                createValidatedDataFetcher { _ ->
                    // Auth functionality not available in app module (security module dependency removed)
                    null
                },
            )
    }

    override fun registerMutationResolvers(type: TypeRuntimeWiring.Builder): TypeRuntimeWiring.Builder {
        return type
            .dataFetcher(
                "createProduct",
                createMutationDataFetcher("createProduct") { input ->
                    productResolver.create(input)
                },
            )
            .dataFetcher(
                "updateProduct",
                createUpdateMutationDataFetcher("updateProduct") { id, input ->
                    productResolver.update(id, input)
                },
            )
            .dataFetcher(
                "deleteProduct",
                createCrudDataFetcher("deleteProduct") { id ->
                    productResolver.delete(id)
                },
            )
            .dataFetcher(
                "createCustomer",
                createMutationDataFetcher("createCustomer") { input ->
                    customerResolver.create(input)
                },
            )
            .dataFetcher(
                "updateCustomer",
                createUpdateMutationDataFetcher("updateCustomer") { id, input ->
                    customerResolver.update(id, input)
                },
            )
            .dataFetcher(
                "deleteCustomer",
                createCrudDataFetcher("deleteCustomer") { id ->
                    customerResolver.delete(id)
                },
            )
        // SignIn mutation removed - auth functionality not available in app module (security module dependency removed)
    }

    override fun registerSubscriptionResolvers(type: TypeRuntimeWiring.Builder): TypeRuntimeWiring.Builder {
        return type
            .dataFetcher(
                "productUpdated",
                createValidatedDataFetcher { _ ->
                    // TODO: Implement subscription logic
                    null
                },
            )
            .dataFetcher(
                "customerUpdated",
                createValidatedDataFetcher { _ ->
                    // TODO: Implement subscription logic
                    null
                },
            )
    }

    override fun registerCustomTypeResolvers(builder: RuntimeWiring.Builder): RuntimeWiring.Builder {
        return builder
            .type("Customer") { type ->
                type.dataFetcher(
                    "id",
                    createValidatedDataFetcher { env: DataFetchingEnvironment ->
                        val customer = env.getSource<io.github.salomax.neotool.example.domain.Customer>()
                        customer?.id?.toString() ?: throw IllegalStateException("Customer ID is null")
                    },
                )
                type.dataFetcher(
                    "version",
                    createValidatedDataFetcher { env: DataFetchingEnvironment ->
                        val customer = env.getSource<io.github.salomax.neotool.example.domain.Customer>()
                        customer?.version?.toInt() ?: 0
                    },
                )
            }
            .type("Product") { type ->
                type.dataFetcher(
                    "id",
                    createValidatedDataFetcher { env: DataFetchingEnvironment ->
                        val product = env.getSource<io.github.salomax.neotool.example.domain.Product>()
                        product?.id?.toString() ?: throw IllegalStateException("Product ID is null")
                    },
                )
                type.dataFetcher(
                    "version",
                    createValidatedDataFetcher { env: DataFetchingEnvironment ->
                        val product = env.getSource<io.github.salomax.neotool.example.domain.Product>()
                        product?.version?.toInt() ?: 0
                    },
                )
            }
            .type("User") { type ->
                type.dataFetcher(
                    "id",
                    createValidatedDataFetcher { env: DataFetchingEnvironment ->
                        val user = env.getSource<io.github.salomax.neotool.example.graphql.dto.UserDTO>()
                        user?.id
                    },
                )
                type.dataFetcher(
                    "email",
                    createValidatedDataFetcher { env: DataFetchingEnvironment ->
                        val user = env.getSource<io.github.salomax.neotool.example.graphql.dto.UserDTO>()
                        user?.email
                    },
                )
                type.dataFetcher(
                    "displayName",
                    createValidatedDataFetcher { env: DataFetchingEnvironment ->
                        val user = env.getSource<io.github.salomax.neotool.example.graphql.dto.UserDTO>()
                        user?.displayName
                    },
                )
            }
            .type("SignInPayload") { type ->
                type.dataFetcher(
                    "token",
                    createValidatedDataFetcher { env: DataFetchingEnvironment ->
                        val payload = env.getSource<io.github.salomax.neotool.example.graphql.dto.SignInPayloadDTO>()
                        payload?.token
                    },
                )
                type.dataFetcher(
                    "refreshToken",
                    createValidatedDataFetcher { env: DataFetchingEnvironment ->
                        val payload = env.getSource<io.github.salomax.neotool.example.graphql.dto.SignInPayloadDTO>()
                        payload?.refreshToken
                    },
                )
                type.dataFetcher(
                    "user",
                    createValidatedDataFetcher { env: DataFetchingEnvironment ->
                        val payload = env.getSource<io.github.salomax.neotool.example.graphql.dto.SignInPayloadDTO>()
                        payload?.user
                    },
                )
            }
    }
}
