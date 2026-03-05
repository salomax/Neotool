package io.github.salomax.neotool.assistant.agent

import io.github.salomax.neotool.assistant.llm.ChatMessage
import io.github.salomax.neotool.assistant.llm.MessageRole
import io.micronaut.serde.annotation.Serdeable

@Serdeable
data class ConversationContext(
    val sessionId: String,
    val messages: MutableList<ChatMessage> = mutableListOf(),
    var status: ConversationStatus = ConversationStatus.ACTIVE,
    var escalationReason: String? = null,
) {
    fun addMessage(
        role: MessageRole,
        content: String,
    ) {
        messages.add(ChatMessage(role, content))
    }

    fun addSystemMessage(content: String) {
        messages.add(0, ChatMessage(MessageRole.SYSTEM, content))
    }

    fun markEscalated(reason: String) {
        status = ConversationStatus.ESCALATED
        escalationReason = reason.trim()
    }
}

@Serdeable
enum class ConversationStatus {
    ACTIVE,
    ESCALATED,
}
