package io.github.salomax.neotool.assistant.entity

import io.github.salomax.neotool.common.entity.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Version
import org.hibernate.annotations.Generated
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.generator.EventType
import org.hibernate.type.SqlTypes
import java.time.Instant
import java.util.UUID

/**
 * JPA entity for assistant.model_routing_rules.
 */
@Entity
@Table(name = "model_routing_rules", schema = "assistant")
open class ModelRoutingRuleEntity(
    @Id
    @Generated(event = [EventType.INSERT])
    @Column(columnDefinition = "uuid", insertable = false, updatable = false)
    override val id: UUID? = null,
    @Column(nullable = false)
    open var priority: Int = 100,
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    open var conditions: Map<String, Any?> = emptyMap(),
    @Column(name = "target_model_id", nullable = false, columnDefinition = "uuid")
    open var targetModelId: UUID,
    @Column(name = "weight_percent", nullable = false)
    open var weightPercent: Int = 0,
    @Column(nullable = false)
    open var enabled: Boolean = true,
    @Column(name = "created_at", nullable = false)
    open var createdAt: Instant = Instant.now(),
    @Version
    @Column(name = "updated_at", nullable = false)
    open var updatedAt: Instant = Instant.now(),
) : BaseEntity<UUID?>(id)
