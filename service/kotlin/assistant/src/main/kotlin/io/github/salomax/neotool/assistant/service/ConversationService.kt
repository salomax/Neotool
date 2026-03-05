package io.github.salomax.neotool.assistant.service

import io.github.salomax.neotool.assistant.agent.AssistantAgent
import io.github.salomax.neotool.assistant.agent.ConversationContext
import io.github.salomax.neotool.assistant.agent.ConversationStatus
import io.github.salomax.neotool.assistant.entity.ConversationEntity
import io.github.salomax.neotool.assistant.entity.ConversationMessageEntity
import io.github.salomax.neotool.assistant.llm.ChatMessage
import io.github.salomax.neotool.assistant.observability.AssistantMetrics
import io.github.salomax.neotool.assistant.repository.ConversationMessageRepository
import io.github.salomax.neotool.assistant.repository.ConversationRepository
import io.github.salomax.neotool.common.tracing.TraceExecution
import io.micronaut.core.annotation.Nullable
import jakarta.inject.Singleton
import jakarta.transaction.Transactional
import java.time.Instant
import java.util.UUID

@TraceExecution
@Singleton
open class ConversationService(
    @param:Nullable private val assistantAgent: AssistantAgent? = null,
    @param:Nullable private val escalationDetector: EscalationDetector? = null,
    @param:Nullable private val assistantMetrics: AssistantMetrics? = null,
    private val conversationRepository: ConversationRepository,
    private val conversationMessageRepository: ConversationMessageRepository,
) {
    @Transactional
    open fun getOrCreateContext(
        accountId: UUID,
        sessionId: String,
    ): ConversationContext {
        val normalizedSessionId = normalizeSessionId(sessionId)
        val conversation = getOrCreateConversation(accountId, normalizedSessionId)
        return mapToContext(conversation)
    }

    @Transactional
    open fun saveContext(
        accountId: UUID,
        context: ConversationContext,
    ) {
        val normalizedSessionId = normalizeSessionId(context.sessionId)
        val conversation = getOrCreateConversationForUpdate(accountId, normalizedSessionId)
        conversation.status = context.status
        conversation.escalationReason = context.escalationReason?.trim()?.takeIf { it.isNotEmpty() }
        conversation.updatedAt = Instant.now()
        conversationRepository.update(conversation)

        val conversationId = conversation.id ?: error("Conversation ID was not generated")
        appendNewMessages(conversationId, context.messages)
    }

    @Transactional
    open fun getContext(
        accountId: UUID,
        sessionId: String,
    ): ConversationContext? {
        val normalizedSessionId = normalizeSessionId(sessionId)
        val conversation =
            conversationRepository.findByAccountIdAndSessionId(
                accountId,
                normalizedSessionId,
            ) ?: return null
        return mapToContext(conversation)
    }

    @Transactional
    open fun clearContext(
        accountId: UUID,
        sessionId: String,
    ) {
        val normalizedSessionId = normalizeSessionId(sessionId)
        val conversation = conversationRepository.findByAccountIdAndSessionId(accountId, normalizedSessionId) ?: return
        val conversationId = conversation.id ?: return
        conversationMessageRepository.deleteByConversationId(conversationId)
        conversationRepository.delete(conversation)
    }

    /**
     * High-level helper used by chat flows:
     *  - Loads or creates the conversation context
     *  - Appends the USER message
     *  - Delegates to AssistantAgent for LLM/tool orchestration
     *  - Runs escalation detection on the final assistant content
     *  - Persists updated conversation + escalation status
     *  - Returns a lightweight response with escalation flag and updated context
     */
    @Transactional
    open suspend fun sendMessage(
        accountId: UUID,
        sessionId: String,
        userMessage: String,
    ): ConversationResponse {
        val agent =
            assistantAgent
                ?: error("AssistantAgent is required for sendMessage but was not provided")
        val detector =
            escalationDetector
                ?: error("EscalationDetector is required for sendMessage but was not provided")

        val context = getOrCreateContext(accountId, sessionId)

        val assistantContent = agent.processMessage(userMessage, context)
        val signal = detector.detect(assistantContent)

        if (signal.escalate) {
            val reason = signal.reason ?: UNKNOWN_ESCALATION_REASON
            context.markEscalated(reason)
            assistantMetrics?.incrementEscalation(reason)
        }

        saveContext(accountId, context)

        return ConversationResponse(
            escalate = signal.escalate,
            conversation = context,
        )
    }

    @Transactional
    open fun markEscalated(
        accountId: UUID,
        sessionId: String,
        reason: String,
    ) {
        val normalizedSessionId = normalizeSessionId(sessionId)
        val conversation = getOrCreateConversationForUpdate(accountId, normalizedSessionId)
        conversation.status = ConversationStatus.ESCALATED
        conversation.escalationReason = reason.ifBlank { UNKNOWN_ESCALATION_REASON }
        conversation.updatedAt = Instant.now()
        conversationRepository.update(conversation)
    }

    private fun getOrCreateConversation(
        accountId: UUID,
        sessionId: String,
    ): ConversationEntity {
        val existing = conversationRepository.findByAccountIdAndSessionId(accountId, sessionId)
        if (existing != null) {
            return existing
        }
        return createConversation(accountId, sessionId)
    }

    private fun getOrCreateConversationForUpdate(
        accountId: UUID,
        sessionId: String,
    ): ConversationEntity {
        val existing = conversationRepository.findForUpdateByAccountIdAndSessionId(accountId, sessionId)
        if (existing != null) {
            return existing
        }
        return createConversation(accountId, sessionId)
    }

    private fun createConversation(
        accountId: UUID,
        sessionId: String,
    ): ConversationEntity {
        return try {
            conversationRepository.save(
                ConversationEntity(
                    accountId = accountId,
                    sessionId = sessionId,
                    status = ConversationStatus.ACTIVE,
                ),
            )
        } catch (exception: RuntimeException) {
            conversationRepository.findForUpdateByAccountIdAndSessionId(accountId, sessionId)
                ?: throw exception
        }
    }

    private fun appendNewMessages(
        conversationId: UUID,
        contextMessages: List<ChatMessage>,
    ) {
        val persistedMessages = conversationMessageRepository.findByConversationIdOrderBySequenceNoAsc(conversationId)

        if (contextMessages.size < persistedMessages.size) {
            throw IllegalStateException("Conversation message history cannot shrink")
        }

        persistedMessages.forEachIndexed { index, persisted ->
            val current = contextMessages[index]
            if (persisted.role != current.role || persisted.content != current.content) {
                throw IllegalStateException("Conversation message history mismatch at index=$index")
            }
        }

        val newMessages = contextMessages.drop(persistedMessages.size)
        if (newMessages.isEmpty()) {
            return
        }

        val startSequenceNo = conversationMessageRepository.findMaxSequenceNoByConversationId(conversationId) + 1
        conversationMessageRepository.saveAll(
            newMessages.mapIndexed { index, message ->
                ConversationMessageEntity(
                    conversationId = conversationId,
                    sequenceNo = startSequenceNo + index,
                    role = message.role,
                    content = message.content,
                )
            },
        )
    }

    private fun mapToContext(conversation: ConversationEntity): ConversationContext {
        val conversationId = conversation.id ?: error("Conversation ID was not generated")
        val messages =
            conversationMessageRepository.findByConversationIdOrderBySequenceNoAsc(conversationId)
                .map { message -> ChatMessage(role = message.role, content = message.content) }
                .toMutableList()

        return ConversationContext(
            sessionId = conversation.sessionId,
            messages = messages,
            status = conversation.status,
            escalationReason = conversation.escalationReason,
        )
    }

    private fun normalizeSessionId(sessionId: String): String {
        val normalized = sessionId.trim()
        require(normalized.isNotEmpty()) { "sessionId is required" }
        return normalized
    }

    companion object {
        private const val UNKNOWN_ESCALATION_REASON = "unknown"
    }
}

data class ConversationResponse(
    val escalate: Boolean,
    val conversation: ConversationContext?,
)
