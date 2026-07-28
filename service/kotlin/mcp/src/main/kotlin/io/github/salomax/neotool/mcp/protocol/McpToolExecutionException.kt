package io.github.salomax.neotool.mcp.protocol

class McpToolExecutionException(
    val code: String,
    val service: String,
    val field: List<String>? = null,
    val parameters: Map<String, Any?> = emptyMap(),
    val detail: String? = null,
    val internal: Boolean = false,
    cause: Throwable? = null,
) : RuntimeException(buildMessage(code, detail), cause) {
    companion object {
        private fun buildMessage(
            code: String,
            detail: String?,
        ): String =
            detail
                ?.takeIf { it.isNotBlank() }
                ?.let { "MCP tool execution failed: $code - $it" }
                ?: "MCP tool execution failed: $code"
    }
}
