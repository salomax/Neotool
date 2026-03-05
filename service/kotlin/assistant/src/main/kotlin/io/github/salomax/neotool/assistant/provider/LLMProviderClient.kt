package io.github.salomax.neotool.assistant.provider

import io.github.salomax.neotool.assistant.provider.model.ProviderCompletionChunk
import io.github.salomax.neotool.assistant.provider.model.ProviderCompletionRequest
import io.github.salomax.neotool.assistant.provider.model.ProviderCompletionResponse
import kotlinx.coroutines.flow.Flow

/**
 * Provider-agnostic completion contract used by the orchestration layer.
 */
interface LLMProviderClient {
    suspend fun complete(request: ProviderCompletionRequest): ProviderCompletionResponse

    fun streamComplete(request: ProviderCompletionRequest): Flow<ProviderCompletionChunk>

    fun estimateTokens(text: String): Int
}
