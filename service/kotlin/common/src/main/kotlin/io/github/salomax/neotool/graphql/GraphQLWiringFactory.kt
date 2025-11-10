package io.github.salomax.neotool.graphql

import graphql.schema.idl.RuntimeWiring
import graphql.schema.idl.TypeRuntimeWiring
import jakarta.inject.Singleton

/**
 * Abstract base class for building GraphQL runtime wiring with resolvers.
 * 
 * Each service module should extend this class to register its GraphQL resolvers
 * for Query, Mutation, and Subscription operations. This provides a consistent
 * pattern for resolver registration across all services.
 * 
 * **Usage:**
 * ```kotlin
 * @Singleton
 * class AppWiringFactory : GraphQLWiringFactory() {
 *     override fun registerQueryResolvers(type: TypeRuntimeWiring.Builder): TypeRuntimeWiring.Builder {
 *         return type.dataFetcher("products", productsDataFetcher)
 *                    .dataFetcher("product", productDataFetcher)
 *     }
 *     
 *     override fun registerMutationResolvers(type: TypeRuntimeWiring.Builder): TypeRuntimeWiring.Builder {
 *         return type.dataFetcher("createProduct", createProductDataFetcher)
 *     }
 *     
 *     override fun registerSubscriptionResolvers(type: TypeRuntimeWiring.Builder): TypeRuntimeWiring.Builder {
 *         return type // No subscriptions
 *     }
 * }
 * ```
 * 
 * **Key Features:**
 * - Separates Query, Mutation, and Subscription resolver registration
 * - Supports custom type resolvers via `registerCustomTypeResolvers()`
 * - Can contribute to a shared builder for merging (though not needed in true federation)
 * 
 * **Resolver Registration:**
 * - Query resolvers: Field resolvers for read operations
 * - Mutation resolvers: Field resolvers for write operations
 * - Subscription resolvers: Field resolvers for real-time subscriptions
 * - Custom type resolvers: Resolvers for specific GraphQL types (e.g., Customer, Product)
 */
abstract class GraphQLWiringFactory {

  /**
   * Build the complete RuntimeWiring with all resolvers
   */
  fun build(): RuntimeWiring {
    val builder = RuntimeWiring.newRuntimeWiring()
    return contributeToBuilder(builder).build()
  }
  
  /**
   * Contribute this factory's wiring to an existing RuntimeWiring builder.
   * This allows multiple factories to contribute to a single builder for merging.
   */
  fun contributeToBuilder(builder: RuntimeWiring.Builder): RuntimeWiring.Builder {
    builder
      .type("Query") { type -> registerQueryResolvers(type) }
      .type("Mutation") { type -> registerMutationResolvers(type) }
      .type("Subscription") { type -> registerSubscriptionResolvers(type) }
    
    return registerCustomTypeResolvers(builder)
  }

  /**
   * Register all Query resolvers - must be implemented by concrete factories
   */
  protected abstract fun registerQueryResolvers(type: TypeRuntimeWiring.Builder): TypeRuntimeWiring.Builder

  /**
   * Register all Mutation resolvers - must be implemented by concrete factories
   */
  protected abstract fun registerMutationResolvers(type: TypeRuntimeWiring.Builder): TypeRuntimeWiring.Builder

  /**
   * Register all Subscription resolvers - must be implemented by concrete factories
   */
  protected abstract fun registerSubscriptionResolvers(type: TypeRuntimeWiring.Builder): TypeRuntimeWiring.Builder

  /**
   * Register custom type resolvers - can be overridden by concrete factories to register
   * business-specific types (e.g., Customer, Product, etc.)
   * 
   * @param builder The RuntimeWiring builder to add custom type registrations to
   * @return The builder with custom type registrations added
   */
  protected open fun registerCustomTypeResolvers(builder: RuntimeWiring.Builder): RuntimeWiring.Builder {
    return builder
  }
}

/**
 * Registry for managing GraphQL resolvers across modules.
 * 
 * This registry allows modules to register and retrieve resolvers by name.
 * Useful for dynamic resolver lookup and cross-module resolver sharing.
 * 
 * **Note:** In true GraphQL Federation, each service manages its own resolvers
 * independently. This registry is primarily useful for legacy monolithic setups
 * or development scenarios where multiple modules run in the same service.
 * 
 * **Usage:**
 * ```kotlin
 * @Singleton
 * class MyResolverRegistry {
 *     fun registerResolvers(registry: GraphQLResolverRegistry) {
 *         registry.register("productResolver", productResolver)
 *     }
 * }
 * ```
 */
@Singleton
class GraphQLResolverRegistry {

  private val resolvers = mutableMapOf<String, Any>()

  fun <T> register(name: String, resolver: T): T {
    resolvers[name] = resolver as Any
    return resolver
  }

  @Suppress("UNCHECKED_CAST")
  fun <T> get(name: String): T? {
    return resolvers[name] as? T
  }

  fun getAll(): Map<String, Any> = resolvers.toMap()
}
