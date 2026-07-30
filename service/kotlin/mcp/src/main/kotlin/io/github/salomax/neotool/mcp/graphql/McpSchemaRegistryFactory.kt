package io.github.salomax.neotool.mcp.graphql

import graphql.schema.idl.TypeDefinitionRegistry
import io.github.salomax.neotool.common.graphql.BaseSchemaRegistryFactory
import io.micronaut.context.annotation.Factory
import jakarta.inject.Singleton

@Factory
class McpSchemaRegistryFactory : BaseSchemaRegistryFactory() {
    @Singleton
    override fun typeRegistry(): TypeDefinitionRegistry = super.typeRegistry()
}
