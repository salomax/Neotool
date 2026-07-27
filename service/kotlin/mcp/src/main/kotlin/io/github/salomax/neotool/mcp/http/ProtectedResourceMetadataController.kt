package io.github.salomax.neotool.mcp.http

import io.github.salomax.neotool.common.security.config.JwtConfig
import io.github.salomax.neotool.mcp.security.McpResourceConfig
import io.micronaut.http.HttpResponse
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Produces
import io.micronaut.serde.annotation.Serdeable
import jakarta.inject.Singleton

@Singleton
@Controller("/.well-known")
class ProtectedResourceMetadataController(
    private val resourceConfig: McpResourceConfig,
    private val jwtConfig: JwtConfig,
) {
    @Serdeable
    data class ProtectedResourceMetadata(
        val resource: String,
        val authorization_servers: List<String>,
        val bearer_methods_supported: List<String>,
    )

    @Get("/oauth-protected-resource")
    @Produces(MediaType.APPLICATION_JSON)
    fun getProtectedResourceMetadata(): HttpResponse<ProtectedResourceMetadata> {
        val response =
            ProtectedResourceMetadata(
                resource = resourceConfig.audience,
                authorization_servers = listOf(jwtConfig.issuer),
                bearer_methods_supported = listOf("header"),
            )

        return HttpResponse
            .ok(response)
            .header("Cache-Control", "public, max-age=${resourceConfig.metadata.cacheMaxAgeSeconds}")
    }
}
