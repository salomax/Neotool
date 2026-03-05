package io.github.salomax.neotool.assistant.repository

import io.github.salomax.neotool.assistant.entity.ModelRoutingRuleEntity
import io.github.salomax.neotool.common.tracing.TraceExecution
import io.micronaut.data.annotation.Repository
import io.micronaut.data.jpa.repository.JpaRepository
import java.util.UUID

@TraceExecution(layer = "repository")
@Repository
interface ModelRoutingRuleRepository : JpaRepository<ModelRoutingRuleEntity, UUID> {
    fun findByEnabledTrueOrderByPriorityAscAndIdAsc(): List<ModelRoutingRuleEntity>
}
