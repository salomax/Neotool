package io.github.salomax.neotool.assistant.domain.enums

/**
 * Prompt version lifecycle state.
 * Matches assistant.prompt_versions.status CHECK constraint.
 */
enum class PromptVersionStatus {
    DRAFT,
    ACTIVE,
    DEPRECATED,
}
