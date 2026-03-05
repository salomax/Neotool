package io.github.salomax.neotool.assistant.provider.model

import io.micronaut.serde.annotation.Serdeable

@Serdeable
enum class ProviderFinishReason(
    val value: String,
) {
    STOP("stop"),
    LENGTH("length"),
    TOOL_CALLS("tool_calls"),
    CONTENT_FILTER("content_filter"),
    ERROR("error"),
    UNKNOWN("unknown"),
    ;

    companion object {
        fun fromValue(value: String?): ProviderFinishReason? {
            if (value.isNullOrBlank()) {
                return null
            }
            return entries.firstOrNull { it.value == value } ?: UNKNOWN
        }
    }
}
