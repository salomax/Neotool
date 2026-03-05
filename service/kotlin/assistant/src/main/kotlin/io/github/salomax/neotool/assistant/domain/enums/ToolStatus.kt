package io.github.salomax.neotool.assistant.domain.enums

/**
 * Tool lifecycle state.
 * Matches assistant.tools.status CHECK constraint.
 */
enum class ToolStatus {
    ACTIVE,
    DISABLED,
}
