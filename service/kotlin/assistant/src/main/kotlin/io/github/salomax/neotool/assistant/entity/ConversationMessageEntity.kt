package io.github.salomax.neotool.assistant.entity

import io.github.salomax.neotool.assistant.llm.MessageRole
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
 * JPA entity for assistant.conversation_messages.
 */
@Entity
@Table(name = "conversation_messages", schema = "assistant")
open class ConversationMessageEntity(
    @Id
    @Generated(event = [EventType.INSERT])
    @Column(columnDefinition = "uuid", insertable = false, updatable = false)
    override val id: UUID? = null,
    @Column(name = "conversation_id", nullable = false, columnDefinition = "uuid")
    open var conversationId: UUID,
    @Column(name = "sequence_no", nullable = false)
    open var sequenceNo: Int,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    open var role: MessageRole,
    @Column(nullable = false)
    open var content: String,
    @Column(name = "created_at", nullable = false)
    open var createdAt: Instant = Instant.now(),
) : BaseEntity<UUID?>(id)
