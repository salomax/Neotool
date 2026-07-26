package io.github.salomax.neotool.security.test.service.unit.oauth2

import io.github.salomax.neotool.security.observability.SecurityMetrics
import io.github.salomax.neotool.security.service.oauth2.OAuth2TokenRateLimitConfig
import io.github.salomax.neotool.security.service.oauth2.OAuth2TokenRateLimitCounterRepository
import io.github.salomax.neotool.security.service.oauth2.OAuth2TokenRateLimitService
import io.github.salomax.neotool.security.service.oauth2.OAuth2TokenRateLimitSnapshot
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.Instant

class OAuth2TokenRateLimitServiceTest {
    @Test
    fun `120 requests per client succeed and the next one is denied with retry after`() {
        val repository = fakeRepository()
        val service =
            object : OAuth2TokenRateLimitService(
                repository = repository,
                config = OAuth2TokenRateLimitConfig(limit = 120, windowMs = 3_600_000),
                metrics = mock<SecurityMetrics>(),
            ) {
                override fun now(): Instant = Instant.parse("2026-07-26T12:00:00Z")
            }

        repeat(120) {
            assertThat(service.check("test-client").allowed).isTrue()
        }

        val denied = service.check("test-client")

        assertThat(denied.allowed).isFalse()
        assertThat(denied.retryAfterSeconds).isPositive()
    }

    private fun fakeRepository(): OAuth2TokenRateLimitCounterRepository {
        val repository = mock<OAuth2TokenRateLimitCounterRepository>()
        val counters = mutableMapOf<Pair<String, Instant>, Long>()
        whenever(repository.ensureCounterExists(any(), any(), any())).thenAnswer {
            val scopeId = it.getArgument<String>(0)
            val windowStart = it.getArgument<Instant>(1)
            counters.putIfAbsent(scopeId to windowStart, 0L)
            Unit
        }
        whenever(repository.incrementIfAllowed(any(), any(), any(), any())).thenAnswer {
            val scopeId = it.getArgument<String>(0)
            val windowStart = it.getArgument<Instant>(1)
            val requestLimit = it.getArgument<Long>(3)
            val key = scopeId to windowStart
            val current = counters.getOrDefault(key, 0L)
            if (current + 1 > requestLimit) {
                null
            } else {
                counters[key] = current + 1
                OAuth2TokenRateLimitSnapshot(current + 1)
            }
        }
        whenever(repository.findCounter(any(), any(), any())).thenAnswer {
            val scopeId = it.getArgument<String>(0)
            val windowStart = it.getArgument<Instant>(1)
            counters[scopeId to windowStart]?.let(::OAuth2TokenRateLimitSnapshot)
        }
        return repository
    }
}
