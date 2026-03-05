package io.github.salomax.neotool.assistant.graphql.resolver

import io.github.salomax.neotool.assistant.domain.enums.OutputFormat
import io.github.salomax.neotool.assistant.entity.CompletionRequestEntity
import io.github.salomax.neotool.assistant.entity.CompletionResponseEntity
import io.github.salomax.neotool.assistant.entity.LLMModelEntity
import io.github.salomax.neotool.assistant.graphql.dto.CompletionChunkDTO
import io.github.salomax.neotool.assistant.graphql.dto.TokenUsageDTO
import io.github.salomax.neotool.assistant.llm.MessageRole
import io.github.salomax.neotool.assistant.observability.AssistantMetrics
import io.github.salomax.neotool.assistant.provider.LLMProviderClient
import io.github.salomax.neotool.assistant.provider.model.ProviderCompletionRequest
import io.github.salomax.neotool.assistant.provider.model.ProviderFinishReason
import io.github.salomax.neotool.assistant.provider.model.ProviderMessage
import io.github.salomax.neotool.assistant.provider.model.ProviderMessageRole
import io.github.salomax.neotool.assistant.provider.model.ProviderTokenUsage
import io.github.salomax.neotool.assistant.repository.CompletionRequestRepository
import io.github.salomax.neotool.assistant.repository.CompletionResponseRepository
import io.github.salomax.neotool.assistant.repository.LLMModelRepository
import io.github.salomax.neotool.assistant.service.CompletionResult
import io.github.salomax.neotool.assistant.service.ConversationService
import io.github.salomax.neotool.assistant.service.EscalationDetector
import io.github.salomax.neotool.assistant.service.UsageTrackingService
import io.micronaut.core.annotation.Nullable
import jakarta.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import org.reactivestreams.Publisher
import org.reactivestreams.Subscriber
import org.reactivestreams.Subscription
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

