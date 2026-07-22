package io.github.salomax.neotool.mcp.render

sealed interface McpToolResult

data class McpToolSuccessResult(
    val data: Map<String, Any?>,
) : McpToolResult

data class McpToolErrorResult(
    val code: String,
    val service: String,
    val field: List<String>? = null,
    val parameters: Map<String, Any?> = emptyMap(),
) : McpToolResult
