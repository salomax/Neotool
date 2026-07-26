package io.github.salomax.neotool.mcp.ratelimit

import io.github.salomax.neotool.common.test.integration.BaseIntegrationTest
import io.github.salomax.neotool.common.test.integration.PostgresIntegrationTest
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import io.micronaut.test.support.TestPropertyProvider
import jakarta.inject.Inject
import jakarta.persistence.EntityManager
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.sql.DriverManager
import java.time.Instant
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.Executors

@MicronautTest(startApplication = true, transactional = false)
@DisplayName("MCP rate-limit counter repository integration")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Tag("integration")
@Tag("mcp")
open class McpRateLimitCounterRepositoryIntegrationTest :
    BaseIntegrationTest(),
    PostgresIntegrationTest,
    TestPropertyProvider {
    @Inject
    lateinit var repository: McpRateLimitCounterRepository

    @Inject
    lateinit var entityManager: EntityManager

    override fun getProperties(): MutableMap<String, String> =
        super.getProperties().apply {
            put("mcp.rate-limit.cleanup.enabled", "false")
            put("mcp.revocation.eviction.enabled", "false")
        }

    @BeforeEach
    fun truncateCounters() {
        val container = getPgContainer()
        DriverManager.getConnection(container.jdbcUrl, container.username, container.password).use { conn ->
            conn.createStatement().use { stmt ->
                stmt.execute("TRUNCATE TABLE mcp.rate_limit_counters")
            }
        }
    }

    @Test
    fun `incrementIfAllowed returns null once the limit is exceeded`() {
        val scopeId = UUID.randomUUID()
        val windowStart = Instant.parse("2026-07-26T12:00:00Z")
        repository.ensureCounterExists(McpRateLimitScope.TOKEN, scopeId, windowStart, 60_000)

        val first =
            repository.incrementIfAllowed(
                scope = McpRateLimitScope.TOKEN,
                scopeId = scopeId,
                windowStart = windowStart,
                windowSizeMs = 60_000,
                requestLimit = 1,
            )
        val second =
            repository.incrementIfAllowed(
                scope = McpRateLimitScope.TOKEN,
                scopeId = scopeId,
                windowStart = windowStart,
                windowSizeMs = 60_000,
                requestLimit = 1,
            )

        assertThat(first?.requestCount).isEqualTo(1)
        assertThat(second).isNull()
    }

    @Test
    fun `concurrent incrementIfAllowed never allows more than the request limit`() {
        val scopeId = UUID.randomUUID()
        val windowStart = Instant.parse("2026-07-26T12:00:00Z")
        val requestLimit = 5L
        repository.ensureCounterExists(McpRateLimitScope.USER, scopeId, windowStart, 60_000)

        val executor = Executors.newFixedThreadPool(8)
        try {
            val tasks =
                List(20) {
                    Callable {
                        repository.incrementIfAllowed(
                            scope = McpRateLimitScope.USER,
                            scopeId = scopeId,
                            windowStart = windowStart,
                            windowSizeMs = 60_000,
                            requestLimit = requestLimit,
                        ) != null
                    }
                }
            val allowed = executor.invokeAll(tasks).count { it.get() }

            assertThat(allowed).isEqualTo(requestLimit.toInt())
            assertThat(
                repository.findCounter(
                    scope = McpRateLimitScope.USER,
                    scopeId = scopeId,
                    windowStart = windowStart,
                    windowSizeMs = 60_000,
                )?.requestCount,
            ).isEqualTo(requestLimit)
        } finally {
            executor.shutdownNow()
        }
    }
}
