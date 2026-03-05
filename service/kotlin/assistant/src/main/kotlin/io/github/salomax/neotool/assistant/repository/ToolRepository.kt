package io.github.salomax.neotool.assistant.repository

import io.github.salomax.neotool.assistant.domain.enums.ToolStatus
import io.github.salomax.neotool.assistant.entity.ToolEntity
import io.github.salomax.neotool.common.tracing.TraceExecution
import io.micronaut.data.annotation.Query
import io.micronaut.data.annotation.Repository
import io.micronaut.data.jpa.repository.JpaRepository
import java.util.UUID

@TraceExecution(layer = "repository")
@Repository
interface ToolRepository : JpaRepository<ToolEntity, UUID> {
    @Query(
        """
        SELECT t FROM ToolEntity t
        WHERE t.status = :status
        ORDER BY t.name ASC
        """,
    )
    fun findByStatus(status: ToolStatus): List<ToolEntity>
}
