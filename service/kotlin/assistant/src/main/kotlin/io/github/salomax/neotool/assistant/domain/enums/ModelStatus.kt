package io.github.salomax.neotool.assistant.domain.enums

/**
 * Model lifecycle state.
 * Matches assistant.llm_models.status CHECK constraint.
 */
enum class ModelStatus {
    ACTIVE,
    DEPRECATED,
    DISABLED,
}
