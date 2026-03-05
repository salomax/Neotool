package io.github.salomax.neotool.assistant.repository

import io.github.salomax.neotool.assistant.domain.enums.PromptVersionStatus
import io.github.salomax.neotool.assistant.entity.PromptVersionEntity
import io.github.salomax.neotool.common.tracing.TraceExecution
import io.micronaut.data.annotation.Repository
import io.micronaut.data.jpa.repository.JpaRepository
import java.util.UUID

@TraceExecution(layer = "repository")
@Repository
interface PromptVersionRepository : JpaRepository<PromptVersionEntity, UUID> {
    fun findByPromptIdAndStatus(
        promptId: UUID,
        status: PromptVersionStatus,
    ): PromptVersionEntity?

    fun findByPromptIdOrderByVersionDesc(promptId: UUID): List<PromptVersionEntity>

    fun findByPromptIdInAndStatus(
        promptIds: List<UUID>,
        status: PromptVersionStatus,
    ): List<PromptVersionEntity>
}
