package io.github.salomax.neotool.mcp.observability

import io.github.salomax.neotool.mcp.security.McpAuthenticationFailureCause
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class McpMetricsTest {
    @Test
    fun `records protocol counter and timer with expected tags`() {
        val meterRegistry = SimpleMeterRegistry()
        val metrics =
            McpMetrics(
                meterRegistry = meterRegistry,
                config = McpMetricsConfig(allowedClientIds = listOf("test-agent")),
            )

        metrics.recordProtocolRequest(
            method = "tools/list",
            outcome = "success",
            latencyMs = 125,
            clientId = "test-agent",
        )

        val counter =
            meterRegistry
                .find(McpMetrics.PROTOCOL_REQUESTS_TOTAL)
                .tags("method", "tools_list", "outcome", "success", "client_id", "test-agent")
                .counter()
        val timer =
            meterRegistry
                .find(McpMetrics.PROTOCOL_REQUEST_DURATION_SECONDS)
                .tags("method", "tools_list", "outcome", "success")
                .timer()

        assertThat(counter).isNotNull
        assertThat(counter!!.count()).isEqualTo(1.0)
        assertThat(timer).isNotNull
        assertThat(timer!!.count()).isEqualTo(1)
        assertThat(timer.totalTime(java.util.concurrent.TimeUnit.MILLISECONDS)).isEqualTo(125.0)
    }

    @Test
    fun `maps non allowlisted protocol client ids to other`() {
        val meterRegistry = SimpleMeterRegistry()
        val metrics =
            McpMetrics(
                meterRegistry = meterRegistry,
                config = McpMetricsConfig(allowedClientIds = listOf("trusted-client")),
            )

        metrics.recordProtocolRequest(
            method = "initialize",
            outcome = "success",
            latencyMs = 10,
            clientId = "new-dcr-client",
        )

        val counter =
            meterRegistry
                .find(McpMetrics.PROTOCOL_REQUESTS_TOTAL)
                .tags("method", "initialize", "outcome", "success", "client_id", "other")
                .counter()

        assertThat(counter).isNotNull
        assertThat(counter!!.count()).isEqualTo(1.0)
    }

    @Test
    fun `records tools list size summary`() {
        val meterRegistry = SimpleMeterRegistry()
        val metrics = McpMetrics(meterRegistry = meterRegistry)

        metrics.recordToolsListSize(3)

        val summary = meterRegistry.find(McpMetrics.TOOLS_LIST_SIZE).summary()

        assertThat(summary).isNotNull
        assertThat(summary!!.count()).isEqualTo(1L)
        assertThat(summary.totalAmount()).isEqualTo(3.0)
    }

    @Test
    fun `records authentication failures without client id label for each cause`() {
        val meterRegistry = SimpleMeterRegistry()
        val metrics = McpMetrics(meterRegistry = meterRegistry)

        McpAuthenticationFailureCause.entries.forEach { cause ->
            metrics.incrementAuthenticationFailure(
                cause = cause.name,
                endpoint = "/mcp/events",
            )
        }

        val authenticationFailureMeters =
            meterRegistry
                .find(McpMetrics.AUTHENTICATION_FAILURES_TOTAL)
                .meters()

        assertThat(authenticationFailureMeters).hasSize(McpAuthenticationFailureCause.entries.size)
        authenticationFailureMeters.forEach { meter ->
            assertThat(meter.id.tags.map { it.key }).doesNotContain("client_id")
        }
    }

    @Test
    fun `records tool catalog fetch failures`() {
        val meterRegistry = SimpleMeterRegistry()
        val metrics = McpMetrics(meterRegistry = meterRegistry)

        metrics.incrementToolCatalogFetchFailure()

        val counter = meterRegistry.find(McpMetrics.TOOL_CATALOG_FETCH_FAILURES_TOTAL).counter()

        assertThat(counter).isNotNull
        assertThat(counter!!.count()).isEqualTo(1.0)
    }

    @Test
    fun `is a no op when meter registry is absent`() {
        val metrics = McpMetrics()

        metrics.incrementRateLimitExceeded("token.read")
        metrics.recordProtocolRequest(
            method = "tools/list",
            outcome = "success",
            latencyMs = 5,
            clientId = "test-agent",
        )
        metrics.recordToolsListSize(1)
        metrics.incrementAuthenticationFailure(
            cause = McpAuthenticationFailureCause.INVALID_TOKEN.name,
            endpoint = "/mcp",
        )
        metrics.incrementToolCatalogFetchFailure()
    }
}
