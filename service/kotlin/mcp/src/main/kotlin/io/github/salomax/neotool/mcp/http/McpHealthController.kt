package io.github.salomax.neotool.mcp.http

import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Produces
import io.micronaut.serde.annotation.Serdeable

@Controller("/mcp")
class McpHealthController {
    @Get("/ready")
    @Produces(MediaType.APPLICATION_JSON)
    fun ready(): McpReadinessResponse = McpReadinessResponse(status = "UP")
}

@Serdeable
data class McpReadinessResponse(
    val status: String,
)
