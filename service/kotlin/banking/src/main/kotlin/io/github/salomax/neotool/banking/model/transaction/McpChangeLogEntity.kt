package io.github.salomax.neotool.banking.model.transaction

import io.github.salomax.neotool.banking.domain.transaction.McpChangeLogAction
import io.github.salomax.neotool.banking.domain.transaction.McpChangeLogResourceType
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
@MappedEntity("banking.mcp_change_log")
@Table(name = "mcp_change_log", schema = "banking")
@Introspected
data class McpChangeLogEntity(
    @Id
    val id: UUID,
    @Column(name = "tool_call_id", nullable = false)
    val toolCallId: UUID,
    @Column(name = "client_id")
    val clientId: String? = null,
    @Column(name = "token_id")
    val tokenId: String? = null,
    @Enumerated(EnumType.STRING)
    @Column(name = "resource_type", nullable = false)
    val resourceType: McpChangeLogResourceType,
    @Column(name = "resource_id", nullable = false)
    val resourceId: UUID,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val action: McpChangeLogAction,
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    val before: Map<String, Any?> = emptyMap(),
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    val after: Map<String, Any?> = emptyMap(),
    @Column(name = "created_at", nullable = false)
    val createdAt: Instant,
)
