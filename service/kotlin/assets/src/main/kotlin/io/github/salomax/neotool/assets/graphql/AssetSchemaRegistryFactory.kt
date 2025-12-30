package io.github.salomax.neotool.assets.graphql

import graphql.schema.idl.SchemaParser
import graphql.schema.idl.TypeDefinitionRegistry
import io.github.salomax.neotool.common.graphql.BaseSchemaRegistryFactory
import io.micronaut.context.annotation.Factory
import jakarta.inject.Singleton
import java.io.InputStreamReader

@Factory
class AssetSchemaRegistryFactory : BaseSchemaRegistryFactory() {
    @Singleton
    override fun typeRegistry(): TypeDefinitionRegistry = super.typeRegistry()

    override fun loadBaseSchema(): TypeDefinitionRegistry {
        val classLoader =
            Thread.currentThread().contextClassLoader
                ?: this::class.java.classLoader
                ?: javaClass.classLoader

        val baseSchemaStream =
            classLoader.getResourceAsStream("graphql/base-schema.graphqls")
                ?: throw IllegalStateException(
                    "GraphQL base schema não encontrado em: graphql/base-schema.graphqls. " +
                        "Certifique-se de que o módulo common está no classpath.",
                )

        return baseSchemaStream.use {
            SchemaParser().parse(InputStreamReader(it))
        }
    }

    override fun loadModuleSchemas(): List<TypeDefinitionRegistry> =
        listOf(
            loadSchemaFromResource("graphql/schema.graphqls"),
        )
}
