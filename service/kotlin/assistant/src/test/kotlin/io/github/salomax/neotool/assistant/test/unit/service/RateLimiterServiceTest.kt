package io.github.salomax.neotool.assistant.test.unit.service

import io.github.salomax.neotool.assistant.domain.enums.BudgetPeriod
import io.github.salomax.neotool.assistant.domain.enums.BudgetScope
import io.github.salomax.neotool.assistant.entity.UsageBudgetEntity
import io.github.salomax.neotool.assistant.repository.RateLimitCounterRepository
import io.github.salomax.neotool.assistant.repository.RateLimitCounterSnapshot
import io.github.salomax.neotool.assistant.repository.UsageBudgetRepository
import io.github.salomax.neotool.assistant.service.RateLimiterService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

class RateLimiterServiceTest {
    private lateinit var usageBudgetRepository: UsageBudgetRepository
    private lateinit var rateLimitCounterRepository: RateLimitCounterRepository
    private lateinit var service: RateLimiterService

    @BeforeEach
    fun setUp() {
        usageBudgetRepository = mock()
        rateLimitCounterRepository = mock()
        service = RateLimiterService(usageBudgetRepository, rateLimitCounterRepository)

        whenever(usageBudgetRepository.findByScopeAndScopeId(any(), any())).thenReturn(emptyList())
    }

    @Test
    fun `checkAndIncrement allows request when no budgets exist`() {
        val result =
            service.checkAndIncrement(
                providerScopeId = UUID.randomUUID(),
                accountScopeId = UUID.randomUUID(),
                service = "assistant-api",
                feature = "chat",
                estimatedTokens = 120,
            )

        assertThat(result.allowed).isTrue()
        assertThat(result.scope).isNull()
    }

    @Test
    fun `checkAndIncrement denies when provider scope budget is exceeded`() {
        val providerId = UUID.randomUUID()
        whenever(usageBudgetRepository.findByScopeAndScopeId(BudgetScope.PROVIDER, providerId)).thenReturn(
            listOf(
                UsageBudgetEntity(
                    scope = BudgetScope.PROVIDER,
                    scopeId = providerId,
                    period = BudgetPeriod.DAILY,
                    limitAmount = BigDecimal.ONE,
                    currentUsage = BigDecimal.ZERO,
                    createdAt = Instant.now(),
                    updatedAt = Instant.now(),
                ),
            ),
        )
        whenever(
            rateLimitCounterRepository.incrementIfAllowed(
                scope = eq(BudgetScope.PROVIDER),
                scopeId = eq(providerId),
                windowStart = any(),
                windowSizeMs = any(),
                requestLimit = eq(1L),
                tokenIncrement = any(),
            ),
        ).thenReturn(null)
        whenever(
            rateLimitCounterRepository.findCounter(
                scope = eq(BudgetScope.PROVIDER),
                scopeId = eq(providerId),
                windowStart = any(),
                windowSizeMs = any(),
            ),
        ).thenReturn(RateLimitCounterSnapshot(requestCount = 1L, tokenCount = 150L))

        val result =
            service.checkAndIncrement(
                providerScopeId = providerId,
                accountScopeId = UUID.randomUUID(),
                service = "assistant-api",
                feature = "chat",
                estimatedTokens = 150,
            )

        assertThat(result.allowed).isFalse()
        assertThat(result.scope).isEqualTo(BudgetScope.PROVIDER)
        assertThat(result.remaining).isEqualTo(0L)
        assertThat(result.resetAt).isNotNull

        verify(usageBudgetRepository, never()).findByScopeAndScopeId(eq(BudgetScope.ACCOUNT), any())
    }

    @Test
    fun `checkAndIncrement passes when provider and account are within limits`() {
        val providerId = UUID.randomUUID()
        val accountId = UUID.randomUUID()

        whenever(usageBudgetRepository.findByScopeAndScopeId(BudgetScope.PROVIDER, providerId)).thenReturn(
            listOf(
                UsageBudgetEntity(
                    scope = BudgetScope.PROVIDER,
                    scopeId = providerId,
                    period = BudgetPeriod.DAILY,
                    limitAmount = BigDecimal(10),
                    currentUsage = BigDecimal.ZERO,
                ),
            ),
        )
        whenever(usageBudgetRepository.findByScopeAndScopeId(BudgetScope.ACCOUNT, accountId)).thenReturn(
            listOf(
                UsageBudgetEntity(
                    scope = BudgetScope.ACCOUNT,
                    scopeId = accountId,
                    period = BudgetPeriod.DAILY,
                    limitAmount = BigDecimal(5),
                    currentUsage = BigDecimal.ZERO,
                ),
            ),
        )

        whenever(
            rateLimitCounterRepository.incrementIfAllowed(
                scope = any(),
                scopeId = any(),
                windowStart = any(),
                windowSizeMs = any(),
                requestLimit = any(),
                tokenIncrement = any(),
            ),
        ).thenReturn(RateLimitCounterSnapshot(requestCount = 1L, tokenCount = 80L))

        val result =
            service.checkAndIncrement(
                providerScopeId = providerId,
                accountScopeId = accountId,
                service = "assistant-api",
                feature = "chat",
                estimatedTokens = 80,
            )

        assertThat(result.allowed).isTrue()
        assertThat(result.scope).isNull()
        assertThat(result.remaining).isGreaterThanOrEqualTo(0L)
    }
}
