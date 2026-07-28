package io.github.salomax.neotool.mcp.protocol

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.github.salomax.neotool.common.security.principal.PrincipalType
import io.github.salomax.neotool.common.security.principal.RequestPrincipal
import io.github.salomax.neotool.mcp.audit.ToolCallAuditOutcome
import io.github.salomax.neotool.mcp.audit.ToolCallAuditRecord
import io.github.salomax.neotool.mcp.audit.ToolCallAuditService
import io.github.salomax.neotool.mcp.catalog.McpCatalogTool
import io.github.salomax.neotool.mcp.catalog.McpToolCatalog
import io.github.salomax.neotool.mcp.dispatch.McpToolHandler
import io.github.salomax.neotool.mcp.dispatch.McpToolHandlerRegistry
import io.github.salomax.neotool.mcp.render.McpToolErrorMapper
import io.github.salomax.neotool.mcp.render.McpToolErrorResult
import io.github.salomax.neotool.mcp.render.McpToolResult
import io.github.salomax.neotool.mcp.render.McpToolResultRenderer
import io.github.salomax.neotool.mcp.render.McpToolSuccessResult
import io.github.salomax.neotool.mcp.security.McpPrincipal
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import java.util.UUID

class McpProtocolServiceTest {
    @Test
    fun `tools list excludes tools not exposed to MCP`() {
        val service =
            protocolService(
                tools =
                    listOf(
                        tool(name = "visible", exposed = true, requiredScope = "finance:cashflow:read"),
                        tool(name = "internal", exposed = false, requiredScope = "finance:cashflow:read"),
                    ),
            )

        val result = service.handle(toolsListRequest(), principal("finance:cashflow:read"))!!.result as ListToolsResult

        assertThat(result.tools.map { it.name }).containsExactly("visible")
    }

    @Test
    fun `tools list excludes MCP exposed tools outside token scopes`() {
        val service =
            protocolService(
                tools =
                    listOf(
                        tool(name = "cashflow", exposed = true, requiredScope = "finance:cashflow:read"),
                        tool(name = "investments", exposed = true, requiredScope = "finance:investments:read"),
                    ),
            )

        val result = service.handle(toolsListRequest(), principal("finance:cashflow:read"))!!.result as ListToolsResult

        assertThat(result.tools.map { it.name }).containsExactly("cashflow")
    }

    @Test
    fun `tools list excludes exposed tool with missing required scope metadata`() {
        val service =
            protocolService(
                tools =
                    listOf(
                        tool(name = "missing_scope", exposed = true, requiredScope = null),
                        tool(name = "blank_scope", exposed = true, requiredScope = " "),
                    ),
            )

        val result = service.handle(toolsListRequest(), principal("finance:cashflow:read"))!!.result as ListToolsResult

        assertThat(result.tools).isEmpty()
    }

    @Test
    fun `tools list includes only tools matching both MCP exposure and scope`() {
        val service =
            protocolService(
                tools =
                    listOf(
                        tool(name = "visible", exposed = true, requiredScope = "finance:cashflow:read"),
                        tool(name = "wrong_scope", exposed = true, requiredScope = "finance:investments:read"),
                        tool(name = "not_exposed", exposed = false, requiredScope = "finance:cashflow:read"),
                    ),
            )

        val result = service.handle(toolsListRequest(), principal("finance:cashflow:read"))!!.result as ListToolsResult

        assertThat(result.tools.map { it.name }).containsExactly("visible")
    }

    @Test
    fun `initialize falls back to latest protocol version for unsupported client version`() {
        val service = protocolService()

        val response =
            service.handle(
                mapOf(
                    "jsonrpc" to "2.0",
                    "id" to "init-1",
                    "method" to "initialize",
                    "params" to
                        mapOf(
                            "protocolVersion" to "2025-03-26",
                            "capabilities" to emptyMap<String, Any>(),
                        ),
                ),
                principal("finance:cashflow:read"),
            )!!
        val result = response.result as InitializeResult

        assertThat(result.protocolVersion).isEqualTo("2025-06-18")
    }

    @Test
    fun `tools call maps handler exception to structured internal error`() {
        val service =
            protocolService(
                tools = listOf(tool(name = "exploding_tool", exposed = true, requiredScope = "finance:cashflow:read")),
                handlers =
                    listOf(
                        object : McpToolHandler {
                            override val name: String = "exploding_tool"

                            override fun call(
                                arguments: Map<String, Any?>,
                                principal: McpPrincipal,
                            ): McpToolResult = error("boom")
                        },
                    ),
            )

        val response =
            service.handle(
                mapOf(
                    "jsonrpc" to "2.0",
                    "id" to "call-1",
                    "method" to "tools/call",
                    "params" to mapOf("name" to "exploding_tool", "arguments" to emptyMap<String, Any>()),
                ),
                principal("finance:cashflow:read"),
            )!!

        assertThat(response.result).isNull()
        assertThat(response.error?.code).isEqualTo(JsonRpcErrorCodes.INTERNAL_ERROR)
        assertThat(response.error?.message).isEqualTo("Tool execution failed")
        assertThat(response.error?.data).containsEntry("code", "tool_execution_failed")
        assertThat(response.error?.data).containsEntry("toolName", "exploding_tool")
        assertThat(response.error.toString()).doesNotContain("boom")
    }

