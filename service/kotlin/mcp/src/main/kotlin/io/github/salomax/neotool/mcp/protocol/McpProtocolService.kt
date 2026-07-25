package io.github.salomax.neotool.mcp.protocol

import io.github.salomax.neotool.common.error.DomainException
import io.github.salomax.neotool.common.security.agent.AgentCallContext
import io.github.salomax.neotool.mcp.audit.ToolCallAuditOutcome
import io.github.salomax.neotool.mcp.audit.ToolCallAuditRecord
import io.github.salomax.neotool.mcp.audit.ToolCallAuditService
import io.github.salomax.neotool.mcp.catalog.McpCatalogTool
import io.github.salomax.neotool.mcp.catalog.McpToolCatalog
import io.github.salomax.neotool.mcp.dispatch.McpToolHandlerRegistry
import io.github.salomax.neotool.mcp.render.McpToolErrorMapper
import io.github.salomax.neotool.mcp.render.McpToolErrorResult
import io.github.salomax.neotool.mcp.render.McpToolResultRenderer
import io.github.salomax.neotool.mcp.render.McpToolSuccessResult
import io.github.salomax.neotool.mcp.security.McpPrincipal
import jakarta.inject.Singleton
import mu.KotlinLogging
import java.time.Instant
import java.util.UUID

private val logger = KotlinLogging.logger {}

