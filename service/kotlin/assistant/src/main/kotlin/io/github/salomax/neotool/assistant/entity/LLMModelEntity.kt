package io.github.salomax.neotool.assistant.entity

import io.github.salomax.neotool.assistant.domain.enums.ModelStatus
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
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/**
 * JPA entity for assistant.llm_models.
 */
@Entity
@Table(
    name = "llm_models",
    schema = "assistant",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uq_llm_models_provider_model_name",
            columnNames = ["provider_id", "model_name"],
        ),
    ],
)
open class LLMModelEntity(
    @Id
    @Generated(event = [EventType.INSERT])
    @Column(columnDefinition = "uuid", insertable = false, updatable = false)
    override val id: UUID? = null,
    @Column(name = "provider_id", nullable = false, columnDefinition = "uuid")
    open var providerId: UUID,
    @Column(name = "model_name", nullable = false)
    open var modelName: String,
    @Column(name = "display_name", nullable = false)
    open var displayName: String,
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(nullable = false, columnDefinition = "text[]")
    open var capabilities: Array<String> = emptyArray(),
    @Column(name = "context_window", nullable = false)
    open var contextWindow: Int,
    @Column(name = "max_output_tokens", nullable = false)
    open var maxOutputTokens: Int,
    @Column(name = "cost_per_input_token", nullable = false, precision = 12, scale = 6)
    open var costPerInputToken: BigDecimal,
    @Column(name = "cost_per_output_token", nullable = false, precision = 12, scale = 6)
    open var costPerOutputToken: BigDecimal,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    open var status: ModelStatus = ModelStatus.ACTIVE,
    @Column(name = "created_at", nullable = false)
    open var createdAt: Instant = Instant.now(),
    @Version
    @Column(name = "updated_at", nullable = false)
    open var updatedAt: Instant = Instant.now(),
) : BaseEntity<UUID?>(id)
