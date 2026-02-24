package io.github.salomax.neotool.financialdata.graphql

import graphql.GraphQL
import graphql.schema.idl.SchemaGenerator
import graphql.schema.idl.TypeDefinitionRegistry
import io.micronaut.context.annotation.Factory
import jakarta.inject.Singleton

@Factory
class FinancialDataGraphQLFactory(
    private val registry: TypeDefinitionRegistry,
    private val wiringFactory: FinancialDataWiringFactory,
) {
    @Singleton
    fun graphQL(): GraphQL {
        val runtimeWiring = wiringFactory.build()
        val schema = SchemaGenerator().makeExecutableSchema(registry, runtimeWiring)
        return GraphQL
            .newGraphQL(schema)
            .defaultDataFetcherExceptionHandler(FinancialDataGraphQLExceptionHandler())
            .build()
    }
}
