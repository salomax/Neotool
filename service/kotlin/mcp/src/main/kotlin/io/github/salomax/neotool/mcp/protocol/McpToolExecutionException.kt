package io.github.salomax.neotool.mcp.protocol

class McpToolExecutionException(
    val code: String,
    val service: String,
    val field: List<String>? = null,
    val parameters: Map<String, Any?> = emptyMap(),
    cause: Throwable? = null,
) : RuntimeException("MCP tool execution failed: $code", cause)
