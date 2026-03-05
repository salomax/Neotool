package io.github.salomax.neotool.assistant.domain.enums

/**
 * Tool execution outcome state.
 * Matches assistant.tool_executions.status CHECK constraint.
 */
enum class ToolExecutionStatus {
    SUCCESS,
    FAILED,
}
