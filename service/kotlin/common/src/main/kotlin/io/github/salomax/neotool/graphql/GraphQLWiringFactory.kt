package io.github.salomax.neotool.graphql

import graphql.schema.idl.RuntimeWiring
import graphql.schema.idl.TypeRuntimeWiring
import jakarta.inject.Singleton

/**
 * Enhanced wiring factory that ensures consistent resolver registration
 */
abstract class GraphQLWiringFactory {

  /**
   * Build the complete RuntimeWiring with all resolvers
   */
  fun build(): RuntimeWiring {
    return RuntimeWiring.newRuntimeWiring()
      .type("Query") { type -> registerQueryResolvers(type) }
      .type("Mutation") { type -> registerMutationResolvers(type) }
      .type("Subscription") { type -> registerSubscriptionResolvers(type) }
      .type("Customer") { type -> registerCustomerTypeResolvers(type) }
      .type("Product") { type -> registerProductTypeResolvers(type) }
      .build()
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
   * Register Customer type resolvers - can be overridden by concrete factories
   */
  protected open fun registerCustomerTypeResolvers(type: TypeRuntimeWiring.Builder): TypeRuntimeWiring.Builder {
    return type
  }

  /**
   * Register Product type resolvers - can be overridden by concrete factories
   */
  protected open fun registerProductTypeResolvers(type: TypeRuntimeWiring.Builder): TypeRuntimeWiring.Builder {
    return type
  }
}

/**
 * Registry for managing resolvers across modules
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
