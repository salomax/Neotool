package io.github.salomax.neotool.assistant.entity

import io.github.salomax.neotool.assistant.domain.enums.OutputFormat
import io.github.salomax.neotool.assistant.domain.enums.PromptVersionStatus
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
 * JPA entity for assistant.prompt_versions.
 */
@Entity
@Table(
    name = "prompt_versions",
    schema = "assistant",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uq_prompt_versions_prompt_version",
            columnNames = ["prompt_id", "version"],
        ),
    ],
)
open class PromptVersionEntity(
    @Id
    @Generated(event = [EventType.INSERT])
    @Column(columnDefinition = "uuid", insertable = false, updatable = false)
    override val id: UUID? = null,
    @Column(name = "prompt_id", nullable = false, columnDefinition = "uuid")
    open var promptId: UUID,
    @Column(nullable = false)
    open var version: Int,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    open var status: PromptVersionStatus,
    @Column(nullable = false, columnDefinition = "text")
    open var template: String,
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "variables_schema", nullable = false, columnDefinition = "jsonb")
    open var variablesSchema: Map<String, Any?> = emptyMap(),
    @Enumerated(EnumType.STRING)
    @Column(name = "output_format", nullable = false)
    open var outputFormat: OutputFormat = OutputFormat.TEXT,
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "model_requirements", columnDefinition = "jsonb")
    open var modelRequirements: Map<String, Any?>? = null,
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "performance_metrics", columnDefinition = "jsonb")
    open var performanceMetrics: Map<String, Any?>? = null,
    @Column(name = "created_at", nullable = false)
    open var createdAt: Instant = Instant.now(),
    @Version
    @Column(name = "updated_at", nullable = false)
    open var updatedAt: Instant = Instant.now(),
) : BaseEntity<UUID?>(id)
