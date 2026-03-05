package io.github.salomax.neotool.assistant.provider.model

import io.micronaut.serde.annotation.Serdeable

@Serdeable
data class ProviderCompletionChunk(
    val delta: String = "",
    val finishReason: ProviderFinishReason? = null,
    val usage: ProviderTokenUsage? = null,
)