@Singleton
class StreamingResolver(
    private val conversationService: ConversationService,
    private val providerClient: LLMProviderClient,
    private val escalationDetector: EscalationDetector,
    private val assistantMetrics: AssistantMetrics,
    @param:Nullable private val llmModelRepository: LLMModelRepository? = null,
    @param:Nullable private val completionRequestRepository: CompletionRequestRepository? = null,
    @param:Nullable private val completionResponseRepository: CompletionResponseRepository? = null,
    @param:Nullable private val usageTrackingService: UsageTrackingService? = null,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun messageStream(
        conversationId: String,
        input: Map<String, Any?>,
        accountId: UUID,
        requestedByService: String,
        userId: UUID?,
    ): Publisher<CompletionChunkDTO> {
        return Publisher { subscriber: Subscriber<in CompletionChunkDTO> ->
            val cancelled = AtomicBoolean(false)
            var modelTag = DEFAULT_STREAM_MODEL
            val startedAt = System.nanoTime()
            var requestStatus = REQUEST_STATUS_SUCCESS
            var job: kotlinx.coroutines.Job? = null

            subscriber.onSubscribe(
                object : Subscription {
                    override fun request(n: Long) {
                        // Streaming push model does not use demand control for now.
                    }

                    override fun cancel() {
                        cancelled.set(true)
                        job?.cancel()
                    }
                },
            )

            job =
                scope.launch {
                    try {
                        val context = conversationService.getOrCreateContext(accountId, conversationId)
                        ensureSystemMessage(context)
                        val userMessage = extractRequiredContent(input)
                        context.addMessage(MessageRole.USER, userMessage)
                        conversationService.saveContext(accountId, context)

                        val model = llmModelRepository?.findPrimaryActiveModel()
                        val modelName = model?.modelName ?: DEFAULT_STREAM_MODEL
                        modelTag = modelName
                        val providerRequest =
                            ProviderCompletionRequest(
                                model = modelName,
                                messages = context.messages.map(::toProviderMessage),
                                stream = true,
                            )

                        val accumulated = StringBuilder()
                        val terminalDelta = StringBuilder()
                        var terminalFinishReason: ProviderFinishReason? = null
                        var terminalUsage: ProviderTokenUsage? = null

                        providerClient.streamComplete(providerRequest).collect { chunk ->
                            if (cancelled.get()) {
                                return@collect
                            }

                            if (chunk.delta.isNotEmpty()) {
                                accumulated.append(chunk.delta)
                            }

                            if (chunk.finishReason != null || chunk.usage != null) {
                                if (chunk.delta.isNotEmpty()) {
                                    terminalDelta.append(chunk.delta)
                                }
                                terminalFinishReason = chunk.finishReason ?: terminalFinishReason
                                terminalUsage = chunk.usage ?: terminalUsage
                            } else {
                                subscriber.onNext(
                                    CompletionChunkDTO(
                                        delta = chunk.delta,
                                        finishReason = null,
                                        usage = null,
                                        escalate = false,
                                    ),
                                )
                            }
                        }

                        if (cancelled.get()) {
                            return@launch
                        }

                        val finalContent = accumulated.toString()
                        val finishReason = terminalFinishReason ?: ProviderFinishReason.STOP
                        val usage = terminalUsage ?: ProviderTokenUsage()
                        val escalation = escalationDetector.detect(finalContent)

                        context.addMessage(MessageRole.ASSISTANT, finalContent)
                        if (escalation.escalate) {
                            val reason = escalation.reason ?: UNKNOWN_ESCALATION_REASON
                            context.markEscalated(reason)
                            assistantMetrics.incrementEscalation(reason)
                        }
                        conversationService.saveContext(accountId, context)

                        persistCompletion(
                            contextMessages = context.messages.map(::toProviderMessage),
                            accountId = accountId,
                            requestedByService = requestedByService,
                            userId = userId,
                            purpose = STREAM_PURPOSE,
                            model = model,
                            modelName = modelName,
                            content = finalContent,
                            finishReason = finishReason,
                            usage = usage,
                            escalate = escalation.escalate,
                        )

                        subscriber.onNext(
                            CompletionChunkDTO(
                                delta = terminalDelta.toString(),
                                finishReason = finishReason.value,
                                usage =
                                    TokenUsageDTO(
                                        promptTokens = usage.promptTokens,
                                        completionTokens = usage.completionTokens,
                                        totalTokens = usage.totalTokens,
                                    ),
                                escalate = escalation.escalate,
                            ),
                        )
                        subscriber.onComplete()
                    } catch (exception: Exception) {
                        requestStatus = REQUEST_STATUS_ERROR
                        if (!cancelled.get()) {
                            subscriber.onError(exception)
                        }
                    } finally {
                        val durationMs = ((System.nanoTime() - startedAt) / 1_000_000L).toInt().coerceAtLeast(0)
                        assistantMetrics.recordRequest(
                            service = requestedByService,
                            model = modelTag,
                            status = requestStatus,
                            outputFormat = OutputFormat.TEXT.name,
                            durationMs = durationMs,
                            stream = true,
                        )
                    }
                }
        }
    }

    @jakarta.annotation.PreDestroy
    fun close() {
        scope.cancel()
    }

    private fun ensureSystemMessage(context: io.github.salomax.neotool.assistant.agent.ConversationContext) {
        if (context.messages.none { it.role == MessageRole.SYSTEM }) {
            context.addSystemMessage(DEFAULT_STREAM_SYSTEM_PROMPT)
        }
    }

    private fun extractRequiredContent(input: Map<String, Any?>): String {
        val content = input["content"]?.toString()?.trim().orEmpty()
        require(content.isNotEmpty()) { "Field 'input.content' is required for messageStream" }
        return content
    }

    private fun toProviderMessage(message: io.github.salomax.neotool.assistant.llm.ChatMessage): ProviderMessage {
        val role =
            when (message.role) {
                MessageRole.SYSTEM -> ProviderMessageRole.SYSTEM
                MessageRole.USER -> ProviderMessageRole.USER
                MessageRole.ASSISTANT -> ProviderMessageRole.ASSISTANT
                MessageRole.FUNCTION -> ProviderMessageRole.TOOL
            }

        return ProviderMessage(
            role = role,
            content = message.content,
        )
    }

    private fun persistCompletion(
        contextMessages: List<ProviderMessage>,
        accountId: UUID,
        requestedByService: String,
        userId: UUID?,
        purpose: String,
        model: LLMModelEntity?,
        modelName: String,
        content: String,
        finishReason: ProviderFinishReason,
        usage: ProviderTokenUsage,
        escalate: Boolean,
    ) {
        val requestRepository = completionRequestRepository ?: return
        val responseRepository = completionResponseRepository ?: return
        val modelId = model?.id ?: return

        val request =
            requestRepository.save(
                CompletionRequestEntity(
                    accountId = accountId,
                    requestedByService = requestedByService,
                    userId = userId,
                    messages =
                        contextMessages.map { message ->
                            mapOf(
                                "role" to message.role.name,
                                "content" to message.content,
                            )
                        },
                    purpose = purpose,
                    options = mapOf("stream" to true),
                ),
            )
        val requestId = request.id ?: return
        val cost = calculateCost(model, usage)

        val response =
            responseRepository.save(
                CompletionResponseEntity(
                    requestId = requestId,
                    accountId = accountId,
                    providerId = model.providerId,
                    modelId = modelId,
                    response =
                        mapOf(
                            "content" to content,
                            "finishReason" to finishReason.value,
                            "usage" to
                                mapOf(
                                    "promptTokens" to usage.promptTokens,
                                    "completionTokens" to usage.completionTokens,
                                    "totalTokens" to usage.totalTokens,
                                ),
                            "model" to modelName,
                        ),
                    finishReason = finishReason.value,
                    promptTokens = usage.promptTokens,
                    completionTokens = usage.completionTokens,
                    totalTokens = usage.totalTokens,
                    cost = cost,
                    escalate = escalate,
                ),
            )

        val responseId = response.id ?: return
        usageTrackingService?.recordUsage(
            completionResponse =
                CompletionResult(
                    id = responseId,
                    requestId = requestId,
                    accountId = accountId,
                    providerId = model.providerId,
                    modelId = modelId,
                    model = modelName,
                    content = content,
                    finishReason = finishReason.value,
                    usage = usage,
                    latencyMs = 0,
                    cost = cost,
                    escalate = escalate,
                ),
            accountId = accountId,
            service = requestedByService,
            feature = STREAM_FEATURE,
            purpose = purpose,
        )
    }

    private fun calculateCost(
        model: LLMModelEntity?,
        usage: ProviderTokenUsage,
    ): BigDecimal {
        if (model == null) {
            return BigDecimal.ZERO.setScale(6, RoundingMode.HALF_UP)
        }
        val promptCost = model.costPerInputToken.multiply(BigDecimal.valueOf(usage.promptTokens.toLong()))
        val completionCost = model.costPerOutputToken.multiply(BigDecimal.valueOf(usage.completionTokens.toLong()))
        return promptCost.add(completionCost).setScale(6, RoundingMode.HALF_UP)
    }

    companion object {
        private const val DEFAULT_STREAM_SYSTEM_PROMPT = "<enter the system prompt here>"
        private const val DEFAULT_STREAM_MODEL = "gpt-4o-mini"
        private const val STREAM_PURPOSE = "chat_stream"
        private const val STREAM_FEATURE = "messageStream"
        private const val REQUEST_STATUS_SUCCESS = "success"
        private const val REQUEST_STATUS_ERROR = "error"
        private const val UNKNOWN_ESCALATION_REASON = "unknown"
    }
}
