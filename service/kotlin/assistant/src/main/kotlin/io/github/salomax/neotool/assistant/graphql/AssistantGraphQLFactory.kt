package io.github.salomax.neotool.assistant.graphql

import graphql.GraphQL
import graphql.execution.instrumentation.ChainedInstrumentation
import graphql.schema.idl.SchemaGenerator
import graphql.schema.idl.TypeDefinitionRegistry
import io.github.salomax.neotool.common.graphql.GraphQLInstrumentationFactory
import io.micronaut.context.annotation.Factory
import jakarta.inject.Singleton

@Factory
class AssistantGraphQLFactory(
    private val registry: TypeDefinitionRegistry,
    private val wiringFactory: AssistantWiringFactory,
    private val instrumentationFactory: GraphQLInstrumentationFactory,
) {
    @Singleton
    fun graphQL(): GraphQL {
        val runtimeWiring = wiringFactory.build()
        val schema = SchemaGenerator().makeExecutableSchema(registry, runtimeWiring)

        val instrumentation =
            ChainedInstrumentation(
                listOf(
                    instrumentationFactory.queryComplexityInstrumentation(),
                    instrumentationFactory.queryDepthInstrumentation(),
                ),
            )

        return GraphQL
            .newGraphQL(schema)
            .instrumentation(instrumentation)
            .defaultDataFetcherExceptionHandler(AssistantGraphQLExceptionHandler())
            .build()
    }
}
