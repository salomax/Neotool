package io.github.salomax.neotool.assistant.graphql.resolver

import io.github.salomax.neotool.assistant.graphql.dto.PromptDTO
import io.github.salomax.neotool.assistant.graphql.dto.PromptVersionDTO
import io.github.salomax.neotool.assistant.graphql.mapper.PromptMapper
import io.github.salomax.neotool.assistant.service.PromptService
import io.micronaut.scheduling.TaskExecutors
import io.micronaut.scheduling.annotation.ExecuteOn
import jakarta.inject.Singleton
import java.util.UUID

@Singleton
class PromptResolver(
    private val promptService: PromptService,
    private val promptMapper: PromptMapper,
) {
    @ExecuteOn(TaskExecutors.BLOCKING)
    fun prompts(
        category: String?,
        tags: List<String>?,
    ): List<PromptDTO> {
        return promptService.findPromptsWithActiveVersions(category = category, tags = tags)
            .map { promptMapper.toPromptDTO(it.prompt, it.activeVersion) }
    }

    @ExecuteOn(TaskExecutors.BLOCKING)
    fun prompt(id: String): PromptDTO? {
        val promptId = parseUUID(id, "id")
        val withActive = promptService.findPromptById(promptId) ?: return null
        return promptMapper.toPromptDTO(withActive.prompt, withActive.activeVersion)
    }

    @ExecuteOn(TaskExecutors.BLOCKING)
    fun promptVersions(promptId: String): List<PromptVersionDTO> {
        val promptUuid = parseUUID(promptId, "promptId")
        return promptService.findPromptVersions(promptUuid).map(promptMapper::toPromptVersionDTO)
    }

    @ExecuteOn(TaskExecutors.BLOCKING)
    fun createPrompt(input: Map<String, Any?>): PromptDTO {
        val inputDTO = promptMapper.mapToCreatePromptInputDTO(input)
        val created = promptService.createPrompt(promptMapper.toCreatePromptInput(inputDTO))
        val promptId = created.id ?: throw IllegalStateException("Prompt ID was not generated")
        val withActive =
            promptService.findPromptById(promptId)
                ?: throw IllegalStateException("Created prompt could not be loaded: $promptId")

        return promptMapper.toPromptDTO(withActive.prompt, withActive.activeVersion)
    }

    @ExecuteOn(TaskExecutors.BLOCKING)
    fun publishPromptVersion(
        promptId: String,
        versionId: String,
    ): PromptVersionDTO {
        val promptUuid = parseUUID(promptId, "promptId")
        val versionUuid = parseUUID(versionId, "versionId")
        val published = promptService.publishPromptVersion(promptUuid, versionUuid)
        return promptMapper.toPromptVersionDTO(published)
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
}
