package io.github.salomax.neotool.mcp.observability

import io.github.salomax.neotool.mcp.test.McpJwtTestFixture
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import io.micronaut.test.support.TestPropertyProvider
import jakarta.inject.Inject
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@MicronautTest(startApplication = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
open class McpPrometheusEndpointTest : TestPropertyProvider {
    @Inject
    @field:Client("/")
    lateinit var client: HttpClient

    @Inject
    lateinit var metrics: McpMetrics

    override fun getProperties(): MutableMap<String, String> =
        mutableMapOf(
            "jwt.issuer" to McpJwtTestFixture.ISSUER,
            "jwt.public-key" to McpJwtTestFixture.publicKeyPem,
            "mcp.resource.audience" to McpJwtTestFixture.AUDIENCE,
            "mcp.resource.metadata-url" to "https://mcp.example.test/.well-known/oauth-protected-resource",
            "mcp.observability.allowed-client-ids[0]" to "test-agent",
            "micronaut.executors.scheduled.enabled" to "false",
            "mcp.rate-limit.cleanup.enabled" to "false",
            "mcp.revocation.eviction.enabled" to "false",
            "datasources.default.enabled" to "false",
            "flyway.datasources.default.enabled" to "false",
        )

    @Test
    fun `prometheus endpoint exports mcp metrics with scrapeable names`() {
        metrics.recordProtocolRequest(
            method = "tools/list",
            outcome = "success",
            latencyMs = 25,
            clientId = "test-agent",
        )
        metrics.recordToolsListSize(2)
        metrics.incrementAuthenticationFailure(
            cause = "INVALID_TOKEN",
            endpoint = "/mcp",
        )

        val response =
            client.toBlocking().exchange(
                HttpRequest.GET<Any>("/prometheus"),
                String::class.java,
            )

        assertThat(response.status).isEqualTo(HttpStatus.OK)
        val body = response.body()!!
        assertThat(body).contains("mcp_protocol_requests_total")
        assertThat(body).contains("client_id=\"test-agent\"")
        assertThat(body).contains("mcp_protocol_request_duration_seconds_bucket")
        assertThat(body).contains("mcp_tools_list_size_count")
        assertThat(body).doesNotContain("mcp_tools_list_size_tools_count")
        assertThat(body).contains("mcp_authentication_failures_total")
    }
}
