package io.github.salomax.neotool.mcp.catalog

data class McpCatalogTool(
    val name: String,
    val description: String,
    val inputSchema: Map<String, Any?>,
    val mcpExposed: Boolean,
    val mcpRequiredScope: String?,
    val sensitiveInputFields: List<String> = emptyList(),
)
