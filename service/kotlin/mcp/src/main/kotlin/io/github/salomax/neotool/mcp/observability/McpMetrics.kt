package io.github.salomax.neotool.mcp.observability

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import io.micronaut.core.annotation.Nullable
import jakarta.inject.Singleton

@Singleton
open class McpMetrics(
    @param:Nullable private val meterRegistry: MeterRegistry? = null,
) {
    open fun incrementRateLimitExceeded(scope: String) {
        val registry = meterRegistry ?: return
        Counter.builder(RATE_LIMIT_EXCEEDED_TOTAL)
            .description("Total number of MCP requests rejected by rate limiting")
            .tag("scope", normalizeTag(scope))
            .register(registry)
            .increment()
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
        private const val RATE_LIMIT_EXCEEDED_TOTAL = "mcp_rate_limit_exceeded_total"
        private const val UNKNOWN_TAG = "unknown"
        private const val MAX_TAG_LENGTH = 64
        private val INVALID_TAG_CHAR_REGEX = Regex("[^a-z0-9._-]")
    }
}
