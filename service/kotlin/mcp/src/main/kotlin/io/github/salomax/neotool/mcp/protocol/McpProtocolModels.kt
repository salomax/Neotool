package io.github.salomax.neotool.mcp.protocol

import io.micronaut.serde.annotation.Serdeable

@Serdeable
data class InitializeResult(
    val protocolVersion: String,
    val capabilities: ServerCapabilities,
    val serverInfo: ImplementationInfo,
)

@Serdeable
data class ServerCapabilities(
    val tools: ToolsCapability = ToolsCapability(),
)

@Serdeable
data class ToolsCapability(
    val listChanged: Boolean = false,
)

@Serdeable
data class ImplementationInfo(
    val name: String,
    val title: String,
    val version: String,
)

@Serdeable
data class ListToolsResult(
    val tools: List<McpTool>,
    val nextCursor: String? = null,
)

@Serdeable
data class McpTool(
    val name: String,
    val description: String,
    val inputSchema: Map<String, Any?>,
)

@Serdeable
data class CallToolResult(
    val content: List<TextContent>,
    val isError: Boolean = false,
)

@Serdeable
data class TextContent(
    val type: String = "text",
    val text: String,
)
