package io.github.salomax.neotool.assistant.graphql.resolver

import io.github.salomax.neotool.assistant.graphql.dto.CompletionResponseDTO
import io.github.salomax.neotool.assistant.graphql.mapper.CompletionMapper
import io.github.salomax.neotool.assistant.service.CompletionService
import io.micronaut.scheduling.TaskExecutors
import io.micronaut.scheduling.annotation.ExecuteOn
import jakarta.inject.Singleton
import kotlinx.coroutines.runBlocking
import java.util.UUID

@Singleton
class CompletionResolver(
    private val completionService: CompletionService,
    private val completionMapper: CompletionMapper,
) {
    @ExecuteOn(TaskExecutors.BLOCKING)
    fun generateCompletion(
        input: Map<String, Any?>,
        accountId: UUID,
        requestedByService: String,
        userId: UUID?,
    ): CompletionResponseDTO {
        val inputDTO = completionMapper.mapToCompletionInputDTO(input)
        val serviceInput = completionMapper.toCompletionInput(inputDTO, requestedByService, userId)
        val result = runBlocking { completionService.generateCompletion(serviceInput, accountId) }
        return completionMapper.toCompletionResponseDTO(result)
    }
}
