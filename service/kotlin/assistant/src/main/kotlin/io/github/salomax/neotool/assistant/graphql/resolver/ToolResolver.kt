package io.github.salomax.neotool.assistant.graphql.resolver

import io.github.salomax.neotool.assistant.graphql.dto.ToolDTO
import io.github.salomax.neotool.assistant.graphql.mapper.ToolMapper
import io.github.salomax.neotool.assistant.service.ToolService
import io.micronaut.scheduling.TaskExecutors
import io.micronaut.scheduling.annotation.ExecuteOn
import jakarta.inject.Singleton

@Singleton
class ToolResolver(
    private val toolService: ToolService,
    private val toolMapper: ToolMapper,
) {
    @ExecuteOn(TaskExecutors.BLOCKING)
    fun availableTools(): List<ToolDTO> {
        return toolService.getAvailableTools().map(toolMapper::toToolDTO)
    }

    @ExecuteOn(TaskExecutors.BLOCKING)
    fun registerTool(input: Map<String, Any?>): ToolDTO {
        val inputDTO = toolMapper.mapToRegisterToolInputDTO(input)
        val created = toolService.registerTool(toolMapper.toRegisterToolInput(inputDTO))
        return toolMapper.toToolDTO(created)
    }
}
