package io.github.salomax.neotool.assistant.entity

import io.github.salomax.neotool.common.entity.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.Generated
import org.hibernate.generator.EventType
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/**
 * JPA entity for assistant.usage_log.
 */
@Entity
@Table(name = "usage_log", schema = "assistant")
open class UsageLogEntity(
    @Id
    @Generated(event = [EventType.INSERT])
    @Column(columnDefinition = "uuid", insertable = false, updatable = false)
    override val id: UUID? = null,
    @Column(name = "account_id", nullable = false, columnDefinition = "uuid")
    open var accountId: UUID,
    @Column(name = "request_id", columnDefinition = "uuid")
    open var requestId: UUID? = null,
    @Column(nullable = false)
    open var service: String,
    @Column(name = "user_id", columnDefinition = "uuid")
    open var userId: UUID? = null,
    @Column(nullable = false)
    open var feature: String,
    @Column(nullable = false)
    open var purpose: String,
    @Column(name = "prompt_tokens", nullable = false)
    open var promptTokens: Int = 0,
    @Column(name = "completion_tokens", nullable = false)
    open var completionTokens: Int = 0,
    @Column(name = "total_tokens", nullable = false)
    open var totalTokens: Int = 0,
    @Column(nullable = false, precision = 18, scale = 6)
    open var cost: BigDecimal = BigDecimal.ZERO,
    @Column(name = "model_id", columnDefinition = "uuid")
    open var modelId: UUID? = null,
    @Column(name = "logged_at", nullable = false)
    open var timestamp: Instant = Instant.now(),
) : BaseEntity<UUID?>(id)
