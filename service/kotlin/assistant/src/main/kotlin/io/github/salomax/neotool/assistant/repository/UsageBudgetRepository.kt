package io.github.salomax.neotool.assistant.repository

import io.github.salomax.neotool.assistant.domain.enums.BudgetPeriod
import io.github.salomax.neotool.assistant.domain.enums.BudgetScope
import io.github.salomax.neotool.assistant.entity.UsageBudgetEntity
import io.github.salomax.neotool.common.tracing.TraceExecution
import io.micronaut.data.annotation.Repository
import io.micronaut.data.jpa.repository.JpaRepository
import java.util.UUID

@TraceExecution(layer = "repository")
@Repository
interface UsageBudgetRepository : JpaRepository<UsageBudgetEntity, UUID> {
    fun findByScopeAndScopeId(
        scope: BudgetScope,
        scopeId: UUID,
    ): List<UsageBudgetEntity>

    fun findByScopeAndScopeIdAndPeriod(
        scope: BudgetScope,
        scopeId: UUID,
        period: BudgetPeriod,
    ): UsageBudgetEntity?
}
