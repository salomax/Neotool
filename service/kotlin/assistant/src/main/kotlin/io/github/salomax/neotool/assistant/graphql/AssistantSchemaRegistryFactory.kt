package io.github.salomax.neotool.assistant.graphql

import graphql.schema.idl.TypeDefinitionRegistry
import io.github.salomax.neotool.common.graphql.BaseSchemaRegistryFactory
import io.micronaut.context.annotation.Factory
import jakarta.inject.Singleton

@Factory
class AssistantSchemaRegistryFactory : BaseSchemaRegistryFactory() {
    /**
     * Assistant subgraph schema already defines Query / Mutation / Subscription and JSON,
     * so we skip the shared base schema here to avoid type redefinition errors.
     */
    override fun loadBaseSchema(): TypeDefinitionRegistry = TypeDefinitionRegistry()

    @Singleton
    override fun typeRegistry(): TypeDefinitionRegistry = super.typeRegistry()
}
