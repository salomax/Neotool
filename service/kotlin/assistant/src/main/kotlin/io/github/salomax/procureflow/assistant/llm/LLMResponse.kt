package io.github.salomax.neotool.assistant.llm

import io.micronaut.serde.annotation.Serdeable

@Serdeable
data class LLMResponse(
    val text: String?,
    val functionCalls: List<FunctionCall> = emptyList(),
    val finishReason: FinishReason
)

@Serdeable
data class FunctionCall(
    val name: String,
    val arguments: Map<String, Any?>
)

enum class FinishReason {
    STOP,
    FUNCTION_CALL,
    LENGTH,
    ERROR
}

