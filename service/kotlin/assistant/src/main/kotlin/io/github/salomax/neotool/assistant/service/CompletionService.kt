package io.github.salomax.neotool.assistant.service

import com.fasterxml.jackson.databind.ObjectMapper
import io.github.salomax.neotool.assistant.domain.enums.BudgetScope
import io.github.salomax.neotool.assistant.domain.enums.ModelStatus
import io.github.salomax.neotool.assistant.domain.enums.OutputFormat
import io.github.salomax.neotool.assistant.domain.enums.ToolExecutionStatus
import io.github.salomax.neotool.assistant.entity.CompletionRequestEntity
import io.github.salomax.neotool.assistant.entity.CompletionResponseEntity
import io.github.salomax.neotool.assistant.entity.LLMModelEntity
import io.github.salomax.neotool.assistant.entity.ToolEntity
import io.github.salomax.neotool.assistant.observability.AssistantMetrics
import io.github.salomax.neotool.assistant.provider.LLMProviderClient
import io.github.salomax.neotool.assistant.provider.error.ErrorType
import io.github.salomax.neotool.assistant.provider.error.ProviderClientException
import io.github.salomax.neotool.assistant.provider.model.ProviderCompletionRequest
import io.github.salomax.neotool.assistant.provider.model.ProviderCompletionResponse
import io.github.salomax.neotool.assistant.provider.model.ProviderFinishReason
import io.github.salomax.neotool.assistant.provider.model.ProviderFunctionDefinition
import io.github.salomax.neotool.assistant.provider.model.ProviderMessage
import io.github.salomax.neotool.assistant.provider.model.ProviderMessageRole
import io.github.salomax.neotool.assistant.provider.model.ProviderTokenUsage
import io.github.salomax.neotool.assistant.provider.model.ProviderTool
import io.github.salomax.neotool.assistant.provider.model.ProviderToolCall
import io.github.salomax.neotool.assistant.provider.model.ResponseFormat
import io.github.salomax.neotool.assistant.provider.model.ResponseFormatType
import io.github.salomax.neotool.assistant.provider.openai.OpenAIMapper
import io.github.salomax.neotool.assistant.repository.CompletionRequestRepository
import io.github.salomax.neotool.assistant.repository.CompletionResponseRepository
import io.github.salomax.neotool.assistant.repository.LLMModelRepository
import io.github.salomax.neotool.assistant.repository.PromptRepository
import io.github.salomax.neotool.assistant.resilience.CircuitBreaker
import io.github.salomax.neotool.assistant.resilience.RetryExecutor
import io.github.salomax.neotool.assistant.resilience.RetryPolicy
import io.github.salomax.neotool.assistant.tool.JsonObject
import io.github.salomax.neotool.assistant.tool.ToolRegistry
import io.github.salomax.neotool.common.tracing.TraceExecution
import io.micronaut.core.annotation.Nullable
import jakarta.inject.Singleton
import jakarta.transaction.Transactional
import mu.KotlinLogging
import java.math.BigDecimal
import java.math.RoundingMode
import java.nio.charset.StandardCharsets
import java.util.UUID

