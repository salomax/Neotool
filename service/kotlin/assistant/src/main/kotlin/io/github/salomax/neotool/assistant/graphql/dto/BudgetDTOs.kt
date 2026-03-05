package io.github.salomax.neotool.assistant.graphql.dto

import io.github.salomax.neotool.assistant.domain.enums.BudgetPeriod
import io.github.salomax.neotool.assistant.domain.enums.BudgetScope

data class SetBudgetInputDTO(
    val scope: BudgetScope,
    val scopeId: String,
    val period: BudgetPeriod,
    val limitAmount: Double,
    val alertThreshold: Double,
)

data class UsageBudgetDTO(
    val id: String,
    val scope: BudgetScope,
    val scopeId: String,
    val period: BudgetPeriod,
    val limitAmount: Double,
    val currentUsage: Double,
    val alertThreshold: Double,
    val createdAt: String,
    val updatedAt: String,
)
