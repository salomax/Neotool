package io.github.salomax.neotool.assistant.provider.model

import io.micronaut.serde.annotation.Serdeable

@Serdeable
data class ProviderCompletionResponse(
    val id: String,
    val content: String?,
    val finishReason: ProviderFinishReason?,
    val usage: ProviderTokenUsage? = null,
    val toolCalls: List<ProviderToolCall> = emptyList(),
)
