package io.github.salomax.neotool.mcp.ratelimit

import io.micronaut.context.annotation.Requires
import io.micronaut.scheduling.annotation.Scheduled
import jakarta.inject.Singleton
import mu.KotlinLogging
import java.time.Clock
import java.time.Instant

@Singleton
@Requires(property = "mcp.rate-limit.cleanup.enabled", value = "true", defaultValue = "true")
@Requires(beans = [McpRateLimitCounterRepository::class])
open class McpRateLimitCounterCleanupJob(
    private val repository: McpRateLimitCounterRepository,
    private val config: McpRateLimitConfig,
) {
    private val logger = KotlinLogging.logger {}

    @Scheduled(fixedDelay = "\${mcp.rate-limit.cleanup.fixed-delay:PT1H}")
    open fun run() {
        val deleted = repository.deleteExpired(now())
        if (deleted > 0) {
            logger.info { "McpRateLimitCounterCleanupJob removed $deleted expired row(s)" }
        }
    }

    internal open fun now(): Instant = Instant.now(Clock.systemUTC())
}
