package io.github.salomax.neotool.mcp.graphql

import com.apollographql.federation.graphqljava.Federation
import graphql.analysis.MaxQueryComplexityInstrumentation
import graphql.analysis.MaxQueryDepthInstrumentation
import graphql.schema.idl.TypeDefinitionRegistry
import io.micronaut.context.annotation.Factory
import jakarta.inject.Singleton

@Factory
class McpGraphQLFactory(
    private val registry: TypeDefinitionRegistry,
    private val wiringFactory: McpWiringFactory,
) {
    @Singleton
    fun graphQL(): graphql.GraphQL {
        val runtimeWiring = wiringFactory.build()
        val federatedSchema =
            Federation
                .transform(registry, runtimeWiring)
                .fetchEntities { emptyList<Any>() }
                .resolveEntityType { null }
                .build()

        return graphql.GraphQL
            .newGraphQL(federatedSchema)
            .instrumentation(MaxQueryComplexityInstrumentation(50))
            .instrumentation(MaxQueryDepthInstrumentation(8))
            .defaultDataFetcherExceptionHandler(McpGraphQLExceptionHandler())
            .build()
    }
}
