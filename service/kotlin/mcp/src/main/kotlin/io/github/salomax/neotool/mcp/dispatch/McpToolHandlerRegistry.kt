package io.github.salomax.neotool.mcp.dispatch

import io.github.salomax.neotool.mcp.render.McpToolResult
import io.github.salomax.neotool.mcp.security.McpPrincipal
import jakarta.inject.Singleton

interface McpToolHandler {
    val name: String

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

    fun find(name: String): McpToolHandler? = handlersByName[name]
}
