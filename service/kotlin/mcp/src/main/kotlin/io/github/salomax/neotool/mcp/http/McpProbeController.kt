package io.github.salomax.neotool.mcp.http

import io.github.salomax.neotool.mcp.security.McpAuthenticationFilter
import io.github.salomax.neotool.mcp.security.McpPrincipal
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Produces
import io.micronaut.serde.annotation.Serdeable

@Controller("/mcp")
class McpProbeController {
    @Get("/probe")
    @Produces(MediaType.APPLICATION_JSON)
    fun probe(request: HttpRequest<*>): HttpResponse<Any> {
        val principal =
            request.getAttribute(McpAuthenticationFilter.PRINCIPAL_ATTRIBUTE, McpPrincipal::class.java).orElseThrow {
                IllegalStateException("MCP principal missing from authenticated request")
            }
        return HttpResponse.ok(
            McpProbeResponse(
                authenticated = true,
                userId = principal.principal.userId.toString(),
                clientId = principal.clientId,
                currentAccountId = principal.principal.currentAccountId.toString(),
            ),
        )
    }
}

@Serdeable
data class McpProbeResponse(
    val authenticated: Boolean,
    val userId: String,
    val clientId: String,
    val currentAccountId: String,
)
