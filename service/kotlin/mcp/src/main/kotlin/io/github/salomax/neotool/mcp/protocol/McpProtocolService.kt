package io.github.salomax.neotool.mcp.protocol

import io.github.salomax.neotool.common.error.DomainException
import io.github.salomax.neotool.common.security.agent.AgentCallContext
import io.github.salomax.neotool.mcp.audit.ToolCallAuditOutcome
import io.github.salomax.neotool.mcp.audit.ToolCallAuditRecord
import io.github.salomax.neotool.mcp.audit.ToolCallAuditService
import io.github.salomax.neotool.mcp.catalog.McpCatalogTool
import io.github.salomax.neotool.mcp.catalog.McpToolCatalog
import io.github.salomax.neotool.mcp.dispatch.McpToolHandlerRegistry
import io.github.salomax.neotool.mcp.observability.McpObservabilityLogger
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
    private val observabilityLogger: McpObservabilityLogger,
) {
    fun handle(
        request: Map<String, Any?>,
        principal: McpPrincipal,
    ): JsonRpcResponse? {
        val startedNanos = System.nanoTime()
        val id = request["id"]
        val method = request["method"] as? String
        val protocolMethod = normalizeProtocolMethod(method)
        if (request["jsonrpc"] != "2.0" || method.isNullOrBlank()) {
            return error(id, JsonRpcErrorCodes.INVALID_REQUEST, "Invalid JSON-RPC request").also { response ->
                recordProtocolRequestSafely(
                    method = "invalid_request",
                    outcome = "error",
                    latencyMs = elapsedMillis(startedNanos),
                    clientId = principal.clientId,
                    attributes = rawMethodAttributes(method),
                )
            }
        }

        val params = request.params()
        return try {
            val result =
                when (protocolMethod) {
                    "initialize" -> {
                        val initializeResult = initialize(params)
                        ProtocolHandlingResult(
                            response = success(id, initializeResult),
                            outcome = "success",
                            attributes = initializeAttributes(params, initializeResult),
                        )
                    }

                    "notifications/initialized" -> ProtocolHandlingResult(response = null, outcome = "accepted")
                    "tools/list" -> {
                        val listToolsResult = listTools(principal)
                        ProtocolHandlingResult(
                            response = success(id, listToolsResult),
                            outcome = "success",
                            attributes = mapOf("tool_count" to listToolsResult.tools.size),
                        )
                    }

                    "tools/call" -> {
                        val response = callTool(id, params, principal)
                        ProtocolHandlingResult(
                            response = response,
                            outcome = toolCallOutcome(response),
                        )
                    }

                    else ->
                        ProtocolHandlingResult(
                            response = error(id, JsonRpcErrorCodes.METHOD_NOT_FOUND, "Method not found"),
                            outcome = "method_not_found",
                            attributes = rawMethodAttributes(method),
                        )
                }
            recordProtocolRequestSafely(
                method = protocolMethod,
                outcome = result.outcome,
                latencyMs = elapsedMillis(startedNanos),
                clientId = principal.clientId,
                attributes = result.attributes,
            )
            result.response
        } catch (e: Exception) {
            recordProtocolRequestSafely(
                method = protocolMethod,
                outcome = "error",
                latencyMs = elapsedMillis(startedNanos),
                clientId = principal.clientId,
                attributes = if (protocolMethod == "unknown") rawMethodAttributes(method) else emptyMap(),
            )
            throw e
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
            loadCatalogTools(method = "tools/list", clientId = principal.clientId)
                .filter { it.visibilityFor(principal.scopes) is ToolVisibility.Visible }
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
            val catalogTools = loadCatalogTools(method = "tools/call", clientId = principal.clientId)
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
            when (val visibility = rawTool.visibilityFor(principal.scopes)) {
                is ToolVisibility.Visible -> Unit
                else -> {
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
                        errorCode = visibility.errorCode,
                        errorMessage = "Tool requires additional scope",
                        jsonRpcCode = JsonRpcErrorCodes.INVALID_PARAMS,
                        jsonRpcMessage = "Unknown tool",
                        jsonRpcData = mapOf("code" to "tool_not_found", "toolName" to name),
                    )
                }
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
            if (e.internal) {
                logger.error(e) { "MCP tool hit internal defect while dispatching: $name" }
            } else {
                logger.debug(e) { "MCP tool returned mapped error: $name" }
            }
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

    private fun loadCatalogTools(
        method: String,
        clientId: String,
    ): List<McpCatalogTool> =
        try {
            toolCatalog.listTools().also { catalogTools ->
                if (catalogTools.none { it.mcpExposed && !it.mcpRequiredScope.isNullOrBlank() }) {
                    observabilityLogger.recordCatalogEmpty(
                        method = method,
                        clientId = clientId,
                        rawToolCount = catalogTools.size,
                    )
                }
            }
        } catch (e: Exception) {
            observabilityLogger.recordCatalogFetchFailure(method = method, clientId = clientId, error = e)
            throw e
        }

    private fun McpCatalogTool.visibilityFor(scopes: Set<String>): ToolVisibility {
        if (!mcpExposed) {
            return ToolVisibility.ToolNotExposed
        }
        val requiredScope = mcpRequiredScope?.takeIf { it.isNotBlank() } ?: return ToolVisibility.NoRequiredScope
        return if (requiredScope in scopes) ToolVisibility.Visible else ToolVisibility.InsufficientScope
    }

    private fun initializeAttributes(
        params: Map<String, Any?>,
        result: InitializeResult,
    ): Map<String, Any?> {
        val clientInfo = params["clientInfo"] as? Map<*, *>
        return mapOf(
            "requested_protocol_version" to params["protocolVersion"]?.toString(),
            "negotiated_protocol_version" to result.protocolVersion,
            "client_name" to clientInfo?.get("name")?.toString(),
            "client_version" to clientInfo?.get("version")?.toString(),
        )
    }

    private fun toolCallOutcome(response: JsonRpcResponse): String =
        when {
            response.error != null -> "error"
            (response.result as? CallToolResult)?.isError == true -> "error"
            else -> "success"
        }

    private fun normalizeProtocolMethod(method: String?): String =
        when (method) {
            "initialize", "notifications/initialized", "tools/list", "tools/call" -> method
            null, "" -> "invalid_request"
            else -> "unknown"
        }

    private fun rawMethodAttributes(method: String?): Map<String, Any?> =
        method?.takeIf { it.isNotBlank() }?.let { mapOf("raw_method" to it) } ?: emptyMap()

    private fun recordProtocolRequestSafely(
        method: String,
        outcome: String,
        latencyMs: Long,
        clientId: String?,
        attributes: Map<String, Any?> = emptyMap(),
    ) {
        runCatching {
            observabilityLogger.recordProtocolRequest(
                method = method,
                outcome = outcome,
                latencyMs = latencyMs,
                clientId = clientId,
                attributes = attributes,
            )
        }
    }

    private fun elapsedMillis(startedNanos: Long): Long =
        ((System.nanoTime() - startedNanos) / 1_000_000).coerceAtLeast(0)

    private data class ProtocolHandlingResult(
        val response: JsonRpcResponse?,
        val outcome: String,
        val attributes: Map<String, Any?> = emptyMap(),
    )

    private sealed interface ToolVisibility {
        data object Visible : ToolVisibility

        data object ToolNotExposed : ToolVisibility

        data object NoRequiredScope : ToolVisibility

        data object InsufficientScope : ToolVisibility

        val errorCode: String
            get() =
                when (this) {
                    Visible -> error("Visible tools do not map to error codes")
                    ToolNotExposed -> "tool_not_exposed"
                    NoRequiredScope -> "no_required_scope"
                    InsufficientScope -> "insufficient_scope"
                }
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
