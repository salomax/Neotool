package io.github.salomax.neotool.assistant.entity

import io.github.salomax.neotool.assistant.domain.enums.ToolExecutionStatus
import io.github.salomax.neotool.common.entity.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.Generated
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.generator.EventType
import org.hibernate.type.SqlTypes
import java.time.Instant
import java.util.UUID

/**
 * JPA entity for assistant.tool_executions.
 */
@Entity
@Table(name = "tool_executions", schema = "assistant")
open class ToolExecutionEntity(
    @Id
    @Generated(event = [EventType.INSERT])
    @Column(columnDefinition = "uuid", insertable = false, updatable = false)
    override val id: UUID? = null,
    @Column(name = "completion_request_id", nullable = false, columnDefinition = "uuid")
    open var completionRequestId: UUID,
    @Column(name = "tool_id", nullable = false, columnDefinition = "uuid")
    open var toolId: UUID,
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    open var input: Map<String, Any?> = emptyMap(),
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    open var output: Map<String, Any?>? = null,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    open var status: ToolExecutionStatus,
    @Column
    open var error: String? = null,
    @Column(name = "latency_ms")
    open var latencyMs: Int? = null,
    @Column(name = "executed_at", nullable = false)
    open var executedAt: Instant = Instant.now(),
) : BaseEntity<UUID?>(id)
