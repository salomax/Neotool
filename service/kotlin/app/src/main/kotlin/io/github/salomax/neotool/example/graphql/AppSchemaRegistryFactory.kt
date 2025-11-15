package io.github.salomax.neotool.example.graphql

import graphql.schema.idl.TypeDefinitionRegistry
import io.github.salomax.neotool.graphql.BaseSchemaRegistryFactory
import io.micronaut.context.annotation.Factory
import jakarta.inject.Singleton

@Factory
class AppSchemaRegistryFactory : BaseSchemaRegistryFactory() {
    
    @Singleton
    override fun typeRegistry(): TypeDefinitionRegistry {
        return super.typeRegistry()
    }
    
    override fun loadBaseSchema(): TypeDefinitionRegistry {
        return loadSchemaFromResource("graphql/schema.graphqls")
    }
}
