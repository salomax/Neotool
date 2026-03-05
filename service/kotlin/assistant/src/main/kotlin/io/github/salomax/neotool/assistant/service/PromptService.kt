package io.github.salomax.neotool.assistant.service

import io.github.salomax.neotool.assistant.domain.enums.OutputFormat
import io.github.salomax.neotool.assistant.domain.enums.PromptVersionStatus
import io.github.salomax.neotool.assistant.entity.PromptEntity
import io.github.salomax.neotool.assistant.entity.PromptVersionEntity
import io.github.salomax.neotool.assistant.repository.PromptRepository
import io.github.salomax.neotool.assistant.repository.PromptVersionRepository
import io.github.salomax.neotool.common.tracing.TraceExecution
import jakarta.inject.Singleton
import jakarta.transaction.Transactional
import java.util.UUID

@TraceExecution
@Singleton
open class PromptService(
    private val promptRepository: PromptRepository,
    private val promptVersionRepository: PromptVersionRepository,
) {
    @Transactional
    open fun createPrompt(input: CreatePromptInput): PromptEntity {
        require(input.name.isNotBlank()) { "Prompt name is required" }
        require(input.category.isNotBlank()) { "Prompt category is required" }
        require(input.template.isNotBlank()) { "Prompt template is required" }

        val prompt =
            promptRepository.save(
                PromptEntity(
                    name = input.name.trim(),
                    category = input.category.trim(),
                    description = input.description?.trim(),
                    tags = input.tags.toTypedArray(),
                    createdBy = input.createdBy,
                ),
            )

        promptVersionRepository.save(
            PromptVersionEntity(
                promptId = prompt.id ?: error("Prompt ID was not generated"),
                version = 1,
                status = PromptVersionStatus.DRAFT,
                template = input.template,
                variablesSchema = input.variablesSchema,
                outputFormat = input.outputFormat,
                modelRequirements = input.modelRequirements,
                performanceMetrics = input.performanceMetrics,
            ),
        )

        return prompt
    }

    @Transactional
    open fun createPromptVersion(
        promptId: UUID,
        input: CreatePromptVersionInput,
    ): PromptVersionEntity {
        require(input.template.isNotBlank()) { "Prompt template is required" }
        val prompt =
            promptRepository.findById(
                promptId,
            ).orElseThrow { IllegalArgumentException("Prompt not found: $promptId") }
        val promptIdValue = prompt.id ?: error("Prompt ID was not loaded")
        val lastVersion = promptVersionRepository.findByPromptIdOrderByVersionDesc(promptIdValue).firstOrNull()
        val nextVersion = (lastVersion?.version ?: 0) + 1

        return promptVersionRepository.save(
            PromptVersionEntity(
                promptId = promptIdValue,
                version = nextVersion,
                status = PromptVersionStatus.DRAFT,
                template = input.template,
                variablesSchema = input.variablesSchema,
                outputFormat = input.outputFormat,
                modelRequirements = input.modelRequirements,
                performanceMetrics = input.performanceMetrics,
            ),
        )
    }

    @Transactional
    open fun publishPromptVersion(
        promptId: UUID,
        versionId: UUID,
    ): PromptVersionEntity {
        promptRepository.findById(promptId).orElseThrow { IllegalArgumentException("Prompt not found: $promptId") }
        val targetVersion =
            promptVersionRepository.findById(versionId)
                .orElseThrow { IllegalArgumentException("Prompt version not found: $versionId") }

        require(targetVersion.promptId == promptId) {
            "Prompt version $versionId does not belong to prompt $promptId"
        }

        val currentActive = promptVersionRepository.findByPromptIdAndStatus(promptId, PromptVersionStatus.ACTIVE)
        if (currentActive != null && currentActive.id != targetVersion.id) {
            currentActive.status = PromptVersionStatus.DEPRECATED
            promptVersionRepository.update(currentActive)
        }

        if (targetVersion.status != PromptVersionStatus.ACTIVE) {
            targetVersion.status = PromptVersionStatus.ACTIVE
            return promptVersionRepository.update(targetVersion)
        }

        return targetVersion
    }

    open fun findPrompts(
        category: String? = null,
        tags: List<String>? = null,
    ): List<PromptEntity> {
        val normalizedCategory = category?.trim()?.takeIf { it.isNotBlank() }
        val normalizedTags = tags.orEmpty().map { it.trim() }.filter { it.isNotBlank() }

        return when {
            normalizedCategory != null && normalizedTags.isNotEmpty() ->
                promptRepository
                    .findByCategory(normalizedCategory)
                    .filter { prompt -> prompt.tags.any { it in normalizedTags } }
            normalizedCategory != null -> promptRepository.findByCategory(normalizedCategory)
            normalizedTags.isNotEmpty() -> promptRepository.findByTags(normalizedTags)
            else -> promptRepository.findAll()
        }
    }

    open fun findPromptsWithActiveVersions(
        category: String? = null,
        tags: List<String>? = null,
    ): List<PromptWithActiveVersion> {
        val prompts = findPrompts(category = category, tags = tags)
        if (prompts.isEmpty()) {
            return emptyList()
        }

        val promptIds = prompts.mapNotNull { it.id }
        val activeVersionsByPromptId =
            promptVersionRepository
                .findByPromptIdInAndStatus(promptIds, PromptVersionStatus.ACTIVE)
                .associateBy { it.promptId }

        return prompts.map { prompt ->
            val promptId = prompt.id ?: error("Prompt ID was not loaded")
            PromptWithActiveVersion(
                prompt = prompt,
                activeVersion = activeVersionsByPromptId[promptId],
            )
        }
    }

    open fun findPromptById(id: UUID): PromptWithActiveVersion? {
        val prompt = promptRepository.findById(id).orElse(null) ?: return null
        val activeVersion = promptRepository.findActiveVersion(id)
        return PromptWithActiveVersion(prompt = prompt, activeVersion = activeVersion)
    }

    open fun findPromptVersions(promptId: UUID): List<PromptVersionEntity> {
        return promptVersionRepository.findByPromptIdOrderByVersionDesc(promptId)
    }

    open fun renderTemplate(
        versionId: UUID,
        variables: Map<String, Any?>,
    ): String {
        val version =
            promptVersionRepository.findById(versionId)
                .orElseThrow { IllegalArgumentException("Prompt version not found: $versionId") }

        val definitions = extractVariableDefinitions(version.variablesSchema)
        val resolvedVariables = definitions.defaults + variables
        val missingRequired =
            definitions.required.filter { key ->
                val value = resolvedVariables[key]
                value == null || (value is String && value.isBlank())
            }

        require(missingRequired.isEmpty()) {
            "Missing required template variables: ${missingRequired.joinToString(", ")}"
        }

        val rendered = replaceTemplateVariables(version.template, resolvedVariables)
        require(!UNRESOLVED_PLACEHOLDER_REGEX.containsMatchIn(rendered)) {
            "Template contains unresolved variables"
        }
        return rendered
    }

    private fun replaceTemplateVariables(
        template: String,
        variables: Map<String, Any?>,
    ): String {
        val normalized = template.replace(DOLLAR_PLACEHOLDER_REGEX) { match -> "{{${match.groupValues[1]}}}" }

        return CURLY_PLACEHOLDER_REGEX.replace(normalized) { match ->
            val key = match.groupValues[1].trim()
            val value =
                variables[key]
                    ?: throw IllegalArgumentException("Missing variable for placeholder: $key")
            value.toString()
        }
    }

    private fun extractVariableDefinitions(schema: Map<String, Any?>): VariableDefinitions {
        val requiredFromJsonSchema =
            (schema["required"] as? List<*>)
                ?.mapNotNull { it?.toString() }
                .orEmpty()
                .toSet()

        val defaultsFromJsonSchema =
            ((schema["properties"] as? Map<*, *>) ?: emptyMap<Any?, Any?>())
                .entries
                .mapNotNull { (rawName, rawDefinition) ->
                    val name = rawName?.toString() ?: return@mapNotNull null
                    val definition = rawDefinition as? Map<*, *> ?: return@mapNotNull null
                    if (!definition.containsKey("default")) return@mapNotNull null
                    name to definition["default"]
                }.toMap()

        val variablesList = schema["variables"] as? List<*>
        val requiredFromList =
            variablesList
                ?.mapNotNull { raw ->
                    val item = raw as? Map<*, *> ?: return@mapNotNull null
                    val name = item["name"]?.toString() ?: return@mapNotNull null
                    val required = item["required"] as? Boolean ?: false
                    if (required) name else null
                }.orEmpty()
                .toSet()

        val defaultsFromList =
            variablesList
                ?.mapNotNull { raw ->
                    val item = raw as? Map<*, *> ?: return@mapNotNull null
                    val name = item["name"]?.toString() ?: return@mapNotNull null
                    if (!item.containsKey("defaultValue")) return@mapNotNull null
                    name to item["defaultValue"]
                }.orEmpty()
                .toMap()

        return VariableDefinitions(
            required = requiredFromJsonSchema + requiredFromList,
            defaults = defaultsFromJsonSchema + defaultsFromList,
        )
    }

    private data class VariableDefinitions(
        val required: Set<String>,
        val defaults: Map<String, Any?>,
    )

    companion object {
        private val CURLY_PLACEHOLDER_REGEX = Regex("\\{\\{\\s*([a-zA-Z0-9_.-]+)\\s*\\}\\}")
        private val DOLLAR_PLACEHOLDER_REGEX = Regex("\\$\\{\\s*([a-zA-Z0-9_.-]+)\\s*\\}")
        private val UNRESOLVED_PLACEHOLDER_REGEX =
            Regex("\\{\\{\\s*[a-zA-Z0-9_.-]+\\s*\\}\\}|\\$\\{\\s*[a-zA-Z0-9_.-]+\\s*\\}")
    }
}

data class CreatePromptInput(
    val name: String,
    val category: String,
    val description: String? = null,
    val tags: List<String> = emptyList(),
    val createdBy: UUID,
    val template: String,
    val variablesSchema: Map<String, Any?> = emptyMap(),
    val outputFormat: OutputFormat = OutputFormat.TEXT,
    val modelRequirements: Map<String, Any?>? = null,
    val performanceMetrics: Map<String, Any?>? = null,
)

data class CreatePromptVersionInput(
    val template: String,
    val variablesSchema: Map<String, Any?> = emptyMap(),
    val outputFormat: OutputFormat = OutputFormat.TEXT,
    val modelRequirements: Map<String, Any?>? = null,
    val performanceMetrics: Map<String, Any?>? = null,
)

data class PromptWithActiveVersion(
    val prompt: PromptEntity,
    val activeVersion: PromptVersionEntity?,
)
