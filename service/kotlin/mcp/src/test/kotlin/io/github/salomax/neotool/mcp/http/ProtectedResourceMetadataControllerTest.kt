package io.github.salomax.neotool.mcp.http

import io.github.salomax.neotool.common.security.config.JwtConfig
import io.github.salomax.neotool.mcp.security.McpResourceConfig
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ProtectedResourceMetadataControllerTest {
    @Test
    fun `returns the RFC 9728 discovery document with cache control`() {
        val controller =
            ProtectedResourceMetadataController(
                resourceConfig =
                    McpResourceConfig(
                        audience = "https://mcp.example.test",
                        metadataUrl = "https://mcp.example.test/.well-known/oauth-protected-resource",
                        metadata = McpResourceConfig.MetadataConfig(cacheMaxAgeSeconds = 7200),
                    ),
                jwtConfig = JwtConfig(issuer = "https://auth.example.test"),
            )

        val response = controller.getProtectedResourceMetadata()

        assertThat(response.header("Cache-Control")).isEqualTo("public, max-age=7200")
        assertThat(response.body()).isEqualTo(
            ProtectedResourceMetadataController.ProtectedResourceMetadata(
                resource = "https://mcp.example.test",
                authorization_servers = listOf("https://auth.example.test"),
                bearer_methods_supported = listOf("header"),
            ),
        )
    }

    @Test
    fun `rejects non-positive cache max age`() {
        val exception =
            assertThrows<IllegalArgumentException> {
                McpResourceConfig.MetadataConfig(cacheMaxAgeSeconds = 0).validate()
            }

        assertThat(exception).hasMessage("mcp.resource.metadata.cache-max-age-seconds must be positive")
    }
}
