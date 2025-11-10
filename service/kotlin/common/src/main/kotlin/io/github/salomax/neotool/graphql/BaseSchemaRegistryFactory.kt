package io.github.salomax.neotool.graphql

import graphql.schema.idl.SchemaParser
import graphql.schema.idl.TypeDefinitionRegistry
import jakarta.inject.Singleton
import java.io.InputStreamReader

/**
 * Abstract base class for loading GraphQL schema definitions from resources.
 * 
 * Each service module (app, security, etc.) should extend this class to load
 * its own GraphQL schema file. This follows true GraphQL Federation patterns
 * where each service owns its schema.
 * 
 * **Usage:**
 * ```kotlin
 * @Singleton
 * class AppSchemaRegistryFactory : BaseSchemaRegistryFactory() {
 *     override fun loadBaseSchema(): TypeDefinitionRegistry {
 *         return loadSchemaFromResource("graphql/schema.graphqls")
 *     }
 * }
 * ```
 * 
 * **Key Features:**
 * - Loads schema from classpath resources
 * - Supports merging multiple schema files
 * - Each service loads only its own schema (no cross-service merging)
 * - Schema files should be in `src/main/resources/graphql/` directory
 * 
 * **Schema File Location:**
 * - Default: `graphql/schema.graphqls` (relative to classpath root)
 * - Override `loadBaseSchema()` to specify a different path
 * - Use `loadModuleSchemas()` to load additional schema files
 */
abstract class BaseSchemaRegistryFactory {
    
    /**
     * Load the GraphQL schema registry for this service.
     * 
     * This method loads the base schema and any additional schemas,
     * merging them into a single TypeDefinitionRegistry.
     * 
     * **Important:** Each service should only load its own schema.
     * Schema composition across services is handled by Apollo Router,
     * not by merging schemas in code.
     * 
     * @return The merged TypeDefinitionRegistry for this service
     */
    @Singleton
    open fun typeRegistry(): TypeDefinitionRegistry {
        // Load only the module's own schema
        val moduleSchema = loadBaseSchema()
        val additionalSchemas = loadModuleSchemas()
        
        // Merge additional schemas if any
        if (additionalSchemas.isEmpty()) {
            return moduleSchema
        }
        
        val mergedRegistry = TypeDefinitionRegistry()
        mergedRegistry.merge(moduleSchema)
        additionalSchemas.forEach { mergedRegistry.merge(it) }
        
        return mergedRegistry
    }
    
    /**
     * Load the module's schema - override in concrete implementations
     * Each module should load only its own schema file
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
