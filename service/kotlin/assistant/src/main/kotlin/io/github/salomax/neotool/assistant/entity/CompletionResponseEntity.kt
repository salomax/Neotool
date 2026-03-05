package io.github.salomax.neotool.assistant.entity

import io.github.salomax.neotool.common.entity.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import org.hibernate.annotations.Generated
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.generator.EventType
import org.hibernate.type.SqlTypes
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/**
 * JPA entity for assistant.completion_responses.
 */
@Entity
@Table(
    name = "completion_responses",
    schema = "assistant",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uq_completion_responses_request",
            columnNames = ["request_id"],
        ),
    ],
)
open class CompletionResponseEntity(
    @Id
    @Generated(event = [EventType.INSERT])
    @Column(columnDefinition = "uuid", insertable = false, updatable = false)
    override val id: UUID? = null,
    @Column(name = "request_id", nullable = false, columnDefinition = "uuid")
    open var requestId: UUID,
    @Column(name = "account_id", nullable = false, columnDefinition = "uuid")
    open var accountId: UUID,
    @Column(name = "provider_id", nullable = false, columnDefinition = "uuid")
    open var providerId: UUID,
    @Column(name = "model_id", nullable = false, columnDefinition = "uuid")
    open var modelId: UUID,
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    open var response: Map<String, Any?> = emptyMap(),
    @Column(name = "finish_reason")
    open var finishReason: String? = null,
    @Column(name = "prompt_tokens", nullable = false)
    open var promptTokens: Int = 0,
    @Column(name = "completion_tokens", nullable = false)
    open var completionTokens: Int = 0,
    @Column(name = "total_tokens", nullable = false)
    open var totalTokens: Int = 0,
    @Column(name = "latency_ms")
    open var latencyMs: Int? = null,
    @Column(nullable = false, precision = 18, scale = 6)
    open var cost: BigDecimal = BigDecimal.ZERO,
    @Column(nullable = false)
    open var escalate: Boolean = false,
    @Column(name = "completed_at", nullable = false)
    open var completedAt: Instant = Instant.now(),
) : BaseEntity<UUID?>(id)
