package io.github.salomax.neotool.mcp.dispatch

import io.github.salomax.neotool.mcp.render.McpToolResult
import io.github.salomax.neotool.mcp.security.McpPrincipal
import jakarta.inject.Singleton

interface McpToolHandler {
    val name: String
    val graphQLDocuments: List<String>
        get() = emptyList()

    fun call(
        arguments: Map<String, Any?>,
        principal: McpPrincipal,
    ): McpToolResult
}

@Singleton
class McpToolHandlerRegistry(
    handlers: List<McpToolHandler>,
) {
    private val handlersByName = handlers.associateBy { it.name }
    private val registeredHandlers = handlers.toList()

    fun find(name: String): McpToolHandler? = handlersByName[name]

    fun all(): Collection<McpToolHandler> = registeredHandlers
}
