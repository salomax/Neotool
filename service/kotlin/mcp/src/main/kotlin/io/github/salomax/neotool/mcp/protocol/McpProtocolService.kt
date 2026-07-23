package io.github.salomax.neotool.mcp.protocol

import io.github.salomax.neotool.common.error.DomainException
import io.github.salomax.neotool.mcp.catalog.McpCatalogTool
import io.github.salomax.neotool.mcp.catalog.McpToolCatalog
import io.github.salomax.neotool.mcp.dispatch.McpToolHandlerRegistry
import io.github.salomax.neotool.mcp.render.McpToolErrorMapper
import io.github.salomax.neotool.mcp.render.McpToolResultRenderer
import io.github.salomax.neotool.mcp.security.McpPrincipal
import jakarta.inject.Singleton
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

@Singleton
class McpProtocolService(
    private val toolCatalog: McpToolCatalog,
    private val handlerRegistry: McpToolHandlerRegistry,
    private val resultRenderer: McpToolResultRenderer,
    private val errorMapper: McpToolErrorMapper,
) {
    fun handle(
        request: Map<String, Any?>,
        principal: McpPrincipal,
    ): JsonRpcResponse? {
        val id = request["id"]
        val method = request["method"] as? String
        if (request["jsonrpc"] != "2.0" || method.isNullOrBlank()) {
            return error(id, JsonRpcErrorCodes.INVALID_REQUEST, "Invalid JSON-RPC request")
        }

        return when (method) {
            "initialize" -> success(id, initialize(request.params()))
            "notifications/initialized" -> null
            "tools/list" -> success(id, listTools(principal))
            "tools/call" -> callTool(id, request.params(), principal)
            else -> error(id, JsonRpcErrorCodes.METHOD_NOT_FOUND, "Method not found")
        }
    }

    private fun initialize(params: Map<String, Any?>): InitializeResult {
        val clientProtocolVersion = params["protocolVersion"]?.toString()
        if (clientProtocolVersion !in SUPPORTED_PROTOCOL_VERSIONS) {
            logger.debug {
                "Client requested unsupported MCP protocol version '$clientProtocolVersion'; " +
                    "responding with '$LATEST_PROTOCOL_VERSION'"
            }
        }
        val protocolVersion =
            if (clientProtocolVersion in SUPPORTED_PROTOCOL_VERSIONS) {
                clientProtocolVersion!!
            } else {
                LATEST_PROTOCOL_VERSION
            }

        return InitializeResult(
            protocolVersion = protocolVersion,
            capabilities = ServerCapabilities(tools = ToolsCapability(listChanged = false)),
            serverInfo =
                ImplementationInfo(
                    name = "neotool-mcp",
                    title = "Neotool MCP Server",
                    version = "0.1.0",
                ),
        )
    }

    private fun listTools(principal: McpPrincipal): ListToolsResult {
        val tools =
            toolCatalog
                .listTools()
                .filter { it.isVisibleTo(principal.scopes) }
                .map {
                    McpTool(
                        name = it.name,
                        description = it.description,
                        inputSchema = it.inputSchema,
                    )
                }

        return ListToolsResult(tools = tools)
    }

    private fun callTool(
        id: Any?,
        params: Map<String, Any?>,
        principal: McpPrincipal,
    ): JsonRpcResponse {
        val name =
            params["name"]?.toString()?.takeIf { it.isNotBlank() }
                ?: return error(id, JsonRpcErrorCodes.INVALID_PARAMS, "Tool name is required")
        val catalogTool =
            toolCatalog
                .listTools()
                .firstOrNull { it.name == name && it.isVisibleTo(principal.scopes) }
                ?: return error(
                    id = id,
                    code = JsonRpcErrorCodes.INVALID_PARAMS,
                    message = "Unknown tool",
                    data = mapOf("code" to "tool_not_found", "toolName" to name),
                )
        val handler =
            handlerRegistry.find(catalogTool.name)
                ?: return error(
                    id = id,
                    code = JsonRpcErrorCodes.INVALID_PARAMS,
                    message = "Unknown tool",
                    data = mapOf("code" to "tool_not_found", "toolName" to name),
                )
        val arguments = (params["arguments"] as? Map<*, *>)?.normalizeMap() ?: emptyMap()
        val result =
            try {
                handler.call(arguments, principal)
            } catch (e: McpToolExecutionException) {
                logger.debug(e) { "MCP tool returned mapped error: $name" }
                return success(id, resultRenderer.render(errorMapper.toResult(e), principal.scopes))
            } catch (e: DomainException) {
                logger.debug(e) { "MCP tool returned domain error: $name" }
                return success(id, resultRenderer.render(errorMapper.toResult(e, service = "mcp"), principal.scopes))
            } catch (e: Exception) {
                logger.warn(e) { "MCP tool handler failed: $name" }
                return error(
                    id = id,
                    code = JsonRpcErrorCodes.INTERNAL_ERROR,
                    message = "Tool execution failed",
                    data = mapOf("code" to "tool_execution_failed", "toolName" to name),
                )
            }
        return success(
            id,
            resultRenderer.render(result, principal.scopes),
        )
    }

    private fun McpCatalogTool.isVisibleTo(scopes: Set<String>): Boolean {
        if (!mcpExposed) {
            return false
        }
        val requiredScope = mcpRequiredScope?.takeIf { it.isNotBlank() } ?: return false
        return requiredScope in scopes
    }

    private fun Map<String, Any?>.params(): Map<String, Any?> =
        (this["params"] as? Map<*, *>)?.normalizeMap() ?: emptyMap()

    private fun Map<*, *>.normalizeMap(): Map<String, Any?> =
        entries.associate { (key, value) ->
            key.toString() to value
        }

    private fun success(
        id: Any?,
        result: Any,
    ): JsonRpcResponse = JsonRpcResponse(id = id, result = result)

    private fun error(
        id: Any?,
        code: Int,
        message: String,
        data: Map<String, Any?>? = null,
    ): JsonRpcResponse =
        JsonRpcResponse(
            id = id,
            error =
                JsonRpcError(
                    code = code,
                    message = message,
                    data = data,
                ),
        )

    companion object {
        const val LATEST_PROTOCOL_VERSION = "2025-06-18"
        val SUPPORTED_PROTOCOL_VERSIONS = setOf(LATEST_PROTOCOL_VERSION)
    }
}
