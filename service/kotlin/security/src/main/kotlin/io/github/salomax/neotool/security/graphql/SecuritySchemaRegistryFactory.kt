package io.github.salomax.neotool.security.graphql

import graphql.schema.idl.TypeDefinitionRegistry
import io.github.salomax.neotool.graphql.BaseSchemaRegistryFactory
import io.micronaut.context.annotation.Factory
import jakarta.inject.Singleton

/**
 * Factory for creating the Security service's GraphQL schema registry.
 * 
 * Loads the Security service's GraphQL schema from resources.
 * This is a federated subgraph that will be composed by Apollo Router.
 */
@Factory
@Singleton
class SecuritySchemaRegistryFactory : BaseSchemaRegistryFactory() {
    
    @Singleton
    override fun typeRegistry(): TypeDefinitionRegistry {
        return super.typeRegistry()
    }
    
    override fun loadBaseSchema(): TypeDefinitionRegistry {
        return loadSchemaFromResource("graphql/schema.graphqls")
    }
}

