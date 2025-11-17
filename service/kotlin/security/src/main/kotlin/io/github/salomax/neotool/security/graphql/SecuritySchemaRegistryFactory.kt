package io.github.salomax.neotool.security.graphql

import graphql.schema.idl.TypeDefinitionRegistry
import io.github.salomax.neotool.common.graphql.BaseSchemaRegistryFactory
import io.micronaut.context.annotation.Factory
import jakarta.inject.Singleton

@Factory
class SecuritySchemaRegistryFactory : BaseSchemaRegistryFactory() {
    
    @Singleton
    override fun typeRegistry(): TypeDefinitionRegistry {
        return super.typeRegistry()
    }
    
    override fun loadBaseSchema(): TypeDefinitionRegistry {
        return loadSchemaFromResource("graphql/schema.graphqls")
    }
}

