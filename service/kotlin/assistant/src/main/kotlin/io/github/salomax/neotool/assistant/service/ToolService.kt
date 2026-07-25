package io.github.salomax.neotool.assistant.service

import io.github.salomax.neotool.assistant.domain.enums.ToolExecutionStatus
import io.github.salomax.neotool.assistant.domain.enums.ToolStatus
import io.github.salomax.neotool.assistant.entity.ToolEntity
import io.github.salomax.neotool.assistant.entity.ToolExecutionEntity
import io.github.salomax.neotool.assistant.repository.ToolExecutionRepository
import io.github.salomax.neotool.assistant.repository.ToolRepository
import io.github.salomax.neotool.common.tracing.TraceExecution
import jakarta.inject.Singleton
import jakarta.transaction.Transactional
import java.util.UUID

@TraceExecution
@Singleton
open class ToolService(
    private val toolRepository: ToolRepository,
    private val toolExecutionRepository: ToolExecutionRepository,
) {
    @Transactional
    open fun registerTool(input: RegisterToolInput): ToolEntity {
        require(input.name.isNotBlank()) { "Tool name is required" }
        require(input.description.isNotBlank()) { "Tool description is required" }
        require(input.category.isNotBlank()) { "Tool category is required" }
        require(input.executorService.isNotBlank()) { "Tool executorService is required" }
        require(input.executorEndpoint.isNotBlank()) { "Tool executorEndpoint is required" }
        require(input.timeoutMs > 0) { "Tool timeoutMs must be greater than zero" }
        if (input.mcpExposed) {
            require(!input.mcpRequiredScope.isNullOrBlank()) { "Tool mcpRequiredScope is required when exposed to MCP" }
        }

        return toolRepository.save(
            ToolEntity(
                name = input.name.trim(),
                description = input.description.trim(),
                category = input.category.trim(),
                inputSchema = input.inputSchema,
                outputSchema = input.outputSchema,
                executorService = input.executorService.trim(),
                executorEndpoint = input.executorEndpoint.trim(),
                timeoutMs = input.timeoutMs,
                retryPolicy = input.retryPolicy,
                mcpExposed = input.mcpExposed,
                mcpRequiredScope = input.mcpRequiredScope?.trim()?.takeIf { it.isNotEmpty() },
                sensitiveInputFields = input.sensitiveInputFields.map(String::trim).filter(String::isNotEmpty).distinct(),
                status = input.status,
            ),
        )
    }

    open fun getAvailableTools(): List<ToolEntity> = toolRepository.findByStatus(ToolStatus.ACTIVE)

    @Transactional
    open fun recordExecution(
        completionRequestId: UUID,
        toolId: UUID,
        input: Map<String, Any?>,
        output: Map<String, Any?>?,
        status: ToolExecutionStatus,
        latencyMs: Int?,
        error: String? = null,
    ): ToolExecutionEntity {
        return toolExecutionRepository.save(
            ToolExecutionEntity(
                completionRequestId = completionRequestId,
                toolId = toolId,
                input = input,
                output = output,
                status = status,
                error = error?.trim()?.takeIf { it.isNotEmpty() },
                latencyMs = latencyMs?.coerceAtLeast(0),
            ),
        )
    }
}

data class RegisterToolInput(
    val name: String,
    val description: String,
    val category: String,
    val inputSchema: Map<String, Any?> = emptyMap(),
    val outputSchema: Map<String, Any?> = emptyMap(),
    val executorService: String,
    val executorEndpoint: String,
    val timeoutMs: Int,
    val retryPolicy: Map<String, Any?>? = null,
    val mcpExposed: Boolean = false,
    val mcpRequiredScope: String? = null,
    val sensitiveInputFields: List<String> = emptyList(),
    val status: ToolStatus = ToolStatus.ACTIVE,
)
