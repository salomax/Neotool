package io.github.salomax.neotool.mcp.security

import io.github.salomax.neotool.mcp.test.McpJwtTestFixture
import io.micronaut.http.HttpHeaders
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
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
import org.junit.jupiter.api.assertThrows
import java.time.Instant
import java.util.UUID

@MicronautTest(startApplication = true)
@DisplayName("MCP resource server HTTP rejection matrix")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
open class McpProbeControllerTest : TestPropertyProvider {
    @Inject
    @field:Client("/")
    lateinit var client: HttpClient

    override fun getProperties(): MutableMap<String, String> =
        mutableMapOf(
            "jwt.issuer" to McpJwtTestFixture.ISSUER,
            "jwt.public-key" to McpJwtTestFixture.publicKeyPem,
            "mcp.resource.audience" to McpJwtTestFixture.AUDIENCE,
            "mcp.resource.metadata-url" to "https://mcp.example.test/.well-known/oauth-protected-resource",
            "mcp.resource.metadata.cache-max-age-seconds" to "7200",
            "micronaut.executors.scheduled.enabled" to "false",
            "mcp.rate-limit.cleanup.enabled" to "false",
            "mcp.revocation.eviction.enabled" to "false",
            "datasources.default.enabled" to "false",
            "flyway.datasources.default.enabled" to "false",
        )

    @Test
    fun `rejects missing bearer token with 401 and WWW-Authenticate metadata`() {
        val exception =
            assertThrows<HttpClientResponseException> {
                client.toBlocking().exchange(HttpRequest.GET<Any>("/mcp/probe"), OAuthError::class.java)
            }

        assertUnauthorized(exception, "invalid_request")
    }

    @Test
    fun `rejects malformed token with 401`() {
        val exception =
            assertThrows<HttpClientResponseException> {
                client.toBlocking().exchange(probeRequest("not-a-jwt"), OAuthError::class.java)
            }

        assertUnauthorized(exception, "invalid_token")
    }

    @Test
    fun `rejects expired token with 401`() {
        val exception =
            assertThrows<HttpClientResponseException> {
                client
                    .toBlocking()
                    .exchange(
                        probeRequest(McpJwtTestFixture.expiredAccessToken()),
                        OAuthError::class.java,
                    )
            }

        assertUnauthorized(exception, "invalid_token")
    }

    @Test
    fun `rejects wrong audience with 401`() {
        val token = McpJwtTestFixture.accessToken(audience = "https://other.example.test")

        val exception =
            assertThrows<HttpClientResponseException> {
                client.toBlocking().exchange(probeRequest(token), OAuthError::class.java)
            }

        assertUnauthorized(exception, "invalid_token")
    }

    @Test
    fun `rejects absent audience with 401`() {
        val token = McpJwtTestFixture.accessToken(audience = null)

        val exception =
            assertThrows<HttpClientResponseException> {
                client.toBlocking().exchange(probeRequest(token), OAuthError::class.java)
            }

        assertUnauthorized(exception, "invalid_token")
    }

    @Test
    fun `rejects insufficient scope with 403`() {
        val token = McpJwtTestFixture.accessToken(scope = "finance:cashflow:read")

        val exception =
            assertThrows<HttpClientResponseException> {
                client.toBlocking().exchange(probeRequest(token), OAuthError::class.java)
            }

        assertThat(exception.status)
            .withFailMessage("body=%s", exception.response.getBody(String::class.java).orElse("<empty>"))
            .isEqualTo(HttpStatus.FORBIDDEN)
        assertThat(exception.response.getBody(OAuthError::class.java)).hasValueSatisfying {
            assertThat(it.error).isEqualTo("insufficient_scope")
        }
    }

    @Test
    fun `allows valid token with audience scope and live connection`() {
        val userId = UUID.randomUUID()
        val accountId = UUID.randomUUID()
        val token = McpJwtTestFixture.accessToken(userId = userId, accountId = accountId)

        val response =
            try {
                client.toBlocking().retrieve(probeRequest(token), Map::class.java)
            } catch (e: HttpClientResponseException) {
                throw AssertionError("body=${e.response.getBody(String::class.java).orElse("<empty>")}", e)
            }

        assertThat(response["authenticated"]).isEqualTo(true)
        assertThat(response["userId"]).isEqualTo(userId.toString())
        assertThat(response["currentAccountId"]).isEqualTo(accountId.toString())
    }

