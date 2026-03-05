package io.github.salomax.neotool.assistant.provider.model

import io.micronaut.serde.annotation.Serdeable

@Serdeable
data class ResponseFormat(
    val type: ResponseFormatType,
    val jsonSchema: Map<String, Any?>? = null,
)

@Serdeable
enum class ResponseFormatType {
    TEXT,
    JSON_OBJECT,
    JSON_SCHEMA,
}
