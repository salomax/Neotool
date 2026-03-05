package io.github.salomax.neotool.assistant.graphql.dto

import io.github.salomax.neotool.assistant.domain.enums.OutputFormat
import io.github.salomax.neotool.assistant.domain.enums.PromptVersionStatus

data class CreatePromptInputDTO(
    val name: String,
    val category: String,
    val description: String?,
    val tags: List<String>,
    val createdBy: String,
    val template: String,
    val variablesSchema: Map<String, Any?>,
    val outputFormat: OutputFormat,
    val modelRequirements: Map<String, Any?>?,
    val performanceMetrics: Map<String, Any?>?,
)

data class PromptDTO(
    val id: String,
    val name: String,
    val category: String,
    val description: String?,
    val tags: List<String>,
    val createdBy: String,
    val createdAt: String,
    val updatedAt: String,
    val activeVersion: PromptVersionDTO?,
)

data class PromptVersionDTO(
    val id: String,
    val promptId: String,
    val version: Int,
    val status: PromptVersionStatus,
    val template: String,
    val variablesSchema: Map<String, Any?>,
    val variables: List<PromptVariableDTO>,
    val outputFormat: OutputFormat,
    val modelRequirements: Map<String, Any?>?,
    val performanceMetrics: Map<String, Any?>?,
    val createdAt: String,
    val updatedAt: String,
)

data class PromptVariableDTO(
    val name: String,
    val type: String?,
    val required: Boolean,
    val defaultValue: Any?,
    val validation: Map<String, Any?>?,
)
