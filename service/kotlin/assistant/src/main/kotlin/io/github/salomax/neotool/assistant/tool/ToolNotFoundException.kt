package io.github.salomax.neotool.assistant.tool

class ToolNotFoundException(
    toolName: String,
) : RuntimeException("Tool not found: $toolName")
