package io.github.salomax.neotool.assistant.repository

import io.github.salomax.neotool.assistant.entity.ConversationMessageEntity
import io.github.salomax.neotool.common.tracing.TraceExecution
import io.micronaut.data.annotation.Query
import io.micronaut.data.annotation.Repository
import io.micronaut.data.jpa.repository.JpaRepository
import java.util.UUID

@TraceExecution(layer = "repository")
@Repository
interface ConversationMessageRepository : JpaRepository<ConversationMessageEntity, UUID> {
    fun findByConversationIdOrderBySequenceNoAsc(conversationId: UUID): List<ConversationMessageEntity>

    @Query(
        """
        SELECT COALESCE(MAX(m.sequenceNo), 0)
        FROM ConversationMessageEntity m
        WHERE m.conversationId = :conversationId
        """,
    )
    fun findMaxSequenceNoByConversationId(conversationId: UUID): Int

    fun deleteByConversationId(conversationId: UUID)
}
