package io.github.salomax.neotool.assistant.llm

import io.micronaut.serde.annotation.Serdeable

@Serdeable
data class ChatMessage(
    val role: MessageRole,
    val content: String,
)

enum class MessageRole {
    USER,
    ASSISTANT,
    SYSTEM,
    FUNCTION,
}
