package io.github.salomax.neotool.assistant.repository

import io.github.salomax.neotool.assistant.entity.ToolExecutionEntity
import io.github.salomax.neotool.common.tracing.TraceExecution
import io.micronaut.data.annotation.Query
import io.micronaut.data.annotation.Repository
import io.micronaut.data.jpa.repository.JpaRepository
import java.util.UUID

@TraceExecution(layer = "repository")
@Repository
interface ToolExecutionRepository : JpaRepository<ToolExecutionEntity, UUID> {
    @Query(
        """
        SELECT te FROM ToolExecutionEntity te
        WHERE te.completionRequestId = :completionRequestId
        ORDER BY te.executedAt DESC
        """,
    )
    fun findByCompletionRequestId(completionRequestId: UUID): List<ToolExecutionEntity>
}
