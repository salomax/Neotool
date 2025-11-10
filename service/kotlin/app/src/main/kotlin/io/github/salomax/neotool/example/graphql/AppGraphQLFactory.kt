package io.github.salomax.neotool.example.graphql

import graphql.GraphQL
import graphql.schema.idl.RuntimeWiring
import graphql.schema.idl.TypeDefinitionRegistry
import io.github.salomax.neotool.example.domain.Customer
import io.github.salomax.neotool.example.domain.Product
import io.github.salomax.neotool.example.service.CustomerService
import io.github.salomax.neotool.example.service.ProductService
import io.github.salomax.neotool.framework.util.toUUID
import io.github.salomax.neotool.graphql.BaseGraphQLFactory
import io.micronaut.context.annotation.Factory
import jakarta.inject.Singleton

/**
 * GraphQL factory for the App service (federated subgraph).
 * 
 * This service owns Product and Customer entities and exposes
 * its own GraphQL endpoint that will be composed by Apollo Router.
 * 
 * This follows true GraphQL Federation patterns where each service
 * has its own GraphQLFactory that handles only its own entities.
 */
@Factory
class AppGraphQLFactory(
    schemaRegistry: TypeDefinitionRegistry,
    wiringFactory: AppWiringFactory,
    private val productService: ProductService,
    private val customerService: CustomerService
) : BaseGraphQLFactory(
    schemaRegistry = schemaRegistry,
    runtimeWiring = wiringFactory.build(),
    serviceName = "App"
) {
    
    @Singleton
    fun graphQL(): GraphQL {
        return buildGraphQL()
    }
    
    /**
     * Fetch an entity by its typename and key fields.
     * 
     * This service handles Product and Customer entities.
     */
    override fun fetchEntity(typename: String, keyFields: Map<String, Any>): Any? {
        val id = extractId(keyFields) ?: return null
        
        return try {
            when (typename) {
                "Product" -> productService.get(toUUID(id))
                "Customer" -> customerService.get(toUUID(id))
                else -> {
                    logger.debug("Unknown entity type for App service: $typename")
                    null
                }
            }
        } catch (e: Exception) {
            logger.debug("Failed to fetch entity for federation: $typename with id: $id", e)
            null
        }
    }
    
    /**
     * Resolve the GraphQL type name for an entity object.
     * 
     * This service handles Product and Customer entities.
     */
    override fun resolveEntityType(entity: Any): String? {
        return when (entity) {
            is Product -> "Product"
            is Customer -> "Customer"
            else -> null
        }
    }
}

