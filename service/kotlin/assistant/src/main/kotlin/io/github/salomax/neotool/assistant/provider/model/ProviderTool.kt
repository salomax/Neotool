package io.github.salomax.neotool.assistant.provider.model

import io.micronaut.serde.annotation.Serdeable

@Serdeable
data class ProviderTool(
    val type: ProviderToolType = ProviderToolType.FUNCTION,
    val function: ProviderFunctionDefinition,
)

@Serdeable
data class ProviderFunctionDefinition(
    val name: String,
    val description: String? = null,
    val parameters: Map<String, Any?>? = null,
)

@Serdeable
enum class ProviderToolType {
    FUNCTION,
}
