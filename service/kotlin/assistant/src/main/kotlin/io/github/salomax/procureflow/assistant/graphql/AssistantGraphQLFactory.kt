package io.github.salomax.neotool.assistant.graphql

import graphql.GraphQL
import graphql.schema.idl.SchemaGenerator
import graphql.schema.idl.SchemaParser
import graphql.schema.idl.TypeDefinitionRegistry
import io.micronaut.context.annotation.Factory
import jakarta.inject.Singleton
import org.slf4j.LoggerFactory
import java.io.InputStreamReader

@Factory
class AssistantGraphQLFactory(
    private val wiringFactory: AssistantWiringFactory,
) {
    private val logger = LoggerFactory.getLogger(AssistantGraphQLFactory::class.java)

    @Singleton
    fun graphQL(): GraphQL {
        val registry = loadSchema()
        val runtimeWiring = wiringFactory.build()
        val schema = SchemaGenerator().makeExecutableSchema(registry, runtimeWiring)
        return GraphQL.newGraphQL(schema).build()
    }

    private fun loadSchema(): TypeDefinitionRegistry {
        val schemaResource =
            javaClass.classLoader.getResourceAsStream("graphql/schema.graphqls")
                ?: throw IllegalStateException("GraphQL schema file not found: graphql/schema.graphqls")

        return try {
            InputStreamReader(schemaResource).use { reader ->
                SchemaParser().parse(reader)
            }
        } catch (e: Exception) {
            logger.error("Failed to load GraphQL schema", e)
            throw IllegalStateException("Failed to load GraphQL schema", e)
        }
    }
}
