package io.github.salomax.neotool.assistant.graphql.mapper

import io.github.salomax.neotool.assistant.domain.enums.ToolStatus
import io.github.salomax.neotool.assistant.entity.ToolEntity
import io.github.salomax.neotool.assistant.graphql.dto.RegisterToolInputDTO
import io.github.salomax.neotool.assistant.graphql.dto.ToolDTO
import io.github.salomax.neotool.assistant.service.RegisterToolInput
import jakarta.inject.Singleton

@Singleton
class ToolMapper {
    fun mapToRegisterToolInputDTO(input: Map<String, Any?>): RegisterToolInputDTO {
        val name = input["name"] as? String ?: throw IllegalArgumentException("Field 'name' is required")
        val description =
            input["description"] as? String ?: throw IllegalArgumentException(
                "Field 'description' is required",
            )
        val category = input["category"] as? String ?: throw IllegalArgumentException("Field 'category' is required")
        val executorService =
            input["executorService"] as? String
                ?: throw IllegalArgumentException("Field 'executorService' is required")
        val executorEndpoint =
            input["executorEndpoint"] as? String
                ?: throw IllegalArgumentException("Field 'executorEndpoint' is required")
        val timeoutMs = toInt(input["timeoutMs"]) ?: throw IllegalArgumentException("Field 'timeoutMs' is required")

        val inputSchema = (input["inputSchema"] as? Map<*, *>)?.normalizeMap() ?: emptyMap()
        val outputSchema = (input["outputSchema"] as? Map<*, *>)?.normalizeMap() ?: emptyMap()
        val retryPolicy = (input["retryPolicy"] as? Map<*, *>)?.normalizeMap()
        val status =
            when (val raw = input["status"]) {
                null -> ToolStatus.ACTIVE
                is String -> ToolStatus.valueOf(raw)
                is ToolStatus -> raw
                else -> throw IllegalArgumentException("Invalid 'status' value")
            }

        return RegisterToolInputDTO(
            name = name,
            description = description,
            category = category,
            inputSchema = inputSchema,
            outputSchema = outputSchema,
            executorService = executorService,
            executorEndpoint = executorEndpoint,
            timeoutMs = timeoutMs,
            retryPolicy = retryPolicy,
            status = status,
        )
    }

    fun toRegisterToolInput(dto: RegisterToolInputDTO): RegisterToolInput {
        return RegisterToolInput(
            name = dto.name,
            description = dto.description,
            category = dto.category,
            inputSchema = dto.inputSchema,
            outputSchema = dto.outputSchema,
            executorService = dto.executorService,
            executorEndpoint = dto.executorEndpoint,
            timeoutMs = dto.timeoutMs,
            retryPolicy = dto.retryPolicy,
            status = dto.status,
        )
    }

    fun toToolDTO(entity: ToolEntity): ToolDTO {
        val id = entity.id ?: throw IllegalArgumentException("Tool must have an ID")
        return ToolDTO(
            id = id.toString(),
            name = entity.name,
            description = entity.description,
            category = entity.category,
            inputSchema = entity.inputSchema,
            outputSchema = entity.outputSchema,
            executorService = entity.executorService,
            executorEndpoint = entity.executorEndpoint,
            timeoutMs = entity.timeoutMs,
            retryPolicy = entity.retryPolicy,
            status = entity.status,
            createdAt = entity.createdAt.toString(),
            updatedAt = entity.updatedAt.toString(),
        )
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
