package io.github.salomax.neotool.assistant.entity

import io.github.salomax.neotool.common.entity.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.Generated
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.generator.EventType
import org.hibernate.type.SqlTypes
import java.time.Instant
import java.util.UUID

/**
 * JPA entity for assistant.completion_requests.
 */
@Entity
@Table(name = "completion_requests", schema = "assistant")
open class CompletionRequestEntity(
    @Id
    @Generated(event = [EventType.INSERT])
    @Column(columnDefinition = "uuid", insertable = false, updatable = false)
    override val id: UUID? = null,
    @Column(name = "account_id", nullable = false, columnDefinition = "uuid")
    open var accountId: UUID,
    @Column(name = "requested_by_service", nullable = false)
    open var requestedByService: String,
    @Column(name = "user_id", columnDefinition = "uuid")
    open var userId: UUID? = null,
    @Column(name = "prompt_id", columnDefinition = "uuid")
    open var promptId: UUID? = null,
    @Column(name = "prompt_version_id", columnDefinition = "uuid")
    open var promptVersionId: UUID? = null,
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    open var messages: List<Map<String, Any?>> = emptyList(),
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    open var variables: Map<String, Any?>? = null,
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    open var options: Map<String, Any?>? = null,
    @Column(nullable = false)
    open var purpose: String,
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    open var metadata: Map<String, Any?>? = null,
    @Column(name = "created_at", nullable = false)
    open var createdAt: Instant = Instant.now(),
) : BaseEntity<UUID?>(id)
