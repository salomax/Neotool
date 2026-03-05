package io.github.salomax.neotool.assistant.service

import io.github.salomax.neotool.assistant.domain.enums.BudgetPeriod
import io.github.salomax.neotool.assistant.domain.enums.BudgetScope
import io.github.salomax.neotool.assistant.repository.RateLimitCounterRepository
import io.github.salomax.neotool.assistant.repository.UsageBudgetRepository
import io.github.salomax.neotool.common.tracing.TraceExecution
import io.micronaut.core.annotation.Nullable
import jakarta.inject.Singleton
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.UUID

@TraceExecution
@Singleton
open class RateLimiterService(
    @param:Nullable private val usageBudgetRepository: UsageBudgetRepository?,
    @param:Nullable private val rateLimitCounterRepository: RateLimitCounterRepository?,
) {
    open fun checkAndIncrement(
        providerScopeId: UUID,
        accountScopeId: UUID,
        service: String,
        feature: String,
        estimatedTokens: Int,
    ): RateLimitResult {
        if (usageBudgetRepository == null || rateLimitCounterRepository == null) {
            return RateLimitResult(
                allowed = true,
                scope = null,
                remaining = Long.MAX_VALUE,
                resetAt = null,
                checkedAnyBudget = false,
            )
        }

        val normalizedService = service.trim()
        val normalizedFeature = feature.trim()
        require(normalizedService.isNotEmpty()) { "service is required" }
        require(normalizedFeature.isNotEmpty()) { "feature is required" }

        val checks =
            listOf(
                ScopeCheck(BudgetScope.PROVIDER, providerScopeId),
                ScopeCheck(BudgetScope.ACCOUNT, accountScopeId),
                ScopeCheck(BudgetScope.SERVICE, scopeIdFromName(BudgetScope.SERVICE, normalizedService)),
                ScopeCheck(BudgetScope.FEATURE, scopeIdFromName(BudgetScope.FEATURE, normalizedFeature)),
            )

        var remaining = Long.MAX_VALUE
        var resetAt: Instant? = null
        var checkedAnyBudget = false

        for (check in checks) {
            val scopeResult = checkAndIncrement(check.scope, check.scopeId, estimatedTokens)
            if (!scopeResult.checkedAnyBudget) {
                continue
            }
            if (!scopeResult.allowed) {
                return scopeResult
            }

            checkedAnyBudget = true
            remaining = minOf(remaining, scopeResult.remaining)
            resetAt =
                when {
                    resetAt == null -> scopeResult.resetAt
                    scopeResult.resetAt != null && scopeResult.resetAt.isBefore(resetAt) -> scopeResult.resetAt
                    else -> resetAt
                }
        }

        return RateLimitResult(
            allowed = true,
            scope = null,
            remaining = if (checkedAnyBudget) remaining else Long.MAX_VALUE,
            resetAt = resetAt,
            checkedAnyBudget = checkedAnyBudget,
        )
    }

    open fun checkAndIncrement(
        scope: BudgetScope,
        scopeId: UUID,
        estimatedTokens: Int,
    ): RateLimitResult {
        if (usageBudgetRepository == null || rateLimitCounterRepository == null) {
            return RateLimitResult(
                allowed = true,
                scope = null,
                remaining = Long.MAX_VALUE,
                resetAt = null,
                checkedAnyBudget = false,
            )
        }

        val budgets = usageBudgetRepository.findByScopeAndScopeId(scope, scopeId)
        if (budgets.isEmpty()) {
            return RateLimitResult(
                allowed = true,
                scope = null,
                remaining = Long.MAX_VALUE,
                resetAt = null,
                checkedAnyBudget = false,
            )
        }

        val now = Instant.now()
        val tokenIncrement = estimatedTokens.coerceAtLeast(0)
        var minRemaining = Long.MAX_VALUE
        var minResetAt: Instant? = null

        for (budget in budgets.sortedBy { it.period.toWindowSizeMs() }) {
            val requestLimit = budget.limitAmount.toLong().coerceAtLeast(0L)
            val windowSizeMs = budget.period.toWindowSizeMs()
            val windowStart = alignToWindow(now, windowSizeMs)
            val resetAt = windowStart.plusMillis(windowSizeMs)

            rateLimitCounterRepository.ensureCounterExists(
                scope = scope,
                scopeId = scopeId,
                windowStart = windowStart,
                windowSizeMs = windowSizeMs,
            )

            val updated =
                rateLimitCounterRepository.incrementIfAllowed(
                    scope = scope,
                    scopeId = scopeId,
                    windowStart = windowStart,
                    windowSizeMs = windowSizeMs,
                    requestLimit = requestLimit,
                    tokenIncrement = tokenIncrement,
                )

            if (updated == null) {
                val currentCount =
                    rateLimitCounterRepository.findCounter(
                        scope = scope,
                        scopeId = scopeId,
                        windowStart = windowStart,
                        windowSizeMs = windowSizeMs,
                    )?.requestCount ?: 0L

                return RateLimitResult(
                    allowed = false,
                    scope = scope,
                    remaining = (requestLimit - currentCount).coerceAtLeast(0L),
                    resetAt = resetAt,
                    checkedAnyBudget = true,
                )
            }

            val remaining = (requestLimit - updated.requestCount).coerceAtLeast(0L)
            minRemaining = minOf(minRemaining, remaining)
            minResetAt =
                when {
                    minResetAt == null -> resetAt
                    resetAt.isBefore(minResetAt) -> resetAt
                    else -> minResetAt
                }
        }

        return RateLimitResult(
            allowed = true,
            scope = null,
            remaining = minRemaining,
            resetAt = minResetAt,
            checkedAnyBudget = true,
        )
    }

    private fun alignToWindow(
        instant: Instant,
        windowSizeMs: Long,
    ): Instant {
        val epochMs = instant.toEpochMilli()
        val windowStartMs = (epochMs / windowSizeMs) * windowSizeMs
        return Instant.ofEpochMilli(windowStartMs)
    }

    private fun scopeIdFromName(
        scope: BudgetScope,
        value: String,
    ): UUID {
        return UUID.nameUUIDFromBytes("${scope.name}:${value.lowercase()}".toByteArray(StandardCharsets.UTF_8))
    }

    private fun BudgetPeriod.toWindowSizeMs(): Long {
        return when (this) {
            BudgetPeriod.DAILY -> 24L * 60L * 60L * 1000L
            BudgetPeriod.WEEKLY -> 7L * 24L * 60L * 60L * 1000L
            BudgetPeriod.MONTHLY -> 30L * 24L * 60L * 60L * 1000L
        }
    }

    private data class ScopeCheck(
        val scope: BudgetScope,
        val scopeId: UUID,
    )
}

data class RateLimitResult(
    val allowed: Boolean,
    val scope: BudgetScope?,
    val remaining: Long,
    val resetAt: Instant?,
    val checkedAnyBudget: Boolean = false,
)
