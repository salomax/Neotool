package io.github.salomax.neotool.mcp.observability

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.DistributionSummary
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import io.micronaut.core.annotation.Nullable
import jakarta.inject.Singleton
import java.util.concurrent.TimeUnit

@Singleton
open class McpMetrics(
    @param:Nullable private val meterRegistry: MeterRegistry? = null,
    private val config: McpMetricsConfig = McpMetricsConfig(),
) {
    private val allowedClientIdTags =
        config.allowedClientIds
            .map(::normalizeTag)
            .filter { it != UNKNOWN_TAG }
            .toSet()

    open fun incrementRateLimitExceeded(scope: String) {
        val registry = meterRegistry ?: return
        Counter.builder(RATE_LIMIT_EXCEEDED_TOTAL)
            .description("Total number of MCP requests rejected by rate limiting")
            .tag("scope", normalizeTag(scope))
            .register(registry)
            .increment()
    }

    open fun recordProtocolRequest(
        method: String,
        outcome: String,
        latencyMs: Long,
        clientId: String?,
    ) {
        val registry = meterRegistry ?: return
        Counter.builder(PROTOCOL_REQUESTS_TOTAL)
            .description("Total number of MCP protocol requests processed after authentication")
            .tag("method", normalizeTag(method))
            .tag("outcome", normalizeTag(outcome))
            .tag("client_id", normalizeClientIdTag(clientId))
            .register(registry)
            .increment()

        Timer.builder(PROTOCOL_REQUEST_DURATION_SECONDS)
            .description("Duration of authenticated MCP protocol request handling")
            .tag("method", normalizeTag(method))
            .tag("outcome", normalizeTag(outcome))
            .publishPercentileHistogram()
            .register(registry)
            .record(latencyMs, TimeUnit.MILLISECONDS)
    }

    open fun recordToolsListSize(toolCount: Int) {
        val registry = meterRegistry ?: return
        DistributionSummary.builder(TOOLS_LIST_SIZE)
            .description("Number of tools returned by MCP tools/list responses")
            .register(registry)
            .record(toolCount.toDouble())
    }

    open fun incrementAuthenticationFailure(
        cause: String,
        endpoint: String,
    ) {
        val registry = meterRegistry ?: return
        Counter.builder(AUTHENTICATION_FAILURES_TOTAL)
            .description("Total number of MCP authentication failures before protocol handling")
            .tag("cause", normalizeTag(cause))
            .tag("endpoint", normalizeTag(endpoint))
            .register(registry)
            .increment()
    }

    open fun incrementToolCatalogFetchFailure() {
        val registry = meterRegistry ?: return
        Counter.builder(TOOL_CATALOG_FETCH_FAILURES_TOTAL)
            .description("Total number of MCP tool catalog fetch failures")
            .register(registry)
            .increment()
    }

    private fun normalizeClientIdTag(clientId: String?): String {
        val normalized = clientId?.let(::normalizeTag) ?: UNKNOWN_TAG
        if (normalized == UNKNOWN_TAG) {
            return UNKNOWN_TAG
        }

        return if (normalized in allowedClientIdTags) normalized else OTHER_CLIENT_ID_TAG
    }

    private fun normalizeTag(value: String): String {
        val normalized =
            value.trim()
                .lowercase()
                .replace(INVALID_TAG_CHAR_REGEX, "_")
                .trim('_')
                .take(MAX_TAG_LENGTH)

        return normalized.ifBlank { UNKNOWN_TAG }
    }

    companion object {
        const val RATE_LIMIT_EXCEEDED_TOTAL = "mcp_rate_limit_exceeded_total"
        const val PROTOCOL_REQUESTS_TOTAL = "mcp_protocol_requests_total"
        const val PROTOCOL_REQUEST_DURATION_SECONDS = "mcp_protocol_request_duration_seconds"
        const val TOOLS_LIST_SIZE = "mcp_tools_list_size"
        const val AUTHENTICATION_FAILURES_TOTAL = "mcp_authentication_failures_total"
        const val TOOL_CATALOG_FETCH_FAILURES_TOTAL = "mcp_tool_catalog_fetch_failures_total"
        private const val UNKNOWN_TAG = "unknown"
        private const val OTHER_CLIENT_ID_TAG = "other"
        private const val MAX_TAG_LENGTH = 64
        private val INVALID_TAG_CHAR_REGEX = Regex("[^a-z0-9._-]")
    }
}