    @Test
    fun `tools call maps expected tool execution error to MCP tool error result`() {
        val service =
            protocolService(
                tools = listOf(tool(name = "domain_tool", exposed = true, requiredScope = "finance:cashflow:read")),
                handlers =
                    listOf(
                        object : McpToolHandler {
                            override val name: String = "domain_tool"

                            override fun call(
                                arguments: Map<String, Any?>,
                                principal: McpPrincipal,
                            ): McpToolResult =
                                throw McpToolExecutionException(
                                    code = "BANKING_RULE_FAILED",
                                    service = "banking",
                                    field = listOf("input"),
                                )
                        },
                    ),
            )

        val response =
            service.handle(
                mapOf(
                    "jsonrpc" to "2.0",
                    "id" to "call-2",
                    "method" to "tools/call",
                    "params" to mapOf("name" to "domain_tool", "arguments" to emptyMap<String, Any>()),
                ),
                principal("finance:cashflow:read"),
            )!!
        val result = response.result as CallToolResult

        assertThat(response.error).isNull()
        assertThat(result.isError).isTrue()
        assertThat(result.content.first().text).contains("BANKING_RULE_FAILED")
    }

    @Test
    fun `mapped tool errors preserve detail in audit without leaking it to the agent payload`() {
        val auditService = mock<ToolCallAuditService>()
        val service =
            protocolService(
                tools = listOf(tool(name = "detail_tool", exposed = true, requiredScope = "finance:cashflow:read")),
                handlers =
                    listOf(
                        object : McpToolHandler {
                            override val name: String = "detail_tool"

                            override fun call(
                                arguments: Map<String, Any?>,
                                principal: McpPrincipal,
                            ): McpToolResult =
                                throw McpToolExecutionException(
                                    code = "MCP_INTERNAL_ERROR",
                                    service = "mcp",
                                    detail = "Field 'symbol' is not defined by type 'InvestmentAsset'",
                                    internal = true,
                                )
                        },
                    ),
                auditService = auditService,
            )

        val response =
            service.handle(
                mapOf(
                    "jsonrpc" to "2.0",
                    "id" to "call-detail",
                    "method" to "tools/call",
                    "params" to mapOf("name" to "detail_tool", "arguments" to emptyMap<String, Any>()),
                ),
                principal("finance:cashflow:read"),
            )!!

        val result = response.result as CallToolResult
        assertThat(result.isError).isTrue()
        assertThat(result.content.first().text).contains("MCP_INTERNAL_ERROR").doesNotContain("InvestmentAsset")

        val captor = argumentCaptor<ToolCallAuditRecord>()
        verify(auditService).record(captor.capture())
        assertThat(captor.firstValue.errorCode).isEqualTo("MCP_INTERNAL_ERROR")
        assertThat(captor.firstValue.errorMessage).contains("Field 'symbol' is not defined by type 'InvestmentAsset'")
    }

    @Test
    fun `tools call injects agent call context into downstream principal`() {
        lateinit var observedPrincipal: McpPrincipal
        val service =
            protocolService(
                tools = listOf(tool(name = "originated_tool", exposed = true, requiredScope = "finance:cashflow:read")),
                handlers =
                    listOf(
                        object : McpToolHandler {
                            override val name: String = "originated_tool"

                            override fun call(
                                arguments: Map<String, Any?>,
                                principal: McpPrincipal,
                            ): McpToolResult {
                                observedPrincipal = principal
                                return McpToolSuccessResult(mapOf("ok" to true))
                            }
                        },
                    ),
            )

        service.handle(
            mapOf(
                "jsonrpc" to "2.0",
                "id" to "call-3",
                "method" to "tools/call",
                "params" to mapOf("name" to "originated_tool", "arguments" to emptyMap<String, Any>()),
            ),
            principal("finance:cashflow:read"),
        )

        assertThat(observedPrincipal.agentCallContext).isNotNull
        assertThat(observedPrincipal.agentCallContext!!.originClientId).isEqualTo(observedPrincipal.clientId)
        assertThat(observedPrincipal.agentCallContext!!.originTokenId).isEqualTo(observedPrincipal.principal.tokenId)
    }

    @Test
    fun `tools call records latency for success outcome`() {
        val auditService = mock<ToolCallAuditService>()
        val service =
            protocolService(
                tools = listOf(tool(name = "latency_success", exposed = true, requiredScope = "finance:cashflow:read")),
                handlers =
                    listOf(
                        object : McpToolHandler {
                            override val name: String = "latency_success"

                            override fun call(
                                arguments: Map<String, Any?>,
                                principal: McpPrincipal,
                            ): McpToolResult = McpToolSuccessResult(mapOf("ok" to true))
                        },
                    ),
                auditService = auditService,
            )

        service.handle(
            mapOf(
                "jsonrpc" to "2.0",
                "id" to "call-4",
                "method" to "tools/call",
                "params" to mapOf("name" to "latency_success", "arguments" to emptyMap<String, Any>()),
            ),
            principal("finance:cashflow:read"),
        )

        val captor = argumentCaptor<ToolCallAuditRecord>()
        verify(auditService).record(captor.capture())
        assertThat(captor.firstValue.outcome).isEqualTo(ToolCallAuditOutcome.SUCCESS)
        assertThat(captor.firstValue.latencyMs).isGreaterThanOrEqualTo(0)
    }

