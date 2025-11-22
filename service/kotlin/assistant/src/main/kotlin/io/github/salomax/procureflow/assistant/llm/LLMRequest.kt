package io.github.salomax.neotool.assistant.llm

import io.micronaut.serde.annotation.Serdeable

@Serdeable
data class LLMRequest(
    val messages: List<ChatMessage>,
    val functions: List<FunctionDefinition>,
    val temperature: Double = 0.7,
    val maxTokens: Int = 2048,
)

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

@Serdeable
data class FunctionDefinition(
    val name: String,
    val description: String,
    val parameters: FunctionParameters,
)

@Serdeable
data class FunctionParameters(
    val type: String = "object",
    val properties: Map<String, PropertyDefinition>,
    val required: List<String> = emptyList(),
)

@Serdeable
data class PropertyDefinition(
    val type: String,
    val description: String,
    val enum: List<String>? = null,
)
