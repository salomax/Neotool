package io.github.salomax.neotool.mcp.ratelimit

import io.github.salomax.neotool.common.security.principal.RequestPrincipal
import io.github.salomax.neotool.mcp.observability.McpMetrics
import jakarta.inject.Singleton
import java.time.Clock
import java.time.Instant
import java.util.UUID
import kotlin.math.ceil

@Singleton
open class McpRateLimitService(
    private val repository: McpRateLimitCounterRepository,
    private val config: McpRateLimitConfig,
    private val metrics: McpMetrics,
) {
    open fun checkAndIncrement(
        principal: RequestPrincipal,
        classification: McpToolClassification,
    ): McpRateLimitResult {
        val tokenLimit =
            when (classification) {
                McpToolClassification.READ -> config.token.read
                McpToolClassification.WRITE -> config.token.write
            }
        val checks =
            listOf(
                ScopeCheck(McpRateLimitScope.TOKEN, parseUuid(principal.tokenId), tokenLimit),
                ScopeCheck(McpRateLimitScope.USER, requireNotNull(principal.userId), config.user),
            )

        var minRemaining = Long.MAX_VALUE
        var minResetAt: Instant? = null
        for (check in checks) {
            val result = checkAndIncrement(check.scope, check.scopeId, check.limit)
            if (!result.allowed) {
                metrics.incrementRateLimitExceeded(check.scope.name)
                return result
            }
            minRemaining = minOf(minRemaining, result.remaining)
            minResetAt =
                when {
                    minResetAt == null -> result.resetAt
                    result.resetAt != null && result.resetAt.isBefore(minResetAt) -> result.resetAt
                    else -> minResetAt
                }
        }

        return McpRateLimitResult(
            allowed = true,
            scope = null,
            remaining = minRemaining,
            resetAt = minResetAt,
        )
    }

    open fun rejectDecision(result: McpRateLimitResult): McpRateLimitDecision {
        require(!result.allowed) { "rejectDecision only applies to denied results" }
        val resetAt = requireNotNull(result.resetAt) { "Denied result must include resetAt" }
        val retryAfterSeconds =
            ceil((resetAt.toEpochMilli() - now().toEpochMilli()).coerceAtLeast(0L) / 1000.0)
                .toLong()
                .coerceAtLeast(0L)
        return McpRateLimitDecision(
            scope = requireNotNull(result.scope),
            retryAfterSeconds = retryAfterSeconds,
            resetAt = resetAt,
        )
    }

    open fun checkAndIncrement(
        scope: McpRateLimitScope,
        scopeId: UUID,
        limit: McpRateLimitConfig.ScopeLimitConfig,
    ): McpRateLimitResult {
        require(limit.windowMs > 0) { "windowMs must be greater than zero" }
        if (limit.limit <= 0) {
            return McpRateLimitResult(
                allowed = false,
                scope = scope,
                remaining = 0,
                resetAt = alignToWindow(now(), limit.windowMs).plusMillis(limit.windowMs),
            )
        }

        val now = now()
        val windowStart = alignToWindow(now, limit.windowMs)
        val resetAt = windowStart.plusMillis(limit.windowMs)

        repository.ensureCounterExists(
            scope = scope,
            scopeId = scopeId,
            windowStart = windowStart,
            windowSizeMs = limit.windowMs,
        )

        val updated =
            repository.incrementIfAllowed(
                scope = scope,
                scopeId = scopeId,
                windowStart = windowStart,
                windowSizeMs = limit.windowMs,
                requestLimit = limit.limit,
            )

        if (updated == null) {
            val currentCount =
                repository.findCounter(
                    scope = scope,
                    scopeId = scopeId,
                    windowStart = windowStart,
                    windowSizeMs = limit.windowMs,
                )?.requestCount ?: 0L
            return McpRateLimitResult(
                allowed = false,
                scope = scope,
                remaining = (limit.limit - currentCount).coerceAtLeast(0L),
                resetAt = resetAt,
            )
        }

        return McpRateLimitResult(
            allowed = true,
            scope = null,
            remaining = (limit.limit - updated.requestCount).coerceAtLeast(0L),
            resetAt = resetAt,
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

    private fun parseUuid(value: String): UUID =
        try {
            UUID.fromString(value)
        } catch (e: IllegalArgumentException) {
            throw IllegalStateException("Expected UUID token id but got '$value'")
        }

    private data class ScopeCheck(
        val scope: McpRateLimitScope,
        val scopeId: UUID,
        val limit: McpRateLimitConfig.ScopeLimitConfig,
    )
}
