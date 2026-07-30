package io.github.salomax.neotool.mcp.audit

import io.micronaut.data.annotation.Query
import io.micronaut.data.annotation.Repository
import io.micronaut.data.jpa.repository.JpaRepository
import java.util.UUID

@Repository
interface ToolCallAuditLogRepository : JpaRepository<ToolCallAuditLogEntity, UUID> {
    @Query(
        value = """
            SELECT *
            FROM mcp.tool_call_audit_log
            WHERE user_id = :userId
              AND client_id = :clientId
            ORDER BY occurred_at DESC
            LIMIT :limit
        """,
        nativeQuery = true,
    )
    fun findRecentActivity(
        userId: UUID,
        clientId: String,
        limit: Int,
    ): List<ToolCallAuditLogEntity>
}
