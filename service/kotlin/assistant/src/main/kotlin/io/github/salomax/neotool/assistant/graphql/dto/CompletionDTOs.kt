package io.github.salomax.neotool.assistant.graphql.dto

import io.github.salomax.neotool.assistant.domain.enums.OutputFormat

data class CompletionInputDTO(
    val promptId: String? = null,
    val promptVariables: Map<String, Any?>? = null,
    val messages: List<MessageInputDTO> = emptyList(),
    val modelPreference: String? = null,
    val requiredCapabilities: List<String> = emptyList(),
    val temperature: Double? = null,
    val maxTokens: Int? = null,
    val outputFormat: OutputFormat? = null,
    val tools: List<String>? = null,
    val purpose: String,
    val metadata: Map<String, Any?>? = null,
)

data class MessageInputDTO(
    val role: String,
    val content: String? = null,
    val name: String? = null,
    val toolCallId: String? = null,
)

data class CompletionResponseDTO(
    val id: String,
    val content: String,
    val finishReason: String,
    val usage: TokenUsageDTO,
    val model: String,
    val latencyMs: Int,
    val cost: Double,
    val escalate: Boolean,
    val toolCalls: List<ToolCallDTO>,
)

data class CompletionChunkDTO(
    val delta: String,
    val finishReason: String? = null,
    val usage: TokenUsageDTO? = null,
    val escalate: Boolean = false,
)

data class TokenUsageDTO(
    val promptTokens: Int,
    val completionTokens: Int,
    val totalTokens: Int,
)

data class ToolCallDTO(
    val id: String,
    val type: String,
    val name: String,
    val arguments: String,
)
