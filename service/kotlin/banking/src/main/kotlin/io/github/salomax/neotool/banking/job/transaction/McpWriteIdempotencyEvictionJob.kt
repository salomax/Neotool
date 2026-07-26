package io.github.salomax.neotool.banking.job.transaction

import io.github.salomax.neotool.banking.repo.transaction.McpWriteIdempotencyRepository
import io.micronaut.context.annotation.Requires
import io.micronaut.context.annotation.Value
import io.micronaut.scheduling.annotation.Scheduled
import jakarta.inject.Singleton
import mu.KotlinLogging
import java.time.Duration
import java.time.Instant

@Singleton
@Requires(property = "banking.mcp-write-idempotency.eviction.enabled", notEquals = "false", defaultValue = "true")
open class McpWriteIdempotencyEvictionJob(
    private val repository: McpWriteIdempotencyRepository,
    @param:Value("\${banking.mcp-write-idempotency.retention:PT48H}")
    private val retention: Duration,
    private val clock: () -> Instant = { Instant.now() },
) {
    private val logger = KotlinLogging.logger {}

    @Scheduled(fixedDelay = "\${banking.mcp-write-idempotency.eviction.fixed-delay:PT1H}")
    open fun run() {
        val deleted = repository.deleteCreatedBefore(clock().minus(retention))
        if (deleted > 0) {
            logger.info { "McpWriteIdempotencyEvictionJob removed $deleted expired row(s)" }
        }
    }
}