@Singleton
class McpProtocolService(
    private val toolCatalog: McpToolCatalog,
    private val handlerRegistry: McpToolHandlerRegistry,
    private val resultRenderer: McpToolResultRenderer,
    private val errorMapper: McpToolErrorMapper,
    private val auditService: ToolCallAuditService,
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
        val startedAt = Instant.now()
        val startedNanos = System.nanoTime()
        val arguments = (params["arguments"] as? Map<*, *>)?.normalizeMap() ?: emptyMap()
        val name =
            params["name"]?.toString()?.takeIf { it.isNotBlank() }
                ?: return auditedToolError(
                    id = id,
                    principal = principal,
                    toolCallId = UUID.randomUUID(),
                    occurredAt = startedAt,
                    startedNanos = startedNanos,
                    toolName = null,
                    arguments = arguments,
                    sensitiveInputFields = emptyList(),
                    redactAllArguments = true,
                    outcome = ToolCallAuditOutcome.ERROR,
                    errorCode = "invalid_params",
                    errorMessage = "Tool name is required",
                    jsonRpcCode = JsonRpcErrorCodes.INVALID_PARAMS,
                    jsonRpcMessage = "Tool name is required",
                )
        val toolCallId = UUID.randomUUID()
        var catalogTool: McpCatalogTool? = null
        try {
            val catalogTools = toolCatalog.listTools()
            val rawTool = catalogTools.firstOrNull { it.name == name }
            if (rawTool == null) {
                return auditedToolError(
                    id = id,
                    principal = principal,
                    toolCallId = toolCallId,
                    occurredAt = startedAt,
                    startedNanos = startedNanos,
                    toolName = name,
                    arguments = arguments,
                    sensitiveInputFields = emptyList(),
                    redactAllArguments = true,
                    outcome = ToolCallAuditOutcome.ERROR,
                    errorCode = "tool_not_found",
                    errorMessage = "Unknown tool",
                    jsonRpcCode = JsonRpcErrorCodes.INVALID_PARAMS,
                    jsonRpcMessage = "Unknown tool",
                    jsonRpcData = mapOf("code" to "tool_not_found", "toolName" to name),
                )
            }
            if (!rawTool.isVisibleTo(principal.scopes)) {
                return auditedToolError(
                    id = id,
                    principal = principal,
                    toolCallId = toolCallId,
                    occurredAt = startedAt,
                    startedNanos = startedNanos,
                    toolName = name,
                    arguments = arguments,
                    sensitiveInputFields = rawTool.sensitiveInputFields,
                    outcome = ToolCallAuditOutcome.REJECTED,
                    errorCode = "insufficient_scope",
                    errorMessage = "Tool requires additional scope",
                    jsonRpcCode = JsonRpcErrorCodes.INVALID_PARAMS,
                    jsonRpcMessage = "Unknown tool",
                    jsonRpcData = mapOf("code" to "tool_not_found", "toolName" to name),
                )
            }
            catalogTool = rawTool
            val handler =
                handlerRegistry.find(rawTool.name)
                    ?: return auditedToolError(
                        id = id,
                        principal = principal,
                        toolCallId = toolCallId,
                        occurredAt = startedAt,
                        startedNanos = startedNanos,
                        toolName = name,
                        arguments = arguments,
                        sensitiveInputFields = rawTool.sensitiveInputFields,
                        outcome = ToolCallAuditOutcome.ERROR,
                        errorCode = "tool_not_found",
                        errorMessage = "Unknown tool",
                        jsonRpcCode = JsonRpcErrorCodes.INVALID_PARAMS,
                        jsonRpcMessage = "Unknown tool",
                        jsonRpcData = mapOf("code" to "tool_not_found", "toolName" to name),
                    )
            val principalWithAgentContext =
                principal.copy(
                    agentCallContext =
                        AgentCallContext(
                            toolCallId = toolCallId,
                            originClientId = principal.clientId,
                            originTokenId = principal.principal.tokenId,
                        ),
                )
            val result = handler.call(arguments, principalWithAgentContext)
            val auditOutcome =
                when (result) {
                    is McpToolSuccessResult -> ToolCallAuditOutcome.SUCCESS
                    is McpToolErrorResult -> ToolCallAuditOutcome.ERROR
                }
            val auditErrorCode = (result as? McpToolErrorResult)?.code
            val auditErrorMessage = if (result is McpToolErrorResult) "Tool returned structured error" else null
            recordAudit(
                principal = principal,
                toolCallId = toolCallId,
                occurredAt = startedAt,
                startedNanos = startedNanos,
                toolName = name,
                arguments = arguments,
                sensitiveInputFields = rawTool.sensitiveInputFields,
                outcome = auditOutcome,
                errorCode = auditErrorCode,
                errorMessage = auditErrorMessage,
            )
            return success(
                id,
                resultRenderer.render(result, principal.scopes),
            )
        } catch (e: McpToolExecutionException) {
            logger.debug(e) { "MCP tool returned mapped error: $name" }
            val mappedError = errorMapper.toResult(e)
            recordAudit(
                principal = principal,
                toolCallId = toolCallId,
                occurredAt = startedAt,
                startedNanos = startedNanos,
                toolName = name,
                arguments = arguments,
                sensitiveInputFields = catalogTool?.sensitiveInputFields.orEmpty(),
                outcome = ToolCallAuditOutcome.ERROR,
                errorCode = mappedError.code,
                errorMessage = e.message,
            )
            return success(id, resultRenderer.render(mappedError, principal.scopes))
        } catch (e: DomainException) {
            logger.debug(e) { "MCP tool returned domain error: $name" }
            val mappedError = errorMapper.toResult(e, service = "mcp")
            recordAudit(
                principal = principal,
                toolCallId = toolCallId,
                occurredAt = startedAt,
                startedNanos = startedNanos,
                toolName = name,
                arguments = arguments,
                sensitiveInputFields = catalogTool?.sensitiveInputFields.orEmpty(),
                outcome = ToolCallAuditOutcome.ERROR,
                errorCode = mappedError.code,
                errorMessage = e.message,
            )
            return success(id, resultRenderer.render(mappedError, principal.scopes))
        } catch (e: Exception) {
            logger.warn(e) { "MCP tool handler failed: $name" }
            return auditedToolError(
                id = id,
                principal = principal,
                toolCallId = toolCallId,
                occurredAt = startedAt,
                startedNanos = startedNanos,
                toolName = name,
                arguments = arguments,
                sensitiveInputFields = catalogTool?.sensitiveInputFields.orEmpty(),
                outcome = ToolCallAuditOutcome.ERROR,
                errorCode = "tool_execution_failed",
                errorMessage = e.message ?: "Tool execution failed",
                jsonRpcCode = JsonRpcErrorCodes.INTERNAL_ERROR,
                jsonRpcMessage = "Tool execution failed",
                jsonRpcData = mapOf("code" to "tool_execution_failed", "toolName" to name),
            )
        }
    }

    private fun auditedToolError(
        id: Any?,
        principal: McpPrincipal,
        toolCallId: UUID,
        occurredAt: Instant,
        startedNanos: Long,
        toolName: String?,
        arguments: Map<String, Any?>,
        sensitiveInputFields: List<String>,
        redactAllArguments: Boolean = false,
        outcome: ToolCallAuditOutcome,
        errorCode: String,
        errorMessage: String,
        jsonRpcCode: Int,
        jsonRpcMessage: String,
        jsonRpcData: Map<String, Any?>? = null,
    ): JsonRpcResponse {
        recordAudit(
            principal = principal,
            toolCallId = toolCallId,
            occurredAt = occurredAt,
            startedNanos = startedNanos,
            toolName = toolName,
            arguments = arguments,
            sensitiveInputFields = sensitiveInputFields,
            redactAllArguments = redactAllArguments,
            outcome = outcome,
            errorCode = errorCode,
            errorMessage = errorMessage,
        )
        return error(
            id = id,
            code = jsonRpcCode,
            message = jsonRpcMessage,
            data = jsonRpcData,
        )
    }

    private fun recordAudit(
        principal: McpPrincipal,
        toolCallId: UUID,
        occurredAt: Instant,
        startedNanos: Long,
        toolName: String?,
        arguments: Map<String, Any?>,
        sensitiveInputFields: List<String>,
        redactAllArguments: Boolean = false,
        outcome: ToolCallAuditOutcome,
        errorCode: String? = null,
        errorMessage: String? = null,
    ) {
        auditService.record(
            ToolCallAuditRecord(
                id = toolCallId,
                occurredAt = occurredAt,
                userId = principal.principal.userId,
                clientId = principal.clientId,
                tokenId = principal.principal.tokenId,
                toolName = toolName,
                arguments = arguments,
                sensitiveInputFields = sensitiveInputFields,
                redactAllArguments = redactAllArguments,
                outcome = outcome,
                errorCode = errorCode,
                errorMessage = errorMessage,
                latencyMs = ((System.nanoTime() - startedNanos) / 1_000_000).coerceAtLeast(0),
            ),
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
