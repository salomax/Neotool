package io.github.salomax.neotool.graphql

import jakarta.inject.Singleton

/**
 * Interface for GraphQL modules to register their resolvers
 */
interface GraphQLModule {
    fun registerResolvers(registry: GraphQLResolverRegistry)
    fun getModuleName(): String
}

/**
 * Abstract base class for GraphQL modules
 */
abstract class BaseGraphQLModule : GraphQLModule {
    
    override fun registerResolvers(registry: GraphQLResolverRegistry) {
        // Default implementation - override in concrete modules
    }
    
    override fun getModuleName(): String {
        return this::class.simpleName ?: "UnknownModule"
    }
}

/**
 * Registry for managing GraphQL modules
 */
@Singleton
class GraphQLModuleRegistry {
    
    private val modules = mutableMapOf<String, GraphQLModule>()
    
    fun registerModule(module: GraphQLModule) {
        modules[module.getModuleName()] = module
    }
    
    fun getModule(name: String): GraphQLModule? = modules[name]
    
    fun getAllModules(): Map<String, GraphQLModule> = modules.toMap()
    
    fun initializeAllModules(resolverRegistry: GraphQLResolverRegistry) {
        modules.values.forEach { it.registerResolvers(resolverRegistry) }
    }
}
