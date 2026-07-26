package io.github.salomax.neotool.mcp.ratelimit

import org.junit.jupiter.api.Test
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import java.time.Instant

class McpRateLimitCounterCleanupJobTest {
    @Test
    fun `cleanup job deletes rows older than the current instant`() {
        val repository = mock<McpRateLimitCounterRepository>()
        val job =
            object : McpRateLimitCounterCleanupJob(repository, McpRateLimitConfig()) {
                override fun now(): Instant = Instant.parse("2026-07-26T12:00:00Z")
            }

        job.run()

        verify(repository).deleteExpired(eq(Instant.parse("2026-07-26T12:00:00Z")))
    }
}
