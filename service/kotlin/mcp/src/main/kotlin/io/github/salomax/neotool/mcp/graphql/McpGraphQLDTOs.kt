package io.github.salomax.neotool.mcp.graphql

import io.github.salomax.neotool.mcp.audit.ToolCallAuditOutcome
import java.time.Instant
import java.util.UUID

data class McpConnectionRevocationDTO(
    val userId: UUID,
    val clientId: String,
    val accountId: UUID,
    val revokedAt: Instant,
    val revokedUntil: Instant?,
    val reason: String?,
)

data class McpRecentActivityEntryDTO(
    val id: UUID,
    val occurredAt: Instant,
    val userId: UUID,
    val clientId: String,
    val tokenId: String?,
    val toolName: String?,
    val outcome: ToolCallAuditOutcome,
    val errorCode: String?,
    val errorMessage: String?,
    val latencyMs: Long,
    val createdAt: Instant,
)
