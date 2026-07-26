package io.github.salomax.neotool.mcp.ratelimit

import io.github.salomax.neotool.common.security.principal.PrincipalType
import io.github.salomax.neotool.common.security.principal.RequestPrincipal
import io.github.salomax.neotool.mcp.observability.McpMetrics
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Instant
import java.util.UUID

class McpRateLimitServiceTest {
    private val config =
        McpRateLimitConfig(
            token =
                McpRateLimitConfig.TokenConfig(
                    read =
                        McpRateLimitConfig.ReadScopeLimitConfig().apply {
                            limit = 60
                            windowMs = 60_000
                        },
                    write =
                        McpRateLimitConfig.WriteScopeLimitConfig().apply {
                            limit = 15
                            windowMs = 60_000
                        },
                ),
            user =
                McpRateLimitConfig.UserScopeLimitConfig().apply {
                    limit = 120
                    windowMs = 60_000
                },
        )

    @Test
    fun `read tool allows 60 per token then denies the 61st`() {
        val repository = fakeRepository()
        val metrics = mock<McpMetrics>()
        val service = testService(repository, metrics)
        val principal = principal(tokenId = UUID.randomUUID(), userId = UUID.randomUUID())

        repeat(60) {
            assertThat(service.checkAndIncrement(principal, McpToolClassification.READ).allowed).isTrue()
        }

        val denied = service.checkAndIncrement(principal, McpToolClassification.READ)

        assertThat(denied.allowed).isFalse()
        assertThat(denied.scope).isEqualTo(McpRateLimitScope.TOKEN)
        verify(metrics).incrementRateLimitExceeded("TOKEN")
    }

    @Test
    fun `write tool allows 15 per token then denies the 16th`() {
        val repository = fakeRepository()
        val service = testService(repository, mock())
        val principal = principal(tokenId = UUID.randomUUID(), userId = UUID.randomUUID())

        repeat(15) {
            assertThat(service.checkAndIncrement(principal, McpToolClassification.WRITE).allowed).isTrue()
        }

        val denied = service.checkAndIncrement(principal, McpToolClassification.WRITE)

        assertThat(denied.allowed).isFalse()
        assertThat(denied.scope).isEqualTo(McpRateLimitScope.TOKEN)
    }

    @Test
    fun `two tokens under the same user hit the shared user ceiling`() {
        val repository = fakeRepository()
        val service =
            object : McpRateLimitService(
                repository = repository,
                config =
                    McpRateLimitConfig(
                        token =
                            McpRateLimitConfig.TokenConfig(
                                read =
                                    McpRateLimitConfig.ReadScopeLimitConfig().apply {
                                        limit = 10
                                        windowMs = 60_000
                                    },
                                write =
                                    McpRateLimitConfig.WriteScopeLimitConfig().apply {
                                        limit = 10
                                        windowMs = 60_000
                                    },
                            ),
                        user =
                            McpRateLimitConfig.UserScopeLimitConfig().apply {
                                limit = 3
                                windowMs = 60_000
                            },
                    ),
                metrics = mock(),
            ) {
                override fun now(): Instant = Instant.parse("2026-07-26T12:00:00Z")
            }
        val userId = UUID.randomUUID()
        val first = principal(tokenId = UUID.randomUUID(), userId = userId)
        val second = principal(tokenId = UUID.randomUUID(), userId = userId)

        assertThat(service.checkAndIncrement(first, McpToolClassification.READ).allowed).isTrue()
        assertThat(service.checkAndIncrement(second, McpToolClassification.READ).allowed).isTrue()
        assertThat(service.checkAndIncrement(first, McpToolClassification.READ).allowed).isTrue()

        val denied = service.checkAndIncrement(second, McpToolClassification.READ)

        assertThat(denied.allowed).isFalse()
        assertThat(denied.scope).isEqualTo(McpRateLimitScope.USER)
    }

    @Test
    fun `reject decision rounds retry after up to the failing window`() {
        val repository = fakeRepository()
        val service = testService(repository, mock(), Instant.parse("2026-07-26T12:00:00.250Z"))
        val result =
            McpRateLimitResult(
                allowed = false,
                scope = McpRateLimitScope.TOKEN,
                remaining = 0,
                resetAt = Instant.parse("2026-07-26T12:00:10Z"),
            )

        val decision = service.rejectDecision(result)

        assertThat(decision.retryAfterSeconds).isEqualTo(10)
        verify(mock<McpMetrics>(), never()).incrementRateLimitExceeded(any())
    }

    private fun principal(
        tokenId: UUID,
        userId: UUID,
    ): RequestPrincipal =
        RequestPrincipal(
            principalType = PrincipalType.USER,
            userId = userId,
            serviceId = null,
            token = "token",
            tokenId = tokenId.toString(),
            clientId = "test-agent",
            permissionsFromToken = emptyList(),
            currentAccountId = UUID.randomUUID(),
        )

    private fun testService(
        repository: McpRateLimitCounterRepository,
        metrics: McpMetrics,
        now: Instant = Instant.parse("2026-07-26T12:00:00Z"),
    ): McpRateLimitService =
        object : McpRateLimitService(repository, config, metrics) {
            override fun now(): Instant = now
        }

    private fun fakeRepository(): McpRateLimitCounterRepository {
        val repository = mock<McpRateLimitCounterRepository>()
        val counters = mutableMapOf<Triple<McpRateLimitScope, UUID, Instant>, Long>()
        whenever(repository.ensureCounterExists(any(), any(), any(), any())).thenAnswer {
            val scope = it.getArgument<McpRateLimitScope>(0)
            val scopeId = it.getArgument<UUID>(1)
            val windowStart = it.getArgument<Instant>(2)
            counters.putIfAbsent(Triple(scope, scopeId, windowStart), 0L)
            Unit
        }
        whenever(repository.incrementIfAllowed(any(), any(), any(), any(), any())).thenAnswer {
            val scope = it.getArgument<McpRateLimitScope>(0)
            val scopeId = it.getArgument<UUID>(1)
            val windowStart = it.getArgument<Instant>(2)
            val requestLimit = it.getArgument<Long>(4)
            val key = Triple(scope, scopeId, windowStart)
            val current = counters.getOrDefault(key, 0L)
            if (current + 1 > requestLimit) {
                null
            } else {
                counters[key] = current + 1
                McpRateLimitCounterSnapshot(current + 1)
            }
        }
        whenever(repository.findCounter(any(), any(), any(), any())).thenAnswer {
            val scope = it.getArgument<McpRateLimitScope>(0)
            val scopeId = it.getArgument<UUID>(1)
            val windowStart = it.getArgument<Instant>(2)
            counters[Triple(scope, scopeId, windowStart)]?.let(::McpRateLimitCounterSnapshot)
        }
        return repository
    }
}
