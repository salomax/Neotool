package io.github.salomax.neotool.graphql

import com.apollographql.federation.graphqljava.Federation
import graphql.analysis.MaxQueryComplexityInstrumentation
import graphql.analysis.MaxQueryDepthInstrumentation
import graphql.schema.GraphQLSchema
import graphql.schema.idl.RuntimeWiring
import graphql.schema.idl.TypeDefinitionRegistry
import io.github.salomax.neotool.exception.GraphQLExceptionHandler
import org.slf4j.LoggerFactory

/**
 * Abstract base class for GraphQL factories in federated services.
 * 
 * This provides common federation logic that all service-specific GraphQL factories share.
 * Each service (app, security, etc.) should extend this class and implement:
 * - Entity fetching logic (fetchEntity)
 * - Entity type resolution (resolveEntityType)
 * 
 * This follows true GraphQL Federation patterns where:
 * - Each service has its own GraphQLFactory
 * - Services run independently with separate GraphQL endpoints
 * - Apollo Router composes the supergraph from independent services
 * 
 * @param schemaRegistry The GraphQL schema registry for this service
 * @param runtimeWiring The runtime wiring with resolvers for this service
 * @param serviceName The name of this service (for logging)
 */
abstract class BaseGraphQLFactory(
    protected val schemaRegistry: TypeDefinitionRegistry,
    protected val runtimeWiring: RuntimeWiring,
    protected val serviceName: String
) {
    
    protected val logger = LoggerFactory.getLogger(this::class.java)
    
    /**
     * Build the GraphQL instance with federation support.
     * 
     * This method:
     * 1. Transforms the schema with Apollo Federation
     * 2. Configures entity fetching and type resolution
     * 3. Adds instrumentation (complexity, depth limits)
     * 4. Configures error handling
     */
    fun buildGraphQL(): graphql.GraphQL {
        logger.info("Building GraphQL schema for $serviceName service (federated subgraph)")
        
        // Create federated schema with entity resolvers
        val federatedSchema = createFederatedSchema()
        
        // Build GraphQL instance with instrumentation
        return graphql.GraphQL.newGraphQL(federatedSchema)
            .instrumentation(MaxQueryComplexityInstrumentation(100))
            .instrumentation(MaxQueryDepthInstrumentation(10))
            .defaultDataFetcherExceptionHandler(GraphQLExceptionHandler())
            .build()
    }
    
    /**
     * Create the federated schema with entity resolvers.
     * 
     * This method handles the Federation.transform() call and configures:
     * - fetchEntities: Called when Federation needs to fetch entities by their key fields
     * - resolveEntityType: Called to resolve a DTO object to its GraphQL type
     */
    protected open fun createFederatedSchema(): GraphQLSchema {
        return Federation.transform(schemaRegistry, runtimeWiring)
            .fetchEntities { env ->
                val reps = env.getArgument<List<Map<String, Any>>>("representations")
                reps?.mapNotNull { rep ->
                    val typename = rep["__typename"]?.toString()
                    
                    if (typename == null) {
                        null
                    } else {
                        try {
                            // Extract all key fields from representation
                            // Federation supports multiple key fields via @key(fields: "...")
                            val keyFields = rep.filterKeys { it != "__typename" }
                            
                            // Delegate to service-specific entity fetching
                            fetchEntity(typename, keyFields)
                        } catch (e: Exception) {
                            logger.debug("Failed to fetch entity for federation: $typename", e)
                            null
                        }
                    }
                }
            }
            .resolveEntityType { env ->
                val entity = env.getObject<Any?>()
                val schema = env.schema
                
                if (schema == null) {
                    throw IllegalStateException("GraphQL schema is null in resolveEntityType")
                }
                
                if (entity == null) {
                    throw IllegalStateException("Entity is null in resolveEntityType")
                }
                
                // Delegate to service-specific type resolution
                val typename = resolveEntityType(entity)
                if (typename == null) {
                    throw IllegalStateException(
                        "Unknown federated type for entity: ${entity.javaClass.name}"
                    )
                }
                
                schema.getObjectType(typename)
                    ?: throw IllegalStateException("Type '$typename' not found in schema")
            }
            .build()
    }
    
    /**
     * Fetch an entity by its typename and key fields.
     * 
     * This method is called by Federation when it needs to fetch entities
     * by their key fields (e.g., when another service references your entities).
     * 
     * @param typename The GraphQL type name (e.g., "Product", "User")
     * @param keyFields Map of key field names to values (e.g., {"id": "123"} or {"id": "123", "sku": "ABC"})
     * @return The entity DTO, or null if the entity doesn't exist or typename is unknown
     */
    protected abstract fun fetchEntity(typename: String, keyFields: Map<String, Any>): Any?
    
    /**
     * Resolve the GraphQL type name for an entity object.
     * 
     * This method is called by Federation to determine the GraphQL type
     * for a DTO object returned from fetchEntity.
     * 
     * @param entity The entity DTO object
     * @return The GraphQL type name (e.g., "Product", "User"), or null if unknown
     */
    protected abstract fun resolveEntityType(entity: Any): String?
    
    /**
     * Helper method to extract ID from key fields.
     * 
     * Most entities use "id" as the primary key, but Federation supports
     * composite keys. This helper extracts "id" if present.
     * 
     * @param keyFields Map of key field names to values
     * @return The ID value as a string, or null if not present
     */
    protected fun extractId(keyFields: Map<String, Any>): String? {
        return keyFields["id"]?.toString()
    }
    
    /**
     * Helper method to extract a specific key field value.
     * 
     * @param keyFields Map of key field names to values
     * @param fieldName The name of the field to extract
     * @return The field value as a string, or null if not present
     */
    protected fun extractKeyField(keyFields: Map<String, Any>, fieldName: String): String? {
        return keyFields[fieldName]?.toString()
    }
}

