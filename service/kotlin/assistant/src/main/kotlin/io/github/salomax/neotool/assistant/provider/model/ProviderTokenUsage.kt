package io.github.salomax.neotool.assistant.provider.model

import io.micronaut.serde.annotation.Serdeable

@Serdeable
data class ProviderTokenUsage(
    val promptTokens: Int = 0,
    val completionTokens: Int = 0,
    val totalTokens: Int = 0,
)
