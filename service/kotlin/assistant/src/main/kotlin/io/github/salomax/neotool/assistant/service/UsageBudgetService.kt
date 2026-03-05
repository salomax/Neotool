package io.github.salomax.neotool.assistant.service

import io.github.salomax.neotool.assistant.domain.enums.BudgetPeriod
import io.github.salomax.neotool.assistant.domain.enums.BudgetScope
import io.github.salomax.neotool.assistant.entity.UsageBudgetEntity
import io.github.salomax.neotool.assistant.observability.AssistantMetrics
import io.github.salomax.neotool.assistant.repository.UsageBudgetRepository
import io.github.salomax.neotool.common.tracing.TraceExecution
import io.micronaut.core.annotation.Nullable
import jakarta.inject.Singleton
import jakarta.transaction.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.util.UUID

@TraceExecution
@Singleton
open class UsageBudgetService(
    @param:Nullable private val usageBudgetRepository: UsageBudgetRepository?,
    private val assistantMetrics: AssistantMetrics,
) {
    @Transactional
    open fun setBudget(
        scope: BudgetScope,
        scopeId: UUID,
        period: BudgetPeriod,
        limitAmount: BigDecimal,
        alertThreshold: BigDecimal,
    ): UsageBudgetEntity {
        val repository = usageBudgetRepository ?: error("Usage budget persistence is not available")
        val normalizedLimit = limitAmount.max(BigDecimal.ZERO).setScale(6, RoundingMode.HALF_UP)
        require(normalizedLimit > BigDecimal.ZERO) { "limitAmount must be greater than zero" }

        val normalizedThreshold = alertThreshold.setScale(2, RoundingMode.HALF_UP)
        require(normalizedThreshold >= BigDecimal.ZERO && normalizedThreshold <= BigDecimal.ONE) {
            "alertThreshold must be between 0 and 1"
        }

        val existing = repository.findByScopeAndScopeIdAndPeriod(scope, scopeId, period)
        return if (existing == null) {
            repository.save(
                UsageBudgetEntity(
                    scope = scope,
                    scopeId = scopeId,
                    period = period,
                    limitAmount = normalizedLimit,
                    currentUsage = BigDecimal.ZERO.setScale(6, RoundingMode.HALF_UP),
                    alertThreshold = normalizedThreshold,
                    createdAt = Instant.now(),
                    updatedAt = Instant.now(),
                ),
            )
        } else {
            existing.limitAmount = normalizedLimit
            existing.alertThreshold = normalizedThreshold
            repository.update(existing)
        }
    }

    open fun checkBudget(
        scope: BudgetScope,
        scopeId: UUID,
        estimatedCost: BigDecimal,
    ): BudgetCheckResult {
        val repository =
            usageBudgetRepository
                ?: return BudgetCheckResult(allowed = true, percentUsed = BigDecimal.ZERO, checkedAnyBudget = false)
        val normalizedCost = estimatedCost.max(BigDecimal.ZERO).setScale(6, RoundingMode.HALF_UP)
        val budgets = repository.findByScopeAndScopeId(scope, scopeId)
        if (budgets.isEmpty()) {
            return BudgetCheckResult(allowed = true, percentUsed = BigDecimal.ZERO, checkedAnyBudget = false)
        }

        var allowed = true
        var maxPercentUsed = BigDecimal.ZERO.setScale(6, RoundingMode.HALF_UP)

        for (budget in budgets) {
            val projectedUsage = budget.currentUsage.add(normalizedCost).setScale(6, RoundingMode.HALF_UP)
            val limitAmount = normalizedLimitAmount(budget.limitAmount)
            val percentUsed = projectedUsage.divide(limitAmount, PERCENT_SCALE, RoundingMode.HALF_UP)
            if (projectedUsage > budget.limitAmount) {
                allowed = false
            }
            if (percentUsed > maxPercentUsed) {
                maxPercentUsed = percentUsed
            }
        }

        return BudgetCheckResult(
            allowed = allowed,
            percentUsed = maxPercentUsed,
            checkedAnyBudget = true,
        )
    }

    @Transactional
    open fun recordCost(
        scope: BudgetScope,
        scopeId: UUID,
        cost: BigDecimal,
    ): List<UsageBudgetEntity> {
        val repository = usageBudgetRepository ?: return emptyList()
        val normalizedCost = cost.max(BigDecimal.ZERO).setScale(6, RoundingMode.HALF_UP)
        if (normalizedCost == BigDecimal.ZERO.setScale(6, RoundingMode.HALF_UP)) {
            return emptyList()
        }

        val budgets = repository.findByScopeAndScopeId(scope, scopeId)
        if (budgets.isEmpty()) {
            return emptyList()
        }

        return budgets.map { budget ->
            val limitAmount = normalizedLimitAmount(budget.limitAmount)
            val previousPercent = budget.currentUsage.divide(limitAmount, PERCENT_SCALE, RoundingMode.HALF_UP)
            val updatedUsage = budget.currentUsage.add(normalizedCost).setScale(6, RoundingMode.HALF_UP)
            val updatedPercent = updatedUsage.divide(limitAmount, PERCENT_SCALE, RoundingMode.HALF_UP)

            budget.currentUsage = updatedUsage
            val updated = repository.update(budget)

            if (previousPercent < budget.alertThreshold && updatedPercent >= budget.alertThreshold) {
                assistantMetrics.incrementBudgetAlert(scope.name)
            }

            updated
        }
    }

    private fun normalizedLimitAmount(limitAmount: BigDecimal): BigDecimal {
        return if (limitAmount <= BigDecimal.ZERO) {
            MIN_LIMIT_AMOUNT
        } else {
            limitAmount.setScale(6, RoundingMode.HALF_UP)
        }
    }

    companion object {
        private const val PERCENT_SCALE = 6
        private val MIN_LIMIT_AMOUNT = BigDecimal("0.000001")
    }
}

data class BudgetCheckResult(
    val allowed: Boolean,
    val percentUsed: BigDecimal,
    val checkedAnyBudget: Boolean = false,
)