    @Test
    fun `allows token when resource audience is not first audience`() {
        val token =
            McpJwtTestFixture.accessToken(
                audience = "https://other.example.test",
                additionalAudiences = listOf(McpJwtTestFixture.AUDIENCE),
            )

        val response =
            try {
                client.toBlocking().retrieve(probeRequest(token), Map::class.java)
            } catch (e: HttpClientResponseException) {
                throw AssertionError("body=${e.response.getBody(String::class.java).orElse("<empty>")}", e)
            }

        assertThat(response["authenticated"]).isEqualTo(true)
    }

    @Test
    fun `filter protects new mcp endpoint without controller-level authentication`() {
        val exception =
            assertThrows<HttpClientResponseException> {
                client.toBlocking().exchange(HttpRequest.GET<Any>("/mcp/filter-only"), OAuthError::class.java)
            }

        assertUnauthorized(exception, "invalid_request")
    }

    @Test
    fun `filter stores authenticated principal for downstream mcp endpoint`() {
        val token = McpJwtTestFixture.accessToken()

        val response =
            try {
                client.toBlocking().retrieve(probeRequest(token, "/mcp/filter-only"), Map::class.java)
            } catch (e: HttpClientResponseException) {
                throw AssertionError("body=${e.response.getBody(String::class.java).orElse("<empty>")}", e)
            }

        assertThat(response["authenticated"]).isEqualTo(true)
    }

    @Test
    fun `protected resource metadata endpoint is public and returns configured discovery document`() {
        val response =
            try {
                client.toBlocking().exchange(
                    HttpRequest.GET<Any>("/.well-known/oauth-protected-resource"),
                    Map::class.java,
                )
            } catch (e: HttpClientResponseException) {
                throw AssertionError("body=${e.response.getBody(String::class.java).orElse("<empty>")}", e)
            }

        assertThat(response.status).isEqualTo(HttpStatus.OK)
        assertThat(response.header(HttpHeaders.CACHE_CONTROL)).isEqualTo("public, max-age=7200")
        val body = response.body()
        assertThat(body?.get("resource")).isEqualTo(McpJwtTestFixture.AUDIENCE)
        assertThat(body?.get("bearer_methods_supported")).isEqualTo(listOf("header"))
        assertThat(body?.get("authorization_servers"))
            .isEqualTo(listOf(McpJwtTestFixture.ISSUER))
    }

    @Test
    fun `mcp prefixed protected resource metadata path requires authentication after cleanup`() {
        val exception =
            assertThrows<HttpClientResponseException> {
                client.toBlocking().exchange(
                    HttpRequest.GET<Any>("/mcp/.well-known/oauth-protected-resource"),
                    OAuthError::class.java,
                )
            }

        assertUnauthorized(exception, "invalid_request")
    }

    private fun probeRequest(token: String): HttpRequest<Any> = probeRequest(token, "/mcp/probe")

    private fun probeRequest(
        token: String,
        path: String,
    ): HttpRequest<Any> = HttpRequest.GET<Any>(path).header(HttpHeaders.AUTHORIZATION, "Bearer $token")

    private fun assertUnauthorized(
        exception: HttpClientResponseException,
        error: String,
    ) {
        assertThat(exception.status)
            .withFailMessage("body=%s", exception.response.getBody(String::class.java).orElse("<empty>"))
            .isEqualTo(HttpStatus.UNAUTHORIZED)
        assertThat(exception.response.header(HttpHeaders.WWW_AUTHENTICATE))
            .contains("Bearer")
            .contains("resource_metadata=\"https://mcp.example.test/.well-known/oauth-protected-resource\"")
        assertThat(exception.response.getBody(OAuthError::class.java)).hasValueSatisfying {
            assertThat(it.error).isEqualTo(error)
        }
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
}

@Controller("/mcp/filter-only")
class FilterOnlyMcpController {
    @Get
    fun index(request: HttpRequest<*>): HttpResponse<Map<String, Boolean>> =
        HttpResponse.ok(
            mapOf(
                "authenticated" to
                    request
                        .getAttribute(McpAuthenticationFilter.PRINCIPAL_ATTRIBUTE, McpPrincipal::class.java)
                        .isPresent,
            ),
        )
}
