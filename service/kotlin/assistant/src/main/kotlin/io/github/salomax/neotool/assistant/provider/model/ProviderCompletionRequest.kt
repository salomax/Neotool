package io.github.salomax.neotool.assistant.provider.model

import io.micronaut.serde.annotation.Serdeable

@Serdeable
data class ProviderCompletionRequest(
    val model: String,
    val messages: List<ProviderMessage>,
    val temperature: Double? = null,
    val maxTokens: Int? = null,
    val tools: List<ProviderTool>? = null,
    val responseFormat: ResponseFormat? = null,
    val stream: Boolean = false,
)
