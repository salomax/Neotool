package io.github.salomax.neotool.assistant.graphql.resolver

import io.github.salomax.neotool.assistant.graphql.dto.UsageBudgetDTO
import io.github.salomax.neotool.assistant.graphql.mapper.BudgetMapper
import io.github.salomax.neotool.assistant.service.UsageBudgetService
import io.micronaut.scheduling.TaskExecutors
import io.micronaut.scheduling.annotation.ExecuteOn
import jakarta.inject.Singleton
import java.math.BigDecimal

@Singleton
class BudgetResolver(
    private val usageBudgetService: UsageBudgetService,
    private val budgetMapper: BudgetMapper,
) {
    @ExecuteOn(TaskExecutors.BLOCKING)
    fun setUsageBudget(input: Map<String, Any?>): UsageBudgetDTO {
        val inputDTO = budgetMapper.mapToSetBudgetInputDTO(input)
        val saved =
            usageBudgetService.setBudget(
                scope = inputDTO.scope,
                scopeId = budgetMapper.toScopeId(inputDTO.scopeId),
                period = inputDTO.period,
                limitAmount = BigDecimal.valueOf(inputDTO.limitAmount),
                alertThreshold = BigDecimal.valueOf(inputDTO.alertThreshold),
            )
        return budgetMapper.toUsageBudgetDTO(saved)
    }
}
