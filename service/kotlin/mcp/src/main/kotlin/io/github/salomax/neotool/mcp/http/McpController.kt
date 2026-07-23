package io.github.salomax.neotool.mcp.http

import io.github.salomax.neotool.mcp.protocol.JsonRpcResponse
import io.github.salomax.neotool.mcp.protocol.McpProtocolService
import io.github.salomax.neotool.mcp.security.McpAuthenticationFilter
import io.github.salomax.neotool.mcp.security.McpPrincipal
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

@Controller("/mcp")
class McpController(
    private val protocolService: McpProtocolService,
) {
    @Post
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    fun handle(
        @Body body: Map<String, Any?>,
        request: HttpRequest<*>,
    ): HttpResponse<JsonRpcResponse> {
        val principal = authenticatedPrincipal(request)
        val response = protocolService.handle(body, principal) ?: return HttpResponse.accepted()
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
}
