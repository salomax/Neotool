package io.github.salomax.neotool.assistant.graphql.mapper

import io.github.salomax.neotool.assistant.domain.enums.BudgetPeriod
import io.github.salomax.neotool.assistant.domain.enums.BudgetScope
import io.github.salomax.neotool.assistant.entity.UsageBudgetEntity
import io.github.salomax.neotool.assistant.graphql.dto.SetBudgetInputDTO
import io.github.salomax.neotool.assistant.graphql.dto.UsageBudgetDTO
import jakarta.inject.Singleton
import java.util.UUID

@Singleton
class BudgetMapper {
    fun mapToSetBudgetInputDTO(input: Map<String, Any?>): SetBudgetInputDTO {
        val rawScope = input["scope"]?.toString() ?: throw IllegalArgumentException("Field 'scope' is required")
        val rawScopeId = input["scopeId"]?.toString() ?: throw IllegalArgumentException("Field 'scopeId' is required")
        val rawPeriod = input["period"]?.toString() ?: throw IllegalArgumentException("Field 'period' is required")
        val limitAmount =
            toDouble(
                input["limitAmount"],
            ) ?: throw IllegalArgumentException("Field 'limitAmount' is required")
        val alertThreshold =
            toDouble(input["alertThreshold"])
                ?: throw IllegalArgumentException("Field 'alertThreshold' is required")

        return SetBudgetInputDTO(
            scope = BudgetScope.valueOf(rawScope.uppercase()),
            scopeId = rawScopeId,
            period = BudgetPeriod.valueOf(rawPeriod.uppercase()),
            limitAmount = limitAmount,
            alertThreshold = alertThreshold,
        )
    }

    fun toScopeId(rawScopeId: String): UUID {
        return try {
            UUID.fromString(rawScopeId)
        } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException("Invalid UUID for 'scopeId': $rawScopeId", e)
        }
    }

    fun toUsageBudgetDTO(entity: UsageBudgetEntity): UsageBudgetDTO {
        val id = entity.id ?: throw IllegalArgumentException("UsageBudget must have an ID")
        return UsageBudgetDTO(
            id = id.toString(),
            scope = entity.scope,
            scopeId = entity.scopeId.toString(),
            period = entity.period,
            limitAmount = entity.limitAmount.toDouble(),
            currentUsage = entity.currentUsage.toDouble(),
            alertThreshold = entity.alertThreshold.toDouble(),
            createdAt = entity.createdAt.toString(),
            updatedAt = entity.updatedAt.toString(),
        )
    }

    private fun toDouble(raw: Any?): Double? {
        return when (raw) {
            null -> null
            is Double -> raw
            is Float -> raw.toDouble()
            is Int -> raw.toDouble()
            is Long -> raw.toDouble()
            is Number -> raw.toDouble()
            is String -> raw.toDoubleOrNull()
            else -> null
        }
    }
}
