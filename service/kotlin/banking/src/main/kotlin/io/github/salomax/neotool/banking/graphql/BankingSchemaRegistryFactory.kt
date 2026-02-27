package io.github.salomax.neotool.banking.graphql

import graphql.schema.idl.TypeDefinitionRegistry
import io.github.salomax.neotool.common.graphql.BaseSchemaRegistryFactory
import io.micronaut.context.annotation.Factory
import jakarta.inject.Singleton

@Factory
class BankingSchemaRegistryFactory : BaseSchemaRegistryFactory() {
    @Singleton
    override fun typeRegistry(): TypeDefinitionRegistry = super.typeRegistry()
}
