package io.github.salomax.neotool.assistant.graphql.mapper

import io.github.salomax.neotool.assistant.domain.enums.OutputFormat
import io.github.salomax.neotool.assistant.graphql.dto.CompletionInputDTO
import io.github.salomax.neotool.assistant.graphql.dto.CompletionResponseDTO
import io.github.salomax.neotool.assistant.graphql.dto.MessageInputDTO
import io.github.salomax.neotool.assistant.graphql.dto.TokenUsageDTO
import io.github.salomax.neotool.assistant.graphql.dto.ToolCallDTO
import io.github.salomax.neotool.assistant.provider.model.ProviderMessageRole
import io.github.salomax.neotool.assistant.service.CompletionInput
import io.github.salomax.neotool.assistant.service.CompletionMessageInput
import io.github.salomax.neotool.assistant.service.CompletionResult
import jakarta.inject.Singleton
import java.util.UUID

@Singleton
class CompletionMapper {
    fun mapToCompletionInputDTO(input: Map<String, Any?>): CompletionInputDTO {
        val purpose = input["purpose"] as? String ?: throw IllegalArgumentException("Field 'purpose' is required")

        val promptId = input["promptId"] as? String
        val modelPreference = input["modelPreference"] as? String
        val temperature = toDouble(input["temperature"])
        val maxTokens = toInt(input["maxTokens"])

        val promptVariables = (input["promptVariables"] as? Map<*, *>)?.normalizeMap()
        val metadata = (input["metadata"] as? Map<*, *>)?.normalizeMap()
        val requiredCapabilities = (input["requiredCapabilities"] as? List<*>)?.mapNotNull { it?.toString() }.orEmpty()
        val tools = (input["tools"] as? List<*>)?.mapNotNull { it?.toString() }
        val messages =
            (input["messages"] as? List<*>)?.mapNotNull { raw ->
                val item = raw as? Map<*, *> ?: return@mapNotNull null
                val normalized = item.normalizeMap()
                val role = normalized["role"]?.toString() ?: return@mapNotNull null
                MessageInputDTO(
                    role = role,
                    content = normalized["content"]?.toString(),
                    name = normalized["name"]?.toString(),
                    toolCallId = normalized["toolCallId"]?.toString(),
                )
            }.orEmpty()

        val outputFormat =
            when (val raw = input["outputFormat"]) {
                null -> null
                is String -> OutputFormat.valueOf(raw)
                is OutputFormat -> raw
                else -> throw IllegalArgumentException("Invalid 'outputFormat' value")
            }

        return CompletionInputDTO(
            promptId = promptId,
            promptVariables = promptVariables,
            messages = messages,
            modelPreference = modelPreference,
            requiredCapabilities = requiredCapabilities,
            temperature = temperature,
            maxTokens = maxTokens,
            outputFormat = outputFormat,
            tools = tools,
            purpose = purpose,
            metadata = metadata,
        )
    }

    fun toCompletionInput(
        dto: CompletionInputDTO,
        requestedByService: String,
        userId: UUID?,
    ): CompletionInput {
        return CompletionInput(
            promptId = dto.promptId?.let { parseUUID(it, "promptId") },
            promptVariables = dto.promptVariables,
            messages =
                dto.messages.map { message ->
                    CompletionMessageInput(
                        role = ProviderMessageRole.valueOf(message.role.uppercase()),
                        content = message.content,
                        name = message.name,
                        toolCallId = message.toolCallId,
                    )
                },
            modelPreference = dto.modelPreference,
            requiredCapabilities = dto.requiredCapabilities,
            temperature = dto.temperature,
            maxTokens = dto.maxTokens,
            outputFormat = dto.outputFormat,
            tools = dto.tools,
            purpose = dto.purpose,
            metadata = dto.metadata,
            requestedByService = requestedByService,
            userId = userId,
        )
    }

    fun toCompletionResponseDTO(result: CompletionResult): CompletionResponseDTO {
        return CompletionResponseDTO(
            id = result.id.toString(),
            content = result.content,
            finishReason = result.finishReason,
            usage =
                TokenUsageDTO(
                    promptTokens = result.usage.promptTokens,
                    completionTokens = result.usage.completionTokens,
                    totalTokens = result.usage.totalTokens,
                ),
            model = result.model,
            latencyMs = result.latencyMs,
            cost = result.cost.toDouble(),
            escalate = result.escalate,
            toolCalls =
                result.toolCalls.map { toolCall ->
                    ToolCallDTO(
                        id = toolCall.id,
                        type = toolCall.type.name,
                        name = toolCall.function.name,
                        arguments = toolCall.function.arguments,
                    )
                },
        )
    }

    private fun parseUUID(
        rawValue: String,
        fieldName: String,
    ): UUID {
        return try {
            UUID.fromString(rawValue)
        } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException("Invalid UUID for '$fieldName': $rawValue", e)
        }
    }

    private fun toDouble(raw: Any?): Double? {
        return when (raw) {
            null -> null
            is Double -> raw
            is Float -> raw.toDouble()
            is Int -> raw.toDouble()
            is Long -> raw.toDouble()
            is Number -> raw.toDouble()
            is String -> raw.toDoubleOrNull()
            else -> null
        }
    }

    private fun toInt(raw: Any?): Int? {
        return when (raw) {
            null -> null
            is Int -> raw
            is Long -> raw.toInt()
            is Double -> raw.toInt()
            is Float -> raw.toInt()
            is Number -> raw.toInt()
            is String -> raw.toIntOrNull()
            else -> null
        }
    }

    private fun Map<*, *>.normalizeMap(): Map<String, Any?> {
        return entries.associate { (key, value) -> key.toString() to value }
    }
}
