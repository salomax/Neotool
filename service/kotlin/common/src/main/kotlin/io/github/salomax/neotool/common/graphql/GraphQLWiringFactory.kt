package io.github.salomax.neotool.common.graphql

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
        val builder =
            RuntimeWiring.newRuntimeWiring()
                .type("Query") { type -> registerQueryResolvers(type) }
                .type("Mutation") { type -> registerMutationResolvers(type) }
                .type("Subscription") { type -> registerSubscriptionResolvers(type) }

        return registerCustomTypeResolvers(builder).build()
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
 * Registry for managing resolvers across modules
 */
@Singleton
class GraphQLResolverRegistry {
    private val resolvers = mutableMapOf<String, Any>()

    fun <T> register(
        name: String,
        resolver: T,
    ): T {
        resolvers[name] = resolver as Any
        return resolver
    }

    @Suppress("UNCHECKED_CAST")
    fun <T> get(name: String): T? {
        return resolvers[name] as? T
    }

    fun getAll(): Map<String, Any> = resolvers.toMap()
}
