package io.github.salomax.neotool.mcp.catalog

import io.github.salomax.neotool.common.service.GraphQLServiceClient
import io.micronaut.context.annotation.Property
import jakarta.inject.Singleton
import kotlinx.coroutines.runBlocking

@Singleton
class AssistantMcpToolCatalog(
    private val graphQLServiceClient: GraphQLServiceClient,
    @Property(name = "mcp.catalog.target-audience", defaultValue = "apollo-router")
    private val targetAudience: String,
) : McpToolCatalog {
    override fun listTools(): List<McpCatalogTool> {
        val response =
            runBlocking {
                graphQLServiceClient.query(
                    query = AVAILABLE_TOOLS_QUERY,
                    targetAudience = targetAudience,
                )
            }

        if (!response.errors.isNullOrEmpty()) {
            throw IllegalStateException("Assistant catalog returned GraphQL errors")
        }

        val rawTools = response.data?.get("availableTools") as? List<*> ?: return emptyList()
        return rawTools.mapNotNull { raw ->
            (raw as? Map<*, *>)?.toCatalogTool()
        }
    }

    private fun Map<*, *>.toCatalogTool(): McpCatalogTool? {
        val name = this["name"]?.toString()?.takeIf { it.isNotBlank() } ?: return null
        val description = this["description"]?.toString().orEmpty()
        val inputSchema = (this["inputSchema"] as? Map<*, *>)?.normalizeMap() ?: emptyMap()
        val mcpExposed = this["mcpExposed"] as? Boolean ?: false
        val mcpRequiredScope = this["mcpRequiredScope"]?.toString()?.takeIf { it.isNotBlank() }
        return McpCatalogTool(
            name = name,
            description = description,
            inputSchema = inputSchema,
            mcpExposed = mcpExposed,
            mcpRequiredScope = mcpRequiredScope,
        )
    }

    private fun Map<*, *>.normalizeMap(): Map<String, Any?> =
        entries.associate { (key, value) ->
            key.toString() to value
        }

    companion object {
        private const val AVAILABLE_TOOLS_QUERY = """
            query McpAvailableTools {
              availableTools {
                name
                description
                inputSchema
                mcpExposed
                mcpRequiredScope
              }
            }
        """
    }
}
