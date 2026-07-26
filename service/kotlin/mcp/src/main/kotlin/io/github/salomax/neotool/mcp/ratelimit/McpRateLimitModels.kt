package io.github.salomax.neotool.mcp.ratelimit

import java.time.Instant

enum class McpRateLimitScope {
    TOKEN,
    USER,
}

enum class McpToolClassification {
    READ,
    WRITE,
}

data class McpRateLimitResult(
    val allowed: Boolean,
    val scope: McpRateLimitScope?,
    val remaining: Long,
    val resetAt: Instant?,
)

data class McpRateLimitDecision(
    val scope: McpRateLimitScope,
    val retryAfterSeconds: Long,
    val resetAt: Instant,
)
