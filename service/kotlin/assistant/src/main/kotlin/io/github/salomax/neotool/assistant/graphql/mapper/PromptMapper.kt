package io.github.salomax.neotool.assistant.graphql.mapper

import io.github.salomax.neotool.assistant.domain.enums.OutputFormat
import io.github.salomax.neotool.assistant.entity.PromptEntity
import io.github.salomax.neotool.assistant.entity.PromptVersionEntity
import io.github.salomax.neotool.assistant.graphql.dto.CreatePromptInputDTO
import io.github.salomax.neotool.assistant.graphql.dto.PromptDTO
import io.github.salomax.neotool.assistant.graphql.dto.PromptVariableDTO
import io.github.salomax.neotool.assistant.graphql.dto.PromptVersionDTO
import io.github.salomax.neotool.assistant.service.CreatePromptInput
import jakarta.inject.Singleton
import java.util.UUID

@Singleton
class PromptMapper {
    fun mapToCreatePromptInputDTO(input: Map<String, Any?>): CreatePromptInputDTO {
        val name = input["name"] as? String ?: throw IllegalArgumentException("Field 'name' is required")
        val category = input["category"] as? String ?: throw IllegalArgumentException("Field 'category' is required")
        val template = input["template"] as? String ?: throw IllegalArgumentException("Field 'template' is required")
        val createdBy = input["createdBy"] as? String ?: throw IllegalArgumentException("Field 'createdBy' is required")

        val description = input["description"] as? String
        val tags = (input["tags"] as? List<*>)?.mapNotNull { it?.toString() }.orEmpty()
        val variablesSchema = (input["variablesSchema"] as? Map<*, *>)?.normalizeMap() ?: emptyMap()
        val modelRequirements = (input["modelRequirements"] as? Map<*, *>)?.normalizeMap()
        val performanceMetrics = (input["performanceMetrics"] as? Map<*, *>)?.normalizeMap()

        val outputFormat =
            when (val raw = input["outputFormat"]) {
                null -> OutputFormat.TEXT
                is String -> OutputFormat.valueOf(raw)
                is OutputFormat -> raw
                else -> throw IllegalArgumentException("Invalid 'outputFormat' value")
            }

        return CreatePromptInputDTO(
            name = name,
            category = category,
            description = description,
            tags = tags,
            createdBy = createdBy,
            template = template,
            variablesSchema = variablesSchema,
            outputFormat = outputFormat,
            modelRequirements = modelRequirements,
            performanceMetrics = performanceMetrics,
        )
    }

    fun toCreatePromptInput(dto: CreatePromptInputDTO): CreatePromptInput {
        return CreatePromptInput(
            name = dto.name,
            category = dto.category,
            description = dto.description,
            tags = dto.tags,
            createdBy = parseUUID(dto.createdBy, "createdBy"),
            template = dto.template,
            variablesSchema = dto.variablesSchema,
            outputFormat = dto.outputFormat,
            modelRequirements = dto.modelRequirements,
            performanceMetrics = dto.performanceMetrics,
        )
    }

    fun toPromptDTO(
        prompt: PromptEntity,
        activeVersion: PromptVersionEntity?,
    ): PromptDTO {
        val promptId = prompt.id ?: throw IllegalArgumentException("Prompt must have an ID")
        return PromptDTO(
            id = promptId.toString(),
            name = prompt.name,
            category = prompt.category,
            description = prompt.description,
            tags = prompt.tags.toList(),
            createdBy = prompt.createdBy.toString(),
            createdAt = prompt.createdAt.toString(),
            updatedAt = prompt.updatedAt.toString(),
            activeVersion = activeVersion?.let { toPromptVersionDTO(it) },
        )
    }

    fun toPromptVersionDTO(version: PromptVersionEntity): PromptVersionDTO {
        val id = version.id ?: throw IllegalArgumentException("PromptVersion must have an ID")
        return PromptVersionDTO(
            id = id.toString(),
            promptId = version.promptId.toString(),
            version = version.version,
            status = version.status,
            template = version.template,
            variablesSchema = version.variablesSchema,
            variables = extractPromptVariables(version.variablesSchema),
            outputFormat = version.outputFormat,
            modelRequirements = version.modelRequirements,
            performanceMetrics = version.performanceMetrics,
            createdAt = version.createdAt.toString(),
            updatedAt = version.updatedAt.toString(),
        )
    }

    private fun extractPromptVariables(schema: Map<String, Any?>): List<PromptVariableDTO> {
        val fromSchema = extractFromJsonSchema(schema)
        if (fromSchema.isNotEmpty()) return fromSchema
        return extractFromVariableList(schema)
    }

    private fun extractFromJsonSchema(schema: Map<String, Any?>): List<PromptVariableDTO> {
        val properties = schema["properties"] as? Map<*, *> ?: return emptyList()
        val requiredSet =
            (schema["required"] as? List<*>)?.mapNotNull { it?.toString() }?.toSet()
                ?: emptySet()

        return properties.entries.mapNotNull { (rawName, rawDefinition) ->
            val name = rawName?.toString() ?: return@mapNotNull null
            val definition = rawDefinition as? Map<*, *> ?: return@mapNotNull null
            val normalizedDefinition = definition.normalizeMap()
            val type = normalizedDefinition["type"]?.toString()
            val defaultValue = normalizedDefinition["default"]

            val validation =
                normalizedDefinition
                    .filterKeys { it !in setOf("type", "default", "description", "title") }
                    .takeIf { it.isNotEmpty() }

            PromptVariableDTO(
                name = name,
                type = type,
                required = name in requiredSet,
                defaultValue = defaultValue,
                validation = validation,
            )
        }
    }

    private fun extractFromVariableList(schema: Map<String, Any?>): List<PromptVariableDTO> {
        val variables = schema["variables"] as? List<*> ?: return emptyList()
        return variables.mapNotNull { raw ->
            val item = raw as? Map<*, *> ?: return@mapNotNull null
            val normalized = item.normalizeMap()
            val name = normalized["name"]?.toString() ?: return@mapNotNull null
            PromptVariableDTO(
                name = name,
                type = normalized["type"]?.toString(),
                required = normalized["required"] as? Boolean ?: false,
                defaultValue = normalized["defaultValue"],
                validation = (normalized["validation"] as? Map<*, *>)?.normalizeMap(),
            )
        }
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

    private fun Map<*, *>.normalizeMap(): Map<String, Any?> {
        return entries.associate { (key, value) -> key.toString() to value }
    }
}
