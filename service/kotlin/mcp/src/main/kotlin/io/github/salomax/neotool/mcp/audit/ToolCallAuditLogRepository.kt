package io.github.salomax.neotool.mcp.audit

import io.micronaut.data.annotation.Repository
import io.micronaut.data.jpa.repository.JpaRepository
import java.util.UUID

@Repository
interface ToolCallAuditLogRepository : JpaRepository<ToolCallAuditLogEntity, UUID>
