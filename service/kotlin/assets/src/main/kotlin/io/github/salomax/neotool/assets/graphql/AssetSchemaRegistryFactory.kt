package io.github.salomax.neotool.assets.graphql

import graphql.schema.idl.TypeDefinitionRegistry
import io.github.salomax.neotool.common.graphql.BaseSchemaRegistryFactory
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Replaces
import jakarta.inject.Singleton

@Factory
class AssetSchemaRegistryFactory : BaseSchemaRegistryFactory() {
    @Singleton
    @Replaces(bean = TypeDefinitionRegistry::class, factory = io.github.salomax.neotool.security.graphql.SecuritySchemaRegistryFactory::class)
    override fun typeRegistry(): TypeDefinitionRegistry {
        return super.typeRegistry()
    }

    override fun loadBaseSchema(): TypeDefinitionRegistry {
        // Load the main security schema as the base
        return loadSchemaFromResource("graphql/schema.graphqls")
    }

    override fun loadModuleSchemas(): List<TypeDefinitionRegistry> {
        // Load the asset schema as a module schema to merge with the base
        return listOf(loadSchemaFromResource("graphql/assets.graphqls"))
    }
}


