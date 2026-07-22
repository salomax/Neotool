package io.github.salomax.neotool.mcp.security

import io.micronaut.scheduling.annotation.Scheduled
import jakarta.inject.Singleton
import mu.KotlinLogging
import java.time.Instant

@Singleton
open class ConnectionRevocationEvictionJob(
    private val revocationStore: ConnectionRevocationStore,
) {
    private val logger = KotlinLogging.logger {}

    @Scheduled(fixedDelay = "\${mcp.revocation.eviction.fixed-delay:PT1H}")
    fun evictExpiredRevocations() {
        val deleted = revocationStore.deleteExpired(Instant.now())
        if (deleted > 0) {
            logger.info { "Evicted $deleted expired MCP connection revocation rows" }
        }
    }
}
