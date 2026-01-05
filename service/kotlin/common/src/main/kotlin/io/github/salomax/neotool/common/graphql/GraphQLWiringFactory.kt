package io.github.salomax.neotool.common.graphql

import graphql.scalars.ExtendedScalars
import graphql.schema.idl.RuntimeWiring
import graphql.schema.idl.TypeRuntimeWiring
import jakarta.inject.Singleton

/**
 * Base class for GraphQL wiring factories that ensures consistent resolver registration.
 * This class provides the core GraphQL wiring functionality without any authentication/authorization concerns.
 *
 * For wiring factories that require authentication and authorization, use [AuthenticatedGraphQLWiringFactory] instead.
 */
abstract class GraphQLWiringFactory {
    /**
     * Build the complete RuntimeWiring with all resolvers.
     * Uses the Template Method pattern to ensure base scalars are always registered.
     */
    fun build(): RuntimeWiring {
        val builder =
            RuntimeWiring.newRuntimeWiring()
                .type("Query") { type -> registerQueryResolvers(type) }
                .type("Mutation") { type -> registerMutationResolvers(type) }
                .type("Subscription") { type -> registerSubscriptionResolvers(type) }
                // Always register base scalars first (foundation for all modules)
                .scalar(ExtendedScalars.GraphQLLong)
                .scalar(ExtendedScalars.DateTime)
                .scalar(ExtendedScalars.UUID)

        // Then allow subclasses to add their custom type resolvers
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
     * Register all Subscription resolvers - can be overridden by concrete factories if subscriptions are needed.
     * Default implementation is a no-op for modules that don't use subscriptions.
     */
    protected open fun registerSubscriptionResolvers(type: TypeRuntimeWiring.Builder): TypeRuntimeWiring.Builder = type

    /**
     * Register custom type resolvers - can be overridden by concrete factories to register
     * business-specific types (e.g., Customer, Product, Asset, User, etc.)
     *
     * Base scalars (Long, DateTime, UUID) are automatically registered in [build()],
     * so subclasses only need to register their module-specific types.
     *
     * @param builder The RuntimeWiring builder to add custom type registrations to.
     *                Base scalars are already registered at this point.
     * @return The builder with custom type registrations added
     */
    protected open fun registerCustomTypeResolvers(builder: RuntimeWiring.Builder): RuntimeWiring.Builder {
        return builder // Default: no custom types, just return the builder as-is
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
