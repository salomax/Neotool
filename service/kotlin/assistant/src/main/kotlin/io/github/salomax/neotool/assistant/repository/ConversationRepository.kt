package io.github.salomax.neotool.assistant.repository

import io.github.salomax.neotool.assistant.entity.ConversationEntity
import io.github.salomax.neotool.common.tracing.TraceExecution
import io.micronaut.data.annotation.Query
import io.micronaut.data.annotation.Repository
import io.micronaut.data.jpa.repository.JpaRepository
import java.util.UUID

@TraceExecution(layer = "repository")
@Repository
interface ConversationRepository : JpaRepository<ConversationEntity, UUID> {
    fun findByAccountIdAndSessionId(
        accountId: UUID,
        sessionId: String,
    ): ConversationEntity?

    @Query(
        """
        SELECT *
        FROM assistant.conversations
        WHERE account_id = :accountId
          AND session_id = :sessionId
        FOR UPDATE
        """,
        nativeQuery = true,
    )
    fun findForUpdateByAccountIdAndSessionId(
        accountId: UUID,
        sessionId: String,
    ): ConversationEntity?
}