    @Test
    fun `tools call records latency for error outcome`() {
        val auditService = mock<ToolCallAuditService>()
        val service =
            protocolService(
                tools = listOf(tool(name = "latency_error", exposed = true, requiredScope = "finance:cashflow:read")),
                handlers =
                    listOf(
                        object : McpToolHandler {
                            override val name: String = "latency_error"

                            override fun call(
                                arguments: Map<String, Any?>,
                                principal: McpPrincipal,
                            ): McpToolResult = McpToolErrorResult(code = "BAD_INPUT", service = "mcp")
                        },
                    ),
                auditService = auditService,
            )

        service.handle(
            mapOf(
                "jsonrpc" to "2.0",
                "id" to "call-5",
                "method" to "tools/call",
                "params" to mapOf("name" to "latency_error", "arguments" to emptyMap<String, Any>()),
            ),
            principal("finance:cashflow:read"),
        )

        val captor = argumentCaptor<ToolCallAuditRecord>()
        verify(auditService).record(captor.capture())
        assertThat(captor.firstValue.outcome).isEqualTo(ToolCallAuditOutcome.ERROR)
        assertThat(captor.firstValue.errorCode).isEqualTo("BAD_INPUT")
        assertThat(captor.firstValue.latencyMs).isGreaterThanOrEqualTo(0)
    }

    @Test
    fun `tools call outside caller scope is audited as rejected while remaining externally hidden`() {
        val auditService = mock<ToolCallAuditService>()
        val service =
            protocolService(
                tools =
                    listOf(
                        tool(name = "guarded_tool", exposed = true, requiredScope = "finance:transactions:read"),
                    ),
                auditService = auditService,
            )

        val response =
            service.handle(
                mapOf(
                    "jsonrpc" to "2.0",
                    "id" to "call-6",
                    "method" to "tools/call",
                    "params" to mapOf("name" to "guarded_tool", "arguments" to emptyMap<String, Any>()),
                ),
                principal("finance:cashflow:read"),
            )!!

        assertThat(response.error?.message).isEqualTo("Unknown tool")
        val captor = argumentCaptor<ToolCallAuditRecord>()
        verify(auditService).record(captor.capture())
        assertThat(captor.firstValue.outcome).isEqualTo(ToolCallAuditOutcome.REJECTED)
        assertThat(captor.firstValue.errorCode).isEqualTo("insufficient_scope")
    }

    @Test
    fun `unknown tool audits arguments with redact all fallback`() {
        val auditService = mock<ToolCallAuditService>()
        val service = protocolService(auditService = auditService)

        service.handle(
            mapOf(
                "jsonrpc" to "2.0",
                "id" to "call-7",
                "method" to "tools/call",
                "params" to
                    mapOf(
                        "name" to "missing_tool",
                        "arguments" to mapOf("secret" to "top-secret"),
                    ),
            ),
            principal("finance:cashflow:read"),
        )

        val captor = argumentCaptor<ToolCallAuditRecord>()
        verify(auditService).record(captor.capture())
        assertThat(captor.firstValue.errorCode).isEqualTo("tool_not_found")
        assertThat(captor.firstValue.redactAllArguments).isTrue()
    }

    private fun protocolService(
        tools: List<McpCatalogTool> = emptyList(),
        handlers: List<McpToolHandler> = emptyList(),
        auditService: ToolCallAuditService = mock(),
    ): McpProtocolService =
        McpProtocolService(
            toolCatalog =
                object : McpToolCatalog {
                    override fun listTools(): List<McpCatalogTool> = tools
                },
            handlerRegistry = McpToolHandlerRegistry(handlers),
            resultRenderer = McpToolResultRenderer(jacksonObjectMapper()),
            errorMapper = McpToolErrorMapper(),
            auditService = auditService,
        )

    private fun toolsListRequest(): Map<String, Any?> =
        mapOf(
            "jsonrpc" to "2.0",
            "id" to "list-1",
            "method" to "tools/list",
        )

    private fun tool(
        name: String,
        exposed: Boolean,
        requiredScope: String?,
    ): McpCatalogTool =
        McpCatalogTool(
            name = name,
            description = "$name description",
            inputSchema = mapOf("type" to "object"),
            mcpExposed = exposed,
            mcpRequiredScope = requiredScope,
        )

    private fun principal(vararg scopes: String): McpPrincipal =
        McpPrincipal(
            principal =
                RequestPrincipal(
                    principalType = PrincipalType.USER,
                    userId = UUID.randomUUID(),
                    serviceId = null,
                    token = "token",
                    tokenId = "token-id",
                    permissionsFromToken = emptyList(),
                    currentAccountId = UUID.randomUUID(),
                ),
            clientId = "client",
            scopes = scopes.toSet(),
        )
}
