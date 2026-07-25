package io.github.salomax.neotool.mcp.audit

import io.micronaut.core.annotation.Introspected
import io.micronaut.data.annotation.MappedEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant
import java.util.UUID

@Entity
@MappedEntity("mcp.tool_call_audit_log")
@Table(name = "tool_call_audit_log", schema = "mcp")
@Introspected
data class ToolCallAuditLogEntity(
    @Id
    val id: UUID,
    @Column(name = "occurred_at", nullable = false)
    val occurredAt: Instant,
    @Column(name = "user_id")
    val userId: UUID? = null,
    @Column(name = "client_id")
    val clientId: String? = null,
    @Column(name = "token_id")
    val tokenId: String? = null,
    @Column(name = "tool_name")
    val toolName: String? = null,
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "arguments", nullable = false, columnDefinition = "jsonb")
    val arguments: Map<String, Any?> = emptyMap(),
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val outcome: ToolCallAuditOutcome,
    @Column(name = "error_code")
    val errorCode: String? = null,
    @Column(name = "error_message")
    val errorMessage: String? = null,
    @Column(name = "latency_ms", nullable = false)
    val latencyMs: Long,
    @Column(name = "created_at", nullable = false)
    val createdAt: Instant,
)
