package io.github.salomax.neotool.assistant.provider.model

import io.micronaut.serde.annotation.Serdeable

@Serdeable
data class ProviderMessage(
    val role: ProviderMessageRole,
    val content: String? = null,
    val name: String? = null,
    val toolCallId: String? = null,
    val toolCalls: List<ProviderToolCall> = emptyList(),
)

@Serdeable
enum class ProviderMessageRole {
    SYSTEM,
    USER,
    ASSISTANT,
    TOOL,
}
