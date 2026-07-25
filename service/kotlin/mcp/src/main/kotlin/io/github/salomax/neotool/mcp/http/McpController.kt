package io.github.salomax.neotool.mcp.http

import io.github.salomax.neotool.mcp.audit.ToolCallAuditOutcome
import io.github.salomax.neotool.mcp.audit.ToolCallAuditRecord
import io.github.salomax.neotool.mcp.audit.ToolCallAuditService
import io.github.salomax.neotool.mcp.protocol.JsonRpcResponse
import io.github.salomax.neotool.mcp.protocol.McpProtocolService
import io.github.salomax.neotool.mcp.security.McpAuthenticationException
import io.github.salomax.neotool.mcp.security.McpAuthenticationFilter
import io.github.salomax.neotool.mcp.security.McpAuthenticationService
import io.github.salomax.neotool.mcp.security.McpPrincipal
import io.github.salomax.neotool.mcp.security.OAuthErrorResponseFactory
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Consumes
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Post
import io.micronaut.http.annotation.Produces
import io.micronaut.http.sse.Event
import reactor.core.publisher.Flux
import java.time.Instant
import java.util.UUID

@Controller("/mcp")
class McpController(
    private val protocolService: McpProtocolService,
    private val authenticationService: McpAuthenticationService,
    private val errorResponseFactory: OAuthErrorResponseFactory,
    private val auditService: ToolCallAuditService,
) {
    @Post
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    fun handle(
        @Body body: Map<String, Any?>,
        request: HttpRequest<*>,
    ): HttpResponse<*> {
        val principal =
            try {
                authenticationService.authenticate(request)
            } catch (e: McpAuthenticationException) {
                auditRejectedToolCall(body, e.error, e.message ?: "Authentication failed")
                return errorResponseFactory.unauthorized(e)
            }
        val response = protocolService.handle(body, principal) ?: return HttpResponse.accepted<Any>()
        return HttpResponse.ok(response)
    }

    @Post("/events")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.TEXT_EVENT_STREAM)
    fun handleEvents(
        @Body body: Map<String, Any?>,
        request: HttpRequest<*>,
    ): Flux<Event<JsonRpcResponse>> {
        val principal = authenticatedPrincipal(request)
        return Flux
            .just(body)
            .mapNotNull { protocolService.handle(it, principal) }
            .map { Event.of(it) }
    }

    private fun authenticatedPrincipal(request: HttpRequest<*>): McpPrincipal =
        request.getAttribute(McpAuthenticationFilter.PRINCIPAL_ATTRIBUTE, McpPrincipal::class.java).orElseThrow {
            IllegalStateException("MCP principal missing from authenticated request")
        }

    private fun auditRejectedToolCall(
        body: Map<String, Any?>,
        errorCode: String,
        errorMessage: String,
    ) {
        if (body["method"] != "tools/call") {
            return
        }
        val params = (body["params"] as? Map<*, *>)?.normalizeMap() ?: emptyMap()
        val toolName = params["name"]?.toString()?.takeIf { it.isNotBlank() }
        val arguments = (params["arguments"] as? Map<*, *>)?.normalizeMap() ?: emptyMap()
        auditService.record(
            ToolCallAuditRecord(
                id = UUID.randomUUID(),
                occurredAt = Instant.now(),
                toolName = toolName,
                arguments = arguments,
                redactAllArguments = true,
                outcome = ToolCallAuditOutcome.REJECTED,
                errorCode = errorCode,
                errorMessage = errorMessage,
                latencyMs = 0,
            ),
        )
    }

    private fun Map<*, *>.normalizeMap(): Map<String, Any?> =
        entries.associate { (key, value) ->
            key.toString() to value
        }
}
