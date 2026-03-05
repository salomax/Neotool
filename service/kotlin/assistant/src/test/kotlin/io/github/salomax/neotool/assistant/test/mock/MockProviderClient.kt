package io.github.salomax.neotool.assistant.test.mock

import io.github.salomax.neotool.assistant.provider.LLMProviderClient
import io.github.salomax.neotool.assistant.provider.error.ErrorType
import io.github.salomax.neotool.assistant.provider.error.ProviderClientException
import io.github.salomax.neotool.assistant.provider.model.ProviderCompletionChunk
import io.github.salomax.neotool.assistant.provider.model.ProviderCompletionRequest
import io.github.salomax.neotool.assistant.provider.model.ProviderCompletionResponse
import io.github.salomax.neotool.assistant.provider.model.ProviderFinishReason
import io.github.salomax.neotool.assistant.provider.model.ProviderTokenUsage
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/**
 * Test double for [LLMProviderClient].
 *
 * Supports deterministic responses through queued outcomes and explicit error simulation.
 */
class MockProviderClient : LLMProviderClient {
    private val queuedOutcomes = ConcurrentLinkedQueue<Outcome>()
    private val capturedRequests = CopyOnWriteArrayList<ProviderCompletionRequest>()
    private val defaultResponseProvider =
        AtomicReference<
            (
                ProviderCompletionRequest,
            ) -> ProviderCompletionResponse,
        >(::defaultResponse)
    private val defaultDelayMs = AtomicLong(0L)

    override suspend fun complete(request: ProviderCompletionRequest): ProviderCompletionResponse {
        capturedRequests.add(request)

        val globalDelay = defaultDelayMs.get()
        if (globalDelay > 0) {
            delay(globalDelay)
        }

        return when (val outcome = queuedOutcomes.poll()) {
            null -> defaultResponseProvider.get().invoke(request)
            is Outcome.Response -> {
                if (outcome.delayMs > 0) {
                    delay(outcome.delayMs)
                }
                outcome.value
            }
            is Outcome.Failure -> {
                if (outcome.delayMs > 0) {
                    delay(outcome.delayMs)
                }
                throw outcome.error
            }
        }
    }

    override fun streamComplete(request: ProviderCompletionRequest): Flow<ProviderCompletionChunk> =
        flow {
            val response = complete(request.copy(stream = true))
            val content = response.content.orEmpty()
            if (content.isNotEmpty()) {
                emit(ProviderCompletionChunk(delta = content))
            }
            emit(
                ProviderCompletionChunk(
                    delta = "",
                    finishReason = response.finishReason ?: ProviderFinishReason.STOP,
                    usage = response.usage,
                ),
            )
        }

    override fun estimateTokens(text: String): Int {
        if (text.isBlank()) {
            return 0
        }

        val regexEstimate = GPT_TOKEN_PATTERN.findAll(text).count()
        val byteEstimate = (text.toByteArray(StandardCharsets.UTF_8).size + 3) / 4
        return maxOf(1, maxOf(regexEstimate, byteEstimate))
    }

    fun enqueueResponse(
        response: ProviderCompletionResponse,
        delayMs: Long = 0,
    ) {
        queuedOutcomes.add(Outcome.Response(response, delayMs))
    }

    fun enqueueFailure(
        error: ProviderClientException,
        delayMs: Long = 0,
    ) {
        queuedOutcomes.add(Outcome.Failure(error, delayMs))
    }

    fun enqueueRateLimit(
        message: String = "Rate limit exceeded",
        statusCode: Int = 429,
        delayMs: Long = 0,
    ) {
        enqueueFailure(
            ProviderClientException(
                errorType = ErrorType.RATE_LIMIT,
                message = message,
                statusCode = statusCode,
            ),
            delayMs = delayMs,
        )
    }

    fun enqueueTimeout(
        message: String = "Provider timeout",
        delayMs: Long = 0,
    ) {
        enqueueFailure(
            ProviderClientException(
                errorType = ErrorType.TIMEOUT,
                message = message,
            ),
            delayMs = delayMs,
        )
    }

    fun enqueueServerError(
        message: String = "Provider server error",
        statusCode: Int = 503,
        delayMs: Long = 0,
    ) {
        enqueueFailure(
            ProviderClientException(
                errorType = ErrorType.SERVER_ERROR,
                message = message,
                statusCode = statusCode,
            ),
            delayMs = delayMs,
        )
    }

    fun setDefaultResponse(response: ProviderCompletionResponse) {
        defaultResponseProvider.set { response }
    }

    fun setDefaultResponseProvider(provider: (ProviderCompletionRequest) -> ProviderCompletionResponse) {
        defaultResponseProvider.set(provider)
    }

    fun setDefaultDelayMs(delayMs: Long) {
        require(delayMs >= 0) { "delayMs must be >= 0" }
        defaultDelayMs.set(delayMs)
    }

    fun clearQueuedOutcomes() {
        queuedOutcomes.clear()
    }

    fun clearCapturedRequests() {
        capturedRequests.clear()
    }

    fun clear() {
        clearQueuedOutcomes()
        clearCapturedRequests()
        defaultResponseProvider.set(::defaultResponse)
        defaultDelayMs.set(0L)
    }

    fun capturedRequests(): List<ProviderCompletionRequest> = capturedRequests.toList()

    fun queuedOutcomeCount(): Int = queuedOutcomes.size

    private fun defaultResponse(request: ProviderCompletionRequest): ProviderCompletionResponse {
        return ProviderCompletionResponse(
            id = "mock-${capturedRequests.size}",
            content = "Mock response for model ${request.model}",
            finishReason = ProviderFinishReason.STOP,
            usage =
                ProviderTokenUsage(
                    promptTokens = estimateTokens(request.messages.joinToString("\n") { it.content.orEmpty() }),
                    completionTokens = estimateTokens("Mock response for model ${request.model}"),
                    totalTokens =
                        estimateTokens(request.messages.joinToString("\n") { it.content.orEmpty() }) +
                            estimateTokens("Mock response for model ${request.model}"),
                ),
            toolCalls = emptyList(),
        )
    }

    private sealed interface Outcome {
        data class Response(
            val value: ProviderCompletionResponse,
            val delayMs: Long,
        ) : Outcome

        data class Failure(
            val error: ProviderClientException,
            val delayMs: Long,
        ) : Outcome
    }

    companion object {
        private val GPT_TOKEN_PATTERN =
            Regex(
                """'s|'t|'re|'ve|'m|'ll|'d| ?\p{L}+| ?\p{N}+| ?[^\s\p{L}\p{N}]+|\s+(?!\S)|\s+""",
            )
    }
}
