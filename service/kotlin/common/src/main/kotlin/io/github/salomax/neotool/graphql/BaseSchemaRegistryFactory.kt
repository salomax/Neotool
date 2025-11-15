package io.github.salomax.neotool.graphql

import graphql.schema.idl.SchemaParser
import graphql.schema.idl.TypeDefinitionRegistry
import jakarta.inject.Singleton
import java.io.InputStreamReader

/**
 * Base schema registry factory that loads core GraphQL schema.
 * Modules can extend this to add their own types.
 */
abstract class BaseSchemaRegistryFactory {
    
    /**
     * Load the base GraphQL schema from resources
     */
    @Singleton
    open fun typeRegistry(): TypeDefinitionRegistry {
        val baseRegistry = loadBaseSchema()
        val moduleRegistry = loadModuleSchemas()
        
        // Merge all registries
        val mergedRegistry = TypeDefinitionRegistry()
        mergedRegistry.merge(baseRegistry)
        moduleRegistry.forEach { mergedRegistry.merge(it) }
        
        return mergedRegistry
    }
    
    /**
     * Load the core/base schema - override in concrete implementations
     */
    protected open fun loadBaseSchema(): TypeDefinitionRegistry {
        return loadSchemaFromResource("graphql/schema.graphqls")
    }
    
    /**
     * Load additional schemas from modules - override to add module-specific types
     */
    protected open fun loadModuleSchemas(): List<TypeDefinitionRegistry> {
        return emptyList()
    }
    
    /**
     * Utility method to load schema from classpath resource
     */
    protected fun loadSchemaFromResource(resourcePath: String): TypeDefinitionRegistry {
        return javaClass.classLoader.getResourceAsStream(resourcePath)
            ?.use { SchemaParser().parse(InputStreamReader(it)) }
            ?: error("GraphQL schema not found at: $resourcePath")
    }
}
