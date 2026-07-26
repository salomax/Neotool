package io.github.salomax.neotool.security.observability

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import io.micronaut.core.annotation.Nullable
import jakarta.inject.Singleton

@Singleton
open class SecurityMetrics(
    @param:Nullable private val meterRegistry: MeterRegistry? = null,
) {
    open fun incrementOAuth2TokenRateLimitExceeded(clientId: String) {
        val registry = meterRegistry ?: return
        Counter.builder(OAUTH2_TOKEN_RATE_LIMIT_EXCEEDED_TOTAL)
            .description("Total number of OAuth2 token requests rejected by rate limiting")
            .tag("client_id", normalizeTag(clientId))
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
        private const val OAUTH2_TOKEN_RATE_LIMIT_EXCEEDED_TOTAL = "oauth2_token_rate_limit_exceeded_total"
        private const val UNKNOWN_TAG = "unknown"
        private const val MAX_TAG_LENGTH = 64
        private val INVALID_TAG_CHAR_REGEX = Regex("[^a-z0-9._-]")
    }
}
