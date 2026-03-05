package io.github.salomax.neotool.assistant.provider.model

import io.micronaut.serde.annotation.Serdeable

@Serdeable
data class ProviderToolCall(
    val id: String,
    val type: ProviderToolType = ProviderToolType.FUNCTION,
    val function: ProviderFunctionCall,
)

@Serdeable
data class ProviderFunctionCall(
    val name: String,
    val arguments: String,
)
