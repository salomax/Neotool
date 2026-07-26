package io.github.salomax.neotool.security.service.oauth2

import io.github.salomax.neotool.security.observability.SecurityMetrics
import jakarta.inject.Singleton
import java.time.Clock
import java.time.Instant
import kotlin.math.ceil

@Singleton
open class OAuth2TokenRateLimitService(
    private val repository: OAuth2TokenRateLimitCounterRepository,
    private val config: OAuth2TokenRateLimitConfig,
    private val metrics: SecurityMetrics,
) {
    open fun check(clientId: String): OAuth2TokenRateLimitDecision {
        require(config.windowMs > 0) { "windowMs must be greater than zero" }
        require(config.limit > 0) { "limit must be greater than zero" }

        val normalizedClientId = clientId.trim().takeIf { it.isNotEmpty() }
            ?: throw OAuth2InvalidRequestException("client_id is required")
        val now = now()
        val windowStart = alignToWindow(now, config.windowMs)
        val resetAt = windowStart.plusMillis(config.windowMs)
        repository.ensureCounterExists(normalizedClientId, windowStart, config.windowMs)
        val updated =
            repository.incrementIfAllowed(
                scopeId = normalizedClientId,
                windowStart = windowStart,
                windowSizeMs = config.windowMs,
                requestLimit = config.limit,
            )

        if (updated == null) {
            metrics.incrementOAuth2TokenRateLimitExceeded(normalizedClientId)
            val currentCount =
                repository.findCounter(
                    scopeId = normalizedClientId,
                    windowStart = windowStart,
                    windowSizeMs = config.windowMs,
                )?.requestCount ?: 0L
            val retryAfterSeconds =
                ceil((resetAt.toEpochMilli() - now.toEpochMilli()).coerceAtLeast(0L) / 1000.0)
                    .toLong()
                    .coerceAtLeast(0L)
            return OAuth2TokenRateLimitDecision(
                allowed = false,
                remaining = (config.limit - currentCount).coerceAtLeast(0L),
                resetAt = resetAt,
                retryAfterSeconds = retryAfterSeconds,
            )
        }

        return OAuth2TokenRateLimitDecision(
            allowed = true,
            remaining = (config.limit - updated.requestCount).coerceAtLeast(0L),
            resetAt = resetAt,
            retryAfterSeconds = 0,
        )
    }

    internal open fun now(): Instant = Instant.now(Clock.systemUTC())

    private fun alignToWindow(
        instant: Instant,
        windowSizeMs: Long,
    ): Instant {
        val epochMs = instant.toEpochMilli()
        val windowStartMs = (epochMs / windowSizeMs) * windowSizeMs
        return Instant.ofEpochMilli(windowStartMs)
    }
}

data class OAuth2TokenRateLimitDecision(
    val allowed: Boolean,
    val remaining: Long,
    val resetAt: Instant,
    val retryAfterSeconds: Long,
)
