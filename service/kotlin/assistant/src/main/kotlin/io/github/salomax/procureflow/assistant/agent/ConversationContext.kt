package io.github.salomax.neotool.assistant.agent

import io.github.salomax.neotool.assistant.llm.ChatMessage
import io.github.salomax.neotool.assistant.llm.MessageRole
import io.micronaut.serde.annotation.Serdeable

@Serdeable
data class ConversationContext(
    val sessionId: String,
    val messages: MutableList<ChatMessage> = mutableListOf(),
    val lastReferencedItem: String? = null
) {
    fun addMessage(role: MessageRole, content: String) {
        messages.add(ChatMessage(role, content))
    }
    
    fun addSystemMessage(content: String) {
        messages.add(0, ChatMessage(MessageRole.SYSTEM, content))
    }
}