@TraceExecution
@Singleton
open class CompletionService(
    private val promptRepository: PromptRepository,
    private val promptService: PromptService,
    private val llmModelRepository: LLMModelRepository,
    private val completionRequestRepository: CompletionRequestRepository,
    private val completionResponseRepository: CompletionResponseRepository,
    private val providerClient: LLMProviderClient,
    private val toolService: ToolService,
    private val toolRegistry: ToolRegistry,
    private val rateLimiterService: RateLimiterService,
    private val usageBudgetService: UsageBudgetService,
    private val openAIMapper: OpenAIMapper,
    private val circuitBreaker: CircuitBreaker,
    private val retryPolicy: RetryPolicy,
    private val retryExecutor: RetryExecutor,
    private val assistantMetrics: AssistantMetrics,
    private val usageTrackingService: UsageTrackingService,
    private val objectMapper: ObjectMapper,
    @param:Nullable private val providerRouter: ProviderRouter? = null,
) {
    private val logger = KotlinLogging.logger {}

    @Transactional
    open suspend fun generateCompletion(
        input: CompletionInput,
        accountId: UUID,
    ): CompletionResult {
        val requestStartedAtNanos = System.nanoTime()
        var requestStatus = REQUEST_STATUS_SUCCESS
        var modelTag = UNKNOWN_MODEL_TAG
        var outputFormatTag = (input.outputFormat ?: OutputFormat.TEXT).name
        var requestIdForLog: UUID? = null

        try {
            require(input.purpose.isNotBlank()) { "Completion purpose is required" }
            require(input.requestedByService.isNotBlank()) { "requestedByService is required" }
            validateClientMessages(input.messages)

            val promptContext = resolvePromptContext(input.promptId, input.promptVariables.orEmpty())
            val resolvedMessages = buildResolvedMessages(promptContext.systemPrompt, input.messages).toMutableList()
            require(resolvedMessages.isNotEmpty()) { "Resolved message list cannot be empty" }
            val effectiveOutputFormat = input.outputFormat ?: promptContext.outputFormat
            outputFormatTag = (effectiveOutputFormat ?: OutputFormat.TEXT).name

            val estimatedTokens = estimateTokens(resolvedMessages)
            val model =
                selectModel(
                    accountId = accountId,
                    modelPreference = input.modelPreference,
                    requiredCapabilities = input.requiredCapabilities,
                    estimatedPromptTokens = estimatedTokens,
                    maxTokens = input.maxTokens,
                    requestedByService = input.requestedByService,
                    purpose = input.purpose,
                    metadata = input.metadata,
                )
            modelTag = model.modelName
            val requestedTools = resolveRequestedTools(input.tools)
            val providerRequest =
                ProviderCompletionRequest(
                    model = model.modelName,
                    messages = resolvedMessages,
                    temperature = input.temperature,
                    maxTokens = input.maxTokens,
                    tools = requestedTools.values.map(::mapToolToProviderTool).takeIf { it.isNotEmpty() },
                    responseFormat = mapOutputFormat(effectiveOutputFormat),
                    stream = false,
                )

            logCompletionRequest(
                service = input.requestedByService,
                accountId = accountId,
                promptId = promptContext.promptId,
                purpose = input.purpose,
                model = model.modelName,
                outputFormat = outputFormatTag,
                estimatedTokens = estimatedTokens,
            )

            val rateLimitResult =
                rateLimiterService.checkAndIncrement(
                    providerScopeId = model.providerId,
                    accountScopeId = accountId,
                    service = input.requestedByService,
                    feature = input.purpose,
                    estimatedTokens = estimatedTokens,
                )
            if (!rateLimitResult.allowed) {
                val deniedScope = rateLimitResult.scope?.name ?: "UNKNOWN"
                assistantMetrics.incrementRateLimitExceeded(deniedScope)
                throw ProviderClientException(
                    errorType = ErrorType.RATE_LIMIT_EXCEEDED,
                    message = "Rate limit exceeded for scope=$deniedScope resetAt=${rateLimitResult.resetAt}",
                )
            }
            val estimatedCost = calculateEstimatedCost(model, estimatedTokens, input.maxTokens)
            enforceUsageBudgets(
                providerScopeId = model.providerId,
                accountId = accountId,
                service = input.requestedByService,
                feature = input.purpose,
                estimatedCost = estimatedCost,
            )

            val providerStartedAtNanos = System.nanoTime()
            val pendingToolExecutions = mutableListOf<PendingToolExecution>()
            var providerResponse = executeProviderRequest(providerRequest, requestIdForLog)
            var aggregatedUsage = providerResponse.usage ?: ProviderTokenUsage()
            var toolIterations = 0

            while (shouldRunToolLoop(providerResponse, toolIterations)) {
                resolvedMessages.add(
                    ProviderMessage(
                        role = ProviderMessageRole.ASSISTANT,
                        content = providerResponse.content,
                        toolCalls = providerResponse.toolCalls,
                    ),
                )

                for (toolCall in providerResponse.toolCalls) {
                    val toolResult = executeToolCall(toolCall, accountId, requestedTools)
                    toolResult.pendingExecution?.let(pendingToolExecutions::add)
                    resolvedMessages.add(
                        ProviderMessage(
                            role = ProviderMessageRole.TOOL,
                            content = toolResult.responseForModel,
                            name = toolCall.function.name,
                            toolCallId = toolCall.id,
                        ),
                    )
                }

                toolIterations += 1
                if (toolIterations >= MAX_TOOL_ITERATIONS) {
                    break
                }

                providerResponse =
                    executeProviderRequest(
                        providerRequest.copy(messages = resolvedMessages),
                        requestIdForLog,
                    )
                aggregatedUsage = mergeUsage(aggregatedUsage, providerResponse.usage ?: ProviderTokenUsage())
            }

            val normalizedContent =
                try {
                    normalizeFinalContent(providerResponse, effectiveOutputFormat)
                } catch (e: ProviderClientException) {
                    assistantMetrics.incrementProviderError(resolveProviderTag(), e.errorType.name)
                    logProviderError(
                        provider = resolveProviderTag(),
                        errorType = e.errorType.name,
                        statusCode = e.statusCode,
                        requestId = requestIdForLog,
                        detail = e.message,
                    )
                    throw e
                }

            val latencyMs =
                ((System.nanoTime() - providerStartedAtNanos) / 1_000_000L).coerceAtMost(
                    Int.MAX_VALUE.toLong(),
                ).toInt()
            val cost = calculateCost(model, aggregatedUsage)

            val request =
                completionRequestRepository.save(
                    CompletionRequestEntity(
                        accountId = accountId,
                        requestedByService = input.requestedByService.trim(),
                        userId = input.userId,
                        promptId = promptContext.promptId,
                        promptVersionId = promptContext.promptVersionId,
                        messages = resolvedMessages.map { messageToMap(it) },
                        variables = input.promptVariables?.takeIf { it.isNotEmpty() },
                        options = buildOptions(input, providerRequest, effectiveOutputFormat),
                        purpose = input.purpose.trim(),
                        metadata = input.metadata,
                    ),
                )
            val requestId = request.id ?: error("Completion request ID was not generated")
            requestIdForLog = requestId
            val modelId = model.id ?: error("Model ID is missing")

            pendingToolExecutions.forEach { execution ->
                toolService.recordExecution(
                    completionRequestId = requestId,
                    toolId = execution.toolId,
                    input = execution.input,
                    output = execution.output,
                    status = execution.status,
                    latencyMs = execution.latencyMs,
                    error = execution.error,
                )
            }

            val response =
                completionResponseRepository.save(
                    CompletionResponseEntity(
                        requestId = requestId,
                        accountId = accountId,
                        providerId = model.providerId,
                        modelId = modelId,
                        response =
                            providerResponseToMap(
                                providerResponse.id,
                                normalizedContent,
                                providerResponse.finishReason,
                                aggregatedUsage,
                                providerResponse.toolCalls,
                            ),
                        finishReason = providerResponse.finishReason?.value,
                        promptTokens = aggregatedUsage.promptTokens,
                        completionTokens = aggregatedUsage.completionTokens,
                        totalTokens = aggregatedUsage.totalTokens,
                        latencyMs = latencyMs,
                        cost = cost,
                        escalate = false,
                    ),
                )
            val responseId = response.id ?: error("Completion response ID was not generated")

            val result =
                CompletionResult(
                    id = responseId,
                    requestId = requestId,
                    accountId = accountId,
                    providerId = model.providerId,
                    modelId = modelId,
                    model = model.modelName,
                    content = normalizedContent,
                    finishReason = providerResponse.finishReason?.value ?: ProviderFinishReason.UNKNOWN.value,
                    usage = aggregatedUsage,
                    latencyMs = latencyMs,
                    cost = cost,
                    escalate = false,
                    toolCalls = providerResponse.toolCalls,
                )
            usageTrackingService.recordUsage(
                completionResponse = result,
                accountId = accountId,
                service = input.requestedByService,
                feature = COMPLETION_FEATURE,
                purpose = input.purpose,
            )

            assistantMetrics.recordTokenUsage(input.requestedByService, model.modelName, aggregatedUsage)
            assistantMetrics.recordCost(input.requestedByService, model.modelName, cost)
            recordUsageBudgetCost(
                providerScopeId = model.providerId,
                accountId = accountId,
                service = input.requestedByService,
                feature = input.purpose,
                cost = cost,
            )
            logCompletionResponse(
                requestId = requestId,
                model = model.modelName,
                usage = aggregatedUsage,
                latencyMs = latencyMs,
                cost = cost,
                escalate = false,
            )

            return result
        } catch (e: Exception) {
            requestStatus = REQUEST_STATUS_ERROR
            throw e
        } finally {
            val requestDurationMs =
                ((System.nanoTime() - requestStartedAtNanos) / 1_000_000L)
                    .coerceAtMost(Int.MAX_VALUE.toLong())
                    .toInt()
            assistantMetrics.recordRequest(
                service = input.requestedByService,
                model = modelTag,
                status = requestStatus,
                outputFormat = outputFormatTag,
                durationMs = requestDurationMs,
            )
        }
    }

    private fun resolveRequestedTools(requestedToolNames: List<String>?): Map<String, ToolEntity> {
        if (requestedToolNames.isNullOrEmpty()) {
            return emptyMap()
        }

        val requested =
            requestedToolNames
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .distinct()
        if (requested.isEmpty()) {
            return emptyMap()
        }

        val availableByName =
            toolService.getAvailableTools()
                .associateBy { it.name.trim() }
        val missing = requested.filterNot(availableByName::containsKey)
        require(missing.isEmpty()) {
            "Requested tools are not ACTIVE or not registered: ${missing.joinToString(", ")}"
        }

        return requested.associateWith { availableByName.getValue(it) }
    }

    private fun mapToolToProviderTool(tool: ToolEntity): ProviderTool {
        val function = openAIMapper.toolToOpenAIFunction(tool)
        return ProviderTool(
            function =
                ProviderFunctionDefinition(
                    name = function.name,
                    description = function.description,
                    parameters = function.parameters,
                ),
        )
    }

    private suspend fun executeProviderRequest(
        request: ProviderCompletionRequest,
        requestIdForLog: UUID?,
    ): ProviderCompletionResponse {
        val providerTag = resolveProviderTag()
        assistantMetrics.recordCircuitBreakerState(providerTag, circuitBreaker.currentState())

        return try {
            val response =
                circuitBreaker.execute {
                    retryExecutor.withRetry(retryPolicy) {
                        providerClient.complete(request)
                    }
                }
            assistantMetrics.recordCircuitBreakerState(providerTag, circuitBreaker.currentState())
            response
        } catch (e: ProviderClientException) {
            assistantMetrics.recordCircuitBreakerState(providerTag, circuitBreaker.currentState())
            assistantMetrics.incrementProviderError(providerTag, e.errorType.name)
            logProviderError(
                provider = providerTag,
                errorType = e.errorType.name,
                statusCode = e.statusCode,
                requestId = requestIdForLog,
                detail = e.message,
            )
            throw e
        } catch (e: Exception) {
            assistantMetrics.recordCircuitBreakerState(providerTag, circuitBreaker.currentState())
            assistantMetrics.incrementProviderError(providerTag, UNKNOWN_ERROR_TYPE_TAG)
            logProviderError(
                provider = providerTag,
                errorType = UNKNOWN_ERROR_TYPE_TAG,
                statusCode = null,
                requestId = requestIdForLog,
                detail = e.message,
            )
            throw e
        }
    }

    private fun shouldRunToolLoop(
        response: ProviderCompletionResponse,
        iteration: Int,
    ): Boolean {
        return response.finishReason == ProviderFinishReason.TOOL_CALLS &&
            response.toolCalls.isNotEmpty() &&
            iteration < MAX_TOOL_ITERATIONS
    }

    private suspend fun executeToolCall(
        toolCall: ProviderToolCall,
        accountId: UUID,
        availableToolsByName: Map<String, ToolEntity>,
    ): ToolExecutionResult {
        val toolName = toolCall.function.name.trim()
        val toolMetadata = availableToolsByName[toolName]
        val executionStartedAtNanos = System.nanoTime()

        val parsedInputResult = runCatching { openAIMapper.openAIToolCallToInput(toolCall) }
        val executionInput =
            parsedInputResult.getOrElse {
                mapOf("rawArguments" to toolCall.function.arguments)
            }
        val executionResult =
            parsedInputResult.fold(
                onSuccess = { parsedInput -> toolRegistry.execute(toolName, accountId, parsedInput) },
                onFailure = { failure -> Result.failure(failure) },
            )

        val latencyMs =
            ((System.nanoTime() - executionStartedAtNanos) / 1_000_000L).coerceAtMost(
                Int.MAX_VALUE.toLong(),
            ).toInt()
        return if (executionResult.isSuccess) {
            val output = executionResult.getOrThrow()
            ToolExecutionResult(
                responseForModel = toJsonContent(output),
                pendingExecution =
                    createPendingToolExecution(
                        tool = toolMetadata,
                        input = executionInput,
                        output = output,
                        status = ToolExecutionStatus.SUCCESS,
                        latencyMs = latencyMs,
                        error = null,
                    ),
            )
        } else {
            val errorMessage =
                executionResult.exceptionOrNull()?.message?.trim().takeUnless {
                    it.isNullOrEmpty()
                } ?: "Tool execution failed"
            val output = mapOf("error" to errorMessage)
            ToolExecutionResult(
                responseForModel = toJsonContent(output),
                pendingExecution =
                    createPendingToolExecution(
                        tool = toolMetadata,
                        input = executionInput,
                        output = output,
                        status = ToolExecutionStatus.FAILED,
                        latencyMs = latencyMs,
                        error = errorMessage,
                    ),
            )
        }
    }

    private fun createPendingToolExecution(
        tool: ToolEntity?,
        input: JsonObject,
        output: JsonObject?,
        status: ToolExecutionStatus,
        latencyMs: Int,
        error: String?,
    ): PendingToolExecution? {
        if (tool == null) {
            logger.warn { "Skipping tool execution persistence because tool metadata was not found" }
            return null
        }
        if (tool.id == null) {
            logger.warn { "Skipping tool execution persistence because tool ID is missing for tool=${tool.name}" }
            return null
        }
        val toolId = requireNotNull(tool.id)

        return PendingToolExecution(
            toolId = toolId,
            input = input,
            output = output,
            status = status,
            latencyMs = latencyMs,
            error = error,
        )
    }

    private fun toJsonContent(payload: JsonObject): String {
        return runCatching { objectMapper.writeValueAsString(payload) }
            .getOrElse { exception ->
                logger.warn(exception) { "Failed to serialize tool payload to JSON" }
                """{"error":"Tool payload serialization failed"}"""
            }
    }

    private fun normalizeFinalContent(
        providerResponse: ProviderCompletionResponse,
        outputFormat: OutputFormat?,
    ): String {
        if (providerResponse.finishReason == ProviderFinishReason.TOOL_CALLS) {
            return providerResponse.content.orEmpty()
        }
        return normalizeProviderContent(providerResponse.content, outputFormat)
    }

    private fun mergeUsage(
        left: ProviderTokenUsage,
        right: ProviderTokenUsage,
    ): ProviderTokenUsage {
        return ProviderTokenUsage(
            promptTokens = sumTokenValues(left.promptTokens, right.promptTokens),
            completionTokens = sumTokenValues(left.completionTokens, right.completionTokens),
            totalTokens = sumTokenValues(left.totalTokens, right.totalTokens),
        )
    }

    private fun sumTokenValues(
        left: Int,
        right: Int,
    ): Int {
        val sum = left.toLong() + right.toLong()
        return sum.coerceAtMost(Int.MAX_VALUE.toLong()).coerceAtLeast(0L).toInt()
    }

    private fun resolvePromptContext(
        promptId: UUID?,
        promptVariables: Map<String, Any?>,
    ): PromptContext {
        if (promptId == null) {
            return PromptContext(promptId = null, promptVersionId = null, systemPrompt = null, outputFormat = null)
        }

        val activeVersion =
            promptRepository.findActiveVersion(promptId)
                ?: throw IllegalArgumentException("Active prompt version not found for prompt: $promptId")
        val versionId = activeVersion.id ?: error("Prompt version ID is missing")

        val rendered = promptService.renderTemplate(versionId, promptVariables)
        return PromptContext(
            promptId = promptId,
            promptVersionId = versionId,
            systemPrompt = rendered,
            outputFormat = activeVersion.outputFormat,
        )
    }

    private fun buildResolvedMessages(
        systemPrompt: String?,
        inputMessages: List<CompletionMessageInput>,
    ): List<ProviderMessage> {
        val resolved = mutableListOf<ProviderMessage>()

        if (!systemPrompt.isNullOrBlank()) {
            resolved.add(ProviderMessage(role = ProviderMessageRole.SYSTEM, content = systemPrompt))
        }

        resolved.addAll(
            inputMessages.mapNotNull { input ->
                val content = input.content?.trim()?.takeIf { it.isNotEmpty() }
                if (content == null && input.toolCalls.isEmpty() && input.toolCallId.isNullOrBlank()) {
                    null
                } else {
                    ProviderMessage(
                        role = input.role,
                        content = content,
                        name = input.name?.trim()?.takeIf { it.isNotEmpty() },
                        toolCallId = input.toolCallId?.trim()?.takeIf { it.isNotEmpty() },
                        toolCalls = input.toolCalls,
                    )
                }
            },
        )

        return resolved
    }

    private fun validateClientMessages(messages: List<CompletionMessageInput>) {
        require(messages.size <= MAX_CLIENT_MESSAGES) {
            "Too many input messages: ${messages.size} (max=$MAX_CLIENT_MESSAGES)"
        }

        var totalChars = 0
        messages.forEachIndexed { index, message ->
            require(message.role != ProviderMessageRole.SYSTEM && message.role != ProviderMessageRole.TOOL) {
                "Input message role ${message.role.name} is not allowed at index=$index"
            }
            require(message.toolCalls.isEmpty()) {
                "Input toolCalls are not allowed at index=$index"
            }
            require(message.toolCallId.isNullOrBlank()) {
                "Input toolCallId is not allowed at index=$index"
            }
            require(message.name.isNullOrBlank()) {
                "Input message name is not allowed at index=$index"
            }

            val contentLength = message.content?.length ?: 0
            require(contentLength <= MAX_MESSAGE_CONTENT_CHARS) {
                "Input message content too long at index=$index (max=$MAX_MESSAGE_CONTENT_CHARS)"
            }
            totalChars += contentLength
        }

        require(totalChars <= MAX_TOTAL_MESSAGE_CHARS) {
            "Total input content too large: $totalChars (max=$MAX_TOTAL_MESSAGE_CHARS)"
        }
    }

    private fun selectModel(
        accountId: UUID,
        modelPreference: String?,
        requiredCapabilities: List<String>,
        estimatedPromptTokens: Int,
        maxTokens: Int?,
        requestedByService: String,
        purpose: String,
        metadata: Map<String, Any?>?,
    ): LLMModelEntity {
        providerRouter?.let { router ->
            return router.selectModel(
                ProviderRoutingRequest(
                    accountId = accountId,
                    modelPreference = modelPreference,
                    requiredCapabilities = requiredCapabilities,
                    estimatedPromptTokens = estimatedPromptTokens,
                    maxTokens = maxTokens,
                    requestedByService = requestedByService,
                    purpose = purpose,
                    metadata = metadata,
                    routingKey =
                        listOf(
                            accountId.toString(),
                            requestedByService.trim(),
                            purpose.trim(),
                            modelPreference?.trim().orEmpty(),
                            requiredCapabilities.joinToString(",") { it.trim().uppercase() },
                        ).joinToString("|"),
                    providerCircuitStates = emptyMap(),
                ),
            )
        }

        val capabilities =
            requiredCapabilities
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .map { it.uppercase() }

        val preferred = modelPreference?.trim()?.takeIf { it.isNotEmpty() }
        if (preferred != null) {
            val preferredModel =
                llmModelRepository.findPrimaryActiveModelByName(preferred)
                    ?: throw IllegalArgumentException("No ACTIVE model found for preference: $preferred")
            require(hasCapabilities(preferredModel, capabilities)) {
                "Preferred model '$preferred' does not satisfy required capabilities: ${capabilities.joinToString(
                    ", ",
                )}"
            }
            return preferredModel
        }

        if (capabilities.isEmpty()) {
            return llmModelRepository.findPrimaryActiveModel()
                ?: throw IllegalStateException("No ACTIVE model found in ACTIVE providers")
        }

        val candidates =
            llmModelRepository.findByStatusAndCapabilitiesContaining(
                ModelStatus.ACTIVE,
                capabilities.first(),
            )
        return candidates.firstOrNull { hasCapabilities(it, capabilities) }
            ?: throw IllegalArgumentException(
                "No ACTIVE model satisfies required capabilities: ${capabilities.joinToString(", ")}",
            )
    }

    private fun hasCapabilities(
        model: LLMModelEntity,
        required: List<String>,
    ): Boolean {
        if (required.isEmpty()) {
            return true
        }
        val available = model.capabilities.map { it.uppercase() }.toSet()
        return required.all { it in available }
    }

    private fun mapOutputFormat(outputFormat: OutputFormat?): ResponseFormat? {
        return when (outputFormat) {
            null -> null
            OutputFormat.TEXT -> null
            OutputFormat.JSON -> ResponseFormat(type = ResponseFormatType.JSON_OBJECT)
        }
    }

    private fun calculateCost(
        model: LLMModelEntity,
        usage: ProviderTokenUsage,
    ): BigDecimal {
        val promptCost = model.costPerInputToken.multiply(BigDecimal.valueOf(usage.promptTokens.toLong()))
        val completionCost = model.costPerOutputToken.multiply(BigDecimal.valueOf(usage.completionTokens.toLong()))
        return promptCost.add(completionCost).setScale(6, RoundingMode.HALF_UP)
    }

    private fun calculateEstimatedCost(
        model: LLMModelEntity,
        estimatedPromptTokens: Int,
        maxTokens: Int?,
    ): BigDecimal {
        val promptTokens = estimatedPromptTokens.coerceAtLeast(0).toLong()
        val completionTokens = maxTokens?.coerceAtLeast(0)?.toLong() ?: 0L
        val promptCost = model.costPerInputToken.multiply(BigDecimal.valueOf(promptTokens))
        val completionCost = model.costPerOutputToken.multiply(BigDecimal.valueOf(completionTokens))
        return promptCost.add(completionCost).setScale(6, RoundingMode.HALF_UP)
    }

    private fun enforceUsageBudgets(
        providerScopeId: UUID,
        accountId: UUID,
        service: String,
        feature: String,
        estimatedCost: BigDecimal,
    ) {
        for (scopeCheck in buildBudgetScopeChecks(providerScopeId, accountId, service, feature)) {
            val result = usageBudgetService.checkBudget(scopeCheck.scope, scopeCheck.scopeId, estimatedCost)
            if (result.checkedAnyBudget && !result.allowed) {
                throw ProviderClientException(
                    errorType = ErrorType.INVALID_REQUEST,
                    message =
                        "Usage budget exceeded for scope=${scopeCheck.scope.name} percentUsed=${result.percentUsed}",
                )
            }
        }
    }

    private fun recordUsageBudgetCost(
        providerScopeId: UUID,
        accountId: UUID,
        service: String,
        feature: String,
        cost: BigDecimal,
    ) {
        for (scopeCheck in buildBudgetScopeChecks(providerScopeId, accountId, service, feature)) {
            usageBudgetService.recordCost(scopeCheck.scope, scopeCheck.scopeId, cost)
        }
    }

    private fun buildBudgetScopeChecks(
        providerScopeId: UUID,
        accountId: UUID,
        service: String,
        feature: String,
    ): List<BudgetScopeCheck> {
        return listOf(
            BudgetScopeCheck(BudgetScope.PROVIDER, providerScopeId),
            BudgetScopeCheck(BudgetScope.ACCOUNT, accountId),
            BudgetScopeCheck(BudgetScope.SERVICE, scopeIdFromName(BudgetScope.SERVICE, service.trim())),
            BudgetScopeCheck(BudgetScope.FEATURE, scopeIdFromName(BudgetScope.FEATURE, feature.trim())),
        )
    }

    private fun scopeIdFromName(
        scope: BudgetScope,
        value: String,
    ): UUID {
        return UUID.nameUUIDFromBytes("${scope.name}:${value.lowercase()}".toByteArray(StandardCharsets.UTF_8))
    }

    private fun buildOptions(
        input: CompletionInput,
        providerRequest: ProviderCompletionRequest,
        effectiveOutputFormat: OutputFormat?,
    ): Map<String, Any?> {
        val options = linkedMapOf<String, Any?>()
        providerRequest.temperature?.let { options["temperature"] = it }
        providerRequest.maxTokens?.let { options["maxTokens"] = it }
        options["stream"] = providerRequest.stream
        input.modelPreference?.trim()?.takeIf { it.isNotEmpty() }?.let { options["modelPreference"] = it }
        if (input.requiredCapabilities.isNotEmpty()) {
            options["requiredCapabilities"] = input.requiredCapabilities
        }
        effectiveOutputFormat?.let { options["outputFormat"] = it.name }
        providerRequest.responseFormat?.let { options["responseFormat"] = it.type.name }
        if (!input.tools.isNullOrEmpty()) {
            options["tools"] = input.tools
        }
        return options.ifEmpty { emptyMap() }
    }

    private fun messageToMap(message: ProviderMessage): Map<String, Any?> {
        val map = linkedMapOf<String, Any?>("role" to message.role.name, "content" to message.content)
        message.name?.let { map["name"] = it }
        message.toolCallId?.let { map["toolCallId"] = it }
        if (message.toolCalls.isNotEmpty()) {
            map["toolCalls"] = message.toolCalls.map { toolCallToMap(it) }
        }
        return map
    }

    private fun providerResponseToMap(
        id: String,
        content: String?,
        finishReason: ProviderFinishReason?,
        usage: ProviderTokenUsage,
        toolCalls: List<ProviderToolCall>,
    ): Map<String, Any?> {
        return mapOf(
            "id" to id,
            "content" to content,
            "finishReason" to (finishReason?.value ?: ProviderFinishReason.UNKNOWN.value),
            "usage" to
                mapOf(
                    "promptTokens" to usage.promptTokens,
                    "completionTokens" to usage.completionTokens,
                    "totalTokens" to usage.totalTokens,
                ),
            "toolCalls" to toolCalls.map { toolCallToMap(it) },
        )
    }

    private fun toolCallToMap(toolCall: ProviderToolCall): Map<String, Any?> {
        return mapOf(
            "id" to toolCall.id,
            "type" to toolCall.type.name,
            "function" to
                mapOf(
                    "name" to toolCall.function.name,
                    "arguments" to toolCall.function.arguments,
                ),
        )
    }

    private fun resolveProviderTag(): String {
        val className = providerClient::class.java.simpleName.substringBefore('$')
        return className.removeSuffix("ProviderClient").removeSuffix("Client").ifBlank { "unknown" }
    }

    private fun estimateTokens(messages: List<ProviderMessage>): Int {
        return runCatching {
            messages.sumOf { message ->
                providerClient.estimateTokens(message.content.orEmpty())
            }
        }.getOrDefault(0).coerceAtLeast(0)
    }

    private fun normalizeProviderContent(
        rawContent: String?,
        outputFormat: OutputFormat?,
    ): String {
        val content = rawContent.orEmpty()
        if (outputFormat != OutputFormat.JSON) {
            return content
        }

        val trimmed = content.trim()
        if (trimmed.isEmpty()) {
            throw ProviderClientException(
                errorType = ErrorType.PROVIDER_RESPONSE_INVALID,
                message = "Provider returned empty content for JSON output format",
            )
        }

        try {
            objectMapper.readTree(trimmed)
        } catch (e: Exception) {
            throw ProviderClientException(
                errorType = ErrorType.PROVIDER_RESPONSE_INVALID,
                message = "Provider returned invalid JSON content for output format JSON",
                cause = e,
            )
        }

        return trimmed
    }

    private fun logCompletionRequest(
        service: String,
        accountId: UUID,
        promptId: UUID?,
        purpose: String,
        model: String,
        outputFormat: String,
        estimatedTokens: Int,
    ) {
        logger.info {
            buildStructuredLog(
                event = "completion_request",
                fields =
                    linkedMapOf(
                        "service" to service.trim(),
                        "accountId" to accountId.toString(),
                        "promptId" to promptId?.toString(),
                        "purpose" to normalizeStructuredField(purpose),
                        "model" to model,
                        "outputFormat" to outputFormat,
                        "estimatedTokens" to estimatedTokens.coerceAtLeast(0),
                    ),
            )
        }
    }

    private fun logCompletionResponse(
        requestId: UUID,
        model: String,
        usage: ProviderTokenUsage,
        latencyMs: Int,
        cost: BigDecimal,
        escalate: Boolean,
    ) {
        logger.info {
            buildStructuredLog(
                event = "completion_response",
                fields =
                    linkedMapOf(
                        "requestId" to requestId.toString(),
                        "model" to model,
                        "promptTokens" to usage.promptTokens.coerceAtLeast(0),
                        "completionTokens" to usage.completionTokens.coerceAtLeast(0),
                        "totalTokens" to usage.totalTokens.coerceAtLeast(0),
                        "latencyMs" to latencyMs.coerceAtLeast(0),
                        "cost" to cost.max(BigDecimal.ZERO).setScale(6, RoundingMode.HALF_UP),
                        "escalate" to escalate,
                    ),
            )
        }
    }

    private fun logProviderError(
        provider: String,
        errorType: String,
        statusCode: Int?,
        requestId: UUID?,
        detail: String?,
    ) {
        logger.error {
            buildStructuredLog(
                event = "provider_error",
                fields =
                    linkedMapOf(
                        "provider" to provider,
                        "errorType" to errorType,
                        "statusCode" to statusCode,
                        "requestId" to requestId?.toString(),
                        "detail" to detail?.take(PROVIDER_ERROR_DETAIL_MAX_LEN),
                    ),
            )
        }
    }

    private fun buildStructuredLog(
        event: String,
        fields: LinkedHashMap<String, Any?>,
    ): String {
        val data = linkedMapOf<String, Any?>("event" to event)
        data.putAll(fields)
        return data.entries.joinToString(" ") { (key, value) -> "$key=${formatLogValue(value)}" }
    }

    private fun formatLogValue(value: Any?): String {
        if (value == null) return "null"
        val text = value.toString()
        return if (text.any { it.isWhitespace() }) {
            "\"${text.replace("\"", "'")}\""
        } else {
            text
        }
    }

    private fun normalizeStructuredField(value: String): String {
        val normalized = value.trim().lowercase().replace(STRUCTURED_FIELD_INVALID_REGEX, "_").take(80)
        return normalized.ifBlank { "unknown" }
    }

    private data class PromptContext(
        val promptId: UUID?,
        val promptVersionId: UUID?,
        val systemPrompt: String?,
        val outputFormat: OutputFormat?,
    )

    private data class PendingToolExecution(
        val toolId: UUID,
        val input: JsonObject,
        val output: JsonObject?,
        val status: ToolExecutionStatus,
        val latencyMs: Int,
        val error: String?,
    )

    private data class ToolExecutionResult(
        val responseForModel: String,
        val pendingExecution: PendingToolExecution?,
    )

    private data class BudgetScopeCheck(
        val scope: BudgetScope,
        val scopeId: UUID,
    )

    companion object {
        private const val MAX_TOOL_ITERATIONS = 5
        private const val MAX_CLIENT_MESSAGES = 100
        private const val MAX_MESSAGE_CONTENT_CHARS = 20_000
        private const val MAX_TOTAL_MESSAGE_CHARS = 100_000
        private const val REQUEST_STATUS_SUCCESS = "success"
        private const val REQUEST_STATUS_ERROR = "error"
        private const val UNKNOWN_MODEL_TAG = "unknown"
        private const val UNKNOWN_ERROR_TYPE_TAG = "unknown"
        private const val COMPLETION_FEATURE = "generateCompletion"
        private const val PROVIDER_ERROR_DETAIL_MAX_LEN = 500
        private val STRUCTURED_FIELD_INVALID_REGEX = Regex("[^a-z0-9._-]")
    }
}

data class CompletionInput(
    val promptId: UUID? = null,
    val promptVariables: Map<String, Any?>? = null,
    val messages: List<CompletionMessageInput> = emptyList(),
    val modelPreference: String? = null,
    val requiredCapabilities: List<String> = emptyList(),
    val temperature: Double? = null,
    val maxTokens: Int? = null,
    val outputFormat: OutputFormat? = null,
    val tools: List<String>? = null,
    val purpose: String,
    val metadata: Map<String, Any?>? = null,
    val requestedByService: String = "assistant",
    val userId: UUID? = null,
)

data class CompletionMessageInput(
    val role: ProviderMessageRole,
    val content: String? = null,
    val name: String? = null,
    val toolCallId: String? = null,
    val toolCalls: List<ProviderToolCall> = emptyList(),
)

data class CompletionResult(
    val id: UUID,
    val requestId: UUID,
    val accountId: UUID,
    val providerId: UUID,
    val modelId: UUID,
    val model: String,
    val content: String,
    val finishReason: String,
    val usage: ProviderTokenUsage,
    val latencyMs: Int,
    val cost: BigDecimal,
    val escalate: Boolean,
    val toolCalls: List<ProviderToolCall> = emptyList(),
)
