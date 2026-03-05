package io.github.salomax.neotool.assistant.graphql.dto

import io.github.salomax.neotool.assistant.domain.enums.ToolStatus

data class RegisterToolInputDTO(
    val name: String,
    val description: String,
    val category: String,
    val inputSchema: Map<String, Any?> = emptyMap(),
    val outputSchema: Map<String, Any?> = emptyMap(),
    val executorService: String,
    val executorEndpoint: String,
    val timeoutMs: Int,
    val retryPolicy: Map<String, Any?>? = null,
    val status: ToolStatus = ToolStatus.ACTIVE,
)

data class ToolDTO(
    val id: String,
    val name: String,
    val description: String,
    val category: String,
    val inputSchema: Map<String, Any?>,
    val outputSchema: Map<String, Any?>,
    val executorService: String,
    val executorEndpoint: String,
    val timeoutMs: Int,
    val retryPolicy: Map<String, Any?>?,
    val status: ToolStatus,
    val createdAt: String,
    val updatedAt: String,
)
