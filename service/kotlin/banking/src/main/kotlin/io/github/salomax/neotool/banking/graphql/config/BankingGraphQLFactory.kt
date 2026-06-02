package io.github.salomax.neotool.banking.graphql.config

import graphql.GraphQL
import graphql.schema.idl.SchemaGenerator
import graphql.schema.idl.TypeDefinitionRegistry
import io.micronaut.context.annotation.Factory
import jakarta.inject.Singleton

/**
 * Produces the [GraphQL] instance for the banking subgraph.
 * Schema is loaded from resources/graphql/schema.graphqls by Micronaut's [TypeDefinitionRegistry].
 */
@Factory
class BankingGraphQLFactory(
    private val registry: TypeDefinitionRegistry,
    private val wiringFactory: BankingWiringFactory,
) {
    @Singleton
    fun graphQL(): GraphQL {
        val runtimeWiring = wiringFactory.build()
        val schema = SchemaGenerator().makeExecutableSchema(registry, runtimeWiring)
        return GraphQL
            .newGraphQL(schema)
            .defaultDataFetcherExceptionHandler(BankingGraphQLExceptionHandler())
            .build()
    }
}
