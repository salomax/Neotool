package io.github.salomax.neotool.mcp.security

import io.github.salomax.neotool.common.security.config.JwtConfig
import io.github.salomax.neotool.common.security.jwt.JwtTokenValidator
import io.github.salomax.neotool.common.security.key.FileKeyManager
import io.github.salomax.neotool.mcp.test.McpJwtTestFixture
import io.micronaut.http.HttpHeaders
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.test.annotation.MockBean
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import io.micronaut.test.support.TestPropertyProvider
import jakarta.inject.Inject
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.net.ServerSocket
import java.time.Instant
import java.util.UUID

@MicronautTest(startApplication = true)
@DisplayName("MCP JWT validation through JWKS")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
open class McpJwksValidationTest : TestPropertyProvider {
    @Inject
    @field:Client("/")
    lateinit var client: HttpClient

    @Inject
    lateinit var jwtTokenValidator: JwtTokenValidator

    @Inject
    lateinit var jwtConfig: JwtConfig

    @Inject
    lateinit var fileKeyManager: FileKeyManager

    override fun getProperties(): MutableMap<String, String> =
        mutableMapOf(
            "micronaut.server.port" to JWKS_TEST_PORT.toString(),
            "jwt.issuer" to McpJwtTestFixture.ISSUER,
            "jwt.jwks-url" to "http://localhost:$JWKS_TEST_PORT/test-jwks.json",
            "mcp.resource.audience" to McpJwtTestFixture.AUDIENCE,
            "mcp.resource.metadata-url" to "https://mcp.example.test/.well-known/oauth-protected-resource",
            "datasources.default.enabled" to "false",
            "flyway.datasources.default.enabled" to "false",
        )

    @Test
    fun `validates access token by fetching MCP context JwksClient key`() {
        val token = McpJwtTestFixture.accessToken()
        assertThat(jwtConfig.jwksUrl).isEqualTo("http://localhost:$JWKS_TEST_PORT/test-jwks.json")
        val jwksBody = client.toBlocking().retrieve(HttpRequest.GET<Any>("/test-jwks.json"), String::class.java)
        assertThat(jwksBody).contains(McpJwtTestFixture.KEY_ID)
        assertThat(fileKeyManager.getPublicKey(McpJwtTestFixture.KEY_ID)).isNotNull()
        jwtTokenValidator.validateToken(token)

        val response =
            try {
                client
                    .toBlocking()
                    .retrieve(
                        HttpRequest
                            .GET<Any>("/mcp/probe")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer $token"),
                        Map::class.java,
                    )
            } catch (e: HttpClientResponseException) {
                throw AssertionError("body=${e.response.getBody(String::class.java).orElse("<empty>")}", e)
            }

        assertThat(response["authenticated"]).isEqualTo(true)
    }

    @MockBean(ConnectionRevocationStore::class)
    fun connectionRevocationStore(): ConnectionRevocationStore =
        object : ConnectionRevocationStore {
            override fun isRevoked(
                userId: UUID,
                clientId: String,
                accountId: UUID,
            ): Boolean = false

            override fun deleteExpired(now: Instant): Long = 0
        }

    companion object {
        private val JWKS_TEST_PORT: Int =
            ServerSocket(0).use { socket ->
                socket.localPort
            }
    }
}

@Controller("/test-jwks.json")
class TestJwksController {
    @Get
    fun jwks(): HttpResponse<McpJwtTestFixture.JwksResponse> = HttpResponse.ok(McpJwtTestFixture.jwks())
}
