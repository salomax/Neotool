package io.github.salomax.neotool.assistant.entity

import io.github.salomax.neotool.assistant.agent.ConversationStatus
import io.github.salomax.neotool.common.entity.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.Generated
import org.hibernate.generator.EventType
import java.time.Instant
import java.util.UUID

/**
 * JPA entity for assistant.conversations.
 */
@Entity
@Table(name = "conversations", schema = "assistant")
open class ConversationEntity(
    @Id
    @Generated(event = [EventType.INSERT])
    @Column(columnDefinition = "uuid", insertable = false, updatable = false)
    override val id: UUID? = null,
    @Column(name = "account_id", nullable = false, columnDefinition = "uuid")
    open var accountId: UUID,
    @Column(name = "session_id", nullable = false)
    open var sessionId: String,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    open var status: ConversationStatus,
    @Column(name = "escalation_reason")
    open var escalationReason: String? = null,
    @Column(name = "created_at", nullable = false)
    open var createdAt: Instant = Instant.now(),
    @Column(name = "updated_at", nullable = false)
    open var updatedAt: Instant = Instant.now(),
) : BaseEntity<UUID?>(id)
