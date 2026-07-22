package io.github.salomax.neotool.assistant.entity

import io.github.salomax.neotool.assistant.domain.enums.ToolStatus
import io.github.salomax.neotool.common.entity.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import jakarta.persistence.Version
import org.hibernate.annotations.Generated
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.generator.EventType
import org.hibernate.type.SqlTypes
import java.time.Instant
import java.util.UUID

/**
 * JPA entity for assistant.tools.
 */
@Entity
@Table(
    name = "tools",
    schema = "assistant",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uq_tools_name",
            columnNames = ["name"],
        ),
    ],
)
open class ToolEntity(
    @Id
    @Generated(event = [EventType.INSERT])
    @Column(columnDefinition = "uuid", insertable = false, updatable = false)
    override val id: UUID? = null,
    @Column(nullable = false)
    open var name: String,
    @Column(nullable = false)
    open var description: String,
    @Column(nullable = false)
    open var category: String,
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "input_schema", nullable = false, columnDefinition = "jsonb")
    open var inputSchema: Map<String, Any?> = emptyMap(),
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "output_schema", nullable = false, columnDefinition = "jsonb")
    open var outputSchema: Map<String, Any?> = emptyMap(),
    @Column(name = "executor_service", nullable = false)
    open var executorService: String,
    @Column(name = "executor_endpoint", nullable = false)
    open var executorEndpoint: String,
    @Column(name = "timeout_ms", nullable = false)
    open var timeoutMs: Int,
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "retry_policy", columnDefinition = "jsonb")
    open var retryPolicy: Map<String, Any?>? = null,
    @Column(name = "mcp_exposed", nullable = false)
    open var mcpExposed: Boolean = false,
    @Column(name = "mcp_required_scope")
    open var mcpRequiredScope: String? = null,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    open var status: ToolStatus = ToolStatus.ACTIVE,
    @Column(name = "created_at", nullable = false)
    open var createdAt: Instant = Instant.now(),
    @Version
    @Column(name = "updated_at", nullable = false)
    open var updatedAt: Instant = Instant.now(),
) : BaseEntity<UUID?>(id)
