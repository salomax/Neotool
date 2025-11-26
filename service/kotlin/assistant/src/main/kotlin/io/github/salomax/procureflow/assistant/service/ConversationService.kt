package io.github.salomax.neotool.assistant.service

import io.github.salomax.neotool.assistant.agent.ConversationContext
import jakarta.inject.Singleton
import java.util.concurrent.ConcurrentHashMap

@Singleton
class ConversationService {
    // In-memory conversation storage (use Redis in production)
    private val conversations = ConcurrentHashMap<String, ConversationContext>()

    fun getOrCreateContext(sessionId: String): ConversationContext {
        return conversations.getOrPut(sessionId) {
            ConversationContext(sessionId = sessionId)
        }
    }

    fun getContext(sessionId: String): ConversationContext? {
        return conversations[sessionId]
    }

    fun clearContext(sessionId: String) {
        conversations.remove(sessionId)
    }
}
