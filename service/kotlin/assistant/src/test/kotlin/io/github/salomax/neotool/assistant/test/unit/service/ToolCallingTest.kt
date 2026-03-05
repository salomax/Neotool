package io.github.salomax.neotool.assistant.test.unit.service

import com.fasterxml.jackson.databind.ObjectMapper
import io.github.salomax.neotool.assistant.domain.enums.ModelStatus
import io.github.salomax.neotool.assistant.domain.enums.ToolExecutionStatus
import io.github.salomax.neotool.assistant.domain.enums.ToolStatus
import io.github.salomax.neotool.assistant.entity.CompletionRequestEntity
import io.github.salomax.neotool.assistant.entity.CompletionResponseEntity
import io.github.salomax.neotool.assistant.entity.LLMModelEntity
import io.github.salomax.neotool.assistant.entity.ToolEntity
import io.github.salomax.neotool.assistant.entity.ToolExecutionEntity
import io.github.salomax.neotool.assistant.entity.UsageLogEntity
import io.github.salomax.neotool.assistant.observability.AssistantMetrics
import io.github.salomax.neotool.assistant.provider.model.ProviderCompletionResponse
import io.github.salomax.neotool.assistant.provider.model.ProviderFinishReason
import io.github.salomax.neotool.assistant.provider.model.ProviderFunctionCall
import io.github.salomax.neotool.assistant.provider.model.ProviderMessageRole
import io.github.salomax.neotool.assistant.provider.model.ProviderTokenUsage
import io.github.salomax.neotool.assistant.provider.model.ProviderToolCall
import io.github.salomax.neotool.assistant.provider.openai.OpenAIMapper
import io.github.salomax.neotool.assistant.repository.CompletionRequestRepository
import io.github.salomax.neotool.assistant.repository.CompletionResponseRepository
import io.github.salomax.neotool.assistant.repository.LLMModelRepository
import io.github.salomax.neotool.assistant.repository.PromptRepository
import io.github.salomax.neotool.assistant.repository.ToolExecutionRepository
import io.github.salomax.neotool.assistant.repository.ToolRepository
import io.github.salomax.neotool.assistant.resilience.CircuitBreaker
import io.github.salomax.neotool.assistant.resilience.CircuitBreakerConfig
import io.github.salomax.neotool.assistant.resilience.RetryExecutor
import io.github.salomax.neotool.assistant.resilience.RetryPolicy
import io.github.salomax.neotool.assistant.resilience.SystemTimeProvider
import io.github.salomax.neotool.assistant.service.BudgetCheckResult
import io.github.salomax.neotool.assistant.service.CompletionInput
import io.github.salomax.neotool.assistant.service.CompletionMessageInput
import io.github.salomax.neotool.assistant.service.CompletionResult
import io.github.salomax.neotool.assistant.service.CompletionService
import io.github.salomax.neotool.assistant.service.PromptService
import io.github.salomax.neotool.assistant.service.RateLimitResult
import io.github.salomax.neotool.assistant.service.RateLimiterService
import io.github.salomax.neotool.assistant.service.ToolService
import io.github.salomax.neotool.assistant.service.UsageBudgetService
import io.github.salomax.neotool.assistant.service.UsageTrackingService
import io.github.salomax.neotool.assistant.test.mock.MockProviderClient
import io.github.salomax.neotool.assistant.tool.ToolRegistry
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

class ToolCallingTest {
    private lateinit var promptRepository: PromptRepository
    private lateinit var promptService: PromptService
    private lateinit var llmModelRepository: LLMModelRepository
    private lateinit var completionRequestRepository: CompletionRequestRepository
    private lateinit var completionResponseRepository: CompletionResponseRepository
    private lateinit var toolRepository: ToolRepository
    private lateinit var toolExecutionRepository: ToolExecutionRepository
    private lateinit var toolService: ToolService
    private lateinit var rateLimiterService: RateLimiterService
    private lateinit var usageBudgetService: UsageBudgetService
    private lateinit var usageTrackingService: UsageTrackingService
    private lateinit var assistantMetrics: AssistantMetrics
    private lateinit var providerClient: MockProviderClient
    private lateinit var toolRegistry: ToolRegistry
    private lateinit var completionService: CompletionService

    @BeforeEach
    fun setUp() {
        promptRepository = mock()
        promptService = mock()
        llmModelRepository = mock()
        completionRequestRepository = mock()
        completionResponseRepository = mock()
        toolRepository = mock()
        toolExecutionRepository = mock()
        toolService = ToolService(toolRepository, toolExecutionRepository)
        rateLimiterService = mock()
        usageBudgetService = mock()
        usageTrackingService = mock()
        assistantMetrics = mock()
        providerClient = MockProviderClient()
        toolRegistry = ToolRegistry()
        whenever(rateLimiterService.checkAndIncrement(any(), any(), any(), any(), any())).thenReturn(
            RateLimitResult(
                allowed = true,
                scope = null,
                remaining = Long.MAX_VALUE,
                resetAt = null,
            ),
        )
        whenever(usageBudgetService.checkBudget(any(), any(), any())).thenReturn(
            BudgetCheckResult(
                allowed = true,
                percentUsed = BigDecimal.ZERO,
                checkedAnyBudget = false,
            ),
        )
        whenever(usageBudgetService.recordCost(any(), any(), any())).thenReturn(emptyList())
        whenever(usageTrackingService.recordUsage(any(), any(), any(), any(), any())).thenAnswer { invocation ->
            val completionResult = invocation.arguments[0] as CompletionResult
            UsageLogEntity(
                id = UUID.randomUUID(),
                accountId = completionResult.accountId,
                requestId = completionResult.requestId,
                service = invocation.arguments[2] as String,
                feature = invocation.arguments[3] as String,
                purpose = invocation.arguments[4] as String,
                promptTokens = completionResult.usage.promptTokens,
                completionTokens = completionResult.usage.completionTokens,
                totalTokens = completionResult.usage.totalTokens,
                cost = completionResult.cost,
                modelId = completionResult.modelId,
            )
        }

        val objectMapper = ObjectMapper()
        completionService =
            CompletionService(
                promptRepository = promptRepository,
                promptService = promptService,
                llmModelRepository = llmModelRepository,
                completionRequestRepository = completionRequestRepository,
                completionResponseRepository = completionResponseRepository,
                providerClient = providerClient,
                toolService = toolService,
                toolRegistry = toolRegistry,
                rateLimiterService = rateLimiterService,
                usageBudgetService = usageBudgetService,
                openAIMapper = OpenAIMapper(objectMapper),
                circuitBreaker = CircuitBreaker(CircuitBreakerConfig(), SystemTimeProvider()),
                retryPolicy = RetryPolicy(),
                retryExecutor = RetryExecutor { },
                assistantMetrics = assistantMetrics,
                usageTrackingService = usageTrackingService,
                objectMapper = objectMapper,
            )

        whenever(toolExecutionRepository.save(any())).thenAnswer { invocation ->
            val row = invocation.arguments[0] as ToolExecutionEntity
            ToolExecutionEntity(
                id = UUID.randomUUID(),
                completionRequestId = row.completionRequestId,
                toolId = row.toolId,
                input = row.input,
                output = row.output,
                status = row.status,
                error = row.error,
                latencyMs = row.latencyMs,
                executedAt = row.executedAt,
            )
        }
    }

    @Test
    fun `single tool call is resolved in two LLM turns`() {
        val requestId = UUID.randomUUID()
        stubActiveModelAndPersistence(requestId)

        val tool = buildTool(name = "get_current_time")
        whenever(toolRepository.findByStatus(ToolStatus.ACTIVE)).thenReturn(listOf(tool))
        toolRegistry.register("get_current_time") { _, _ ->
            Result.success(
                mapOf(
                    "datetime" to "2026-03-04T12:00:00Z",
                    "timezone" to "UTC",
                ),
            )
        }

        providerClient.enqueueResponse(
            ProviderCompletionResponse(
                id = "chat-1",
                content = null,
                finishReason = ProviderFinishReason.TOOL_CALLS,
                usage = ProviderTokenUsage(promptTokens = 5, completionTokens = 2, totalTokens = 7),
                toolCalls =
                    listOf(
                        ProviderToolCall(
                            id = "call_1",
                            function = ProviderFunctionCall(name = "get_current_time", arguments = "{}"),
                        ),
                    ),
            ),
        )
        providerClient.enqueueResponse(
            ProviderCompletionResponse(
                id = "chat-2",
                content = "Current time is 2026-03-04T12:00:00Z",
                finishReason = ProviderFinishReason.STOP,
                usage = ProviderTokenUsage(promptTokens = 4, completionTokens = 2, totalTokens = 6),
            ),
        )

        val result =
            runBlocking {
                completionService.generateCompletion(
                    CompletionInput(
                        messages =
                            listOf(
                                CompletionMessageInput(role = ProviderMessageRole.USER, content = "what time is it?"),
                            ),
                        tools = listOf("get_current_time"),
                        purpose = "tool_test",
                        requestedByService = "assistant-test-service",
                    ),
                    UUID.randomUUID(),
                )
            }

        assertThat(result.finishReason).isEqualTo("stop")
        assertThat(result.content).contains("Current time")
        assertThat(result.usage.totalTokens).isEqualTo(13)
        assertThat(providerClient.capturedRequests()).hasSize(2)

        val firstRequestTools = providerClient.capturedRequests().first().tools
        assertThat(firstRequestTools).hasSize(1)
        assertThat(firstRequestTools!!.first().function.name).isEqualTo("get_current_time")

        val secondRequestMessages = providerClient.capturedRequests()[1].messages
        assertThat(secondRequestMessages).anySatisfy { message ->
            assertThat(message.role).isEqualTo(ProviderMessageRole.TOOL)
            assertThat(message.toolCallId).isEqualTo("call_1")
            assertThat(message.content).contains("\"timezone\":\"UTC\"")
        }

        val executionCaptor = argumentCaptor<ToolExecutionEntity>()
        verify(toolExecutionRepository).save(executionCaptor.capture())
        assertThat(executionCaptor.firstValue.completionRequestId).isEqualTo(requestId)
        assertThat(executionCaptor.firstValue.status).isEqualTo(ToolExecutionStatus.SUCCESS)
        assertThat(executionCaptor.firstValue.input).isEqualTo(emptyMap<String, Any?>())
    }

    @Test
    fun `max tool iterations stops repeated tool-call loop`() {
        stubActiveModelAndPersistence(UUID.randomUUID())

        val tool = buildTool(name = "get_current_time")
        whenever(toolRepository.findByStatus(ToolStatus.ACTIVE)).thenReturn(listOf(tool))
        toolRegistry.register(
            "get_current_time",
        ) { _, _ -> Result.success(mapOf("datetime" to "2026-03-04T12:00:00Z", "timezone" to "UTC")) }

        providerClient.setDefaultResponseProvider {
            ProviderCompletionResponse(
                id = "chat-loop-${providerClient.capturedRequests().size}",
                content = "need tool",
                finishReason = ProviderFinishReason.TOOL_CALLS,
                usage = ProviderTokenUsage(promptTokens = 1, completionTokens = 1, totalTokens = 2),
                toolCalls =
                    listOf(
                        ProviderToolCall(
                            id = "call-${providerClient.capturedRequests().size}",
                            function = ProviderFunctionCall(name = "get_current_time", arguments = "{}"),
                        ),
                    ),
            )
        }

        val result =
            runBlocking {
                completionService.generateCompletion(
                    CompletionInput(
                        messages = listOf(CompletionMessageInput(role = ProviderMessageRole.USER, content = "loop")),
                        tools = listOf("get_current_time"),
                        purpose = "tool_loop_test",
                        requestedByService = "assistant-test-service",
                    ),
                    UUID.randomUUID(),
                )
            }

        assertThat(providerClient.capturedRequests()).hasSize(5)
        assertThat(result.finishReason).isEqualTo("tool_calls")
        assertThat(result.usage.totalTokens).isEqualTo(10)
        verify(toolExecutionRepository, times(5)).save(any())
    }

    @Test
    fun `tool execution failure is persisted and forwarded to LLM context`() {
        val requestId = UUID.randomUUID()
        stubActiveModelAndPersistence(requestId)

        val tool = buildTool(name = "get_balance")
        whenever(toolRepository.findByStatus(ToolStatus.ACTIVE)).thenReturn(listOf(tool))
        toolRegistry.register("get_balance") { _, _ -> Result.failure(IllegalStateException("downstream timeout")) }

        providerClient.enqueueResponse(
            ProviderCompletionResponse(
                id = "chat-fail-1",
                content = null,
                finishReason = ProviderFinishReason.TOOL_CALLS,
                usage = ProviderTokenUsage(promptTokens = 3, completionTokens = 1, totalTokens = 4),
                toolCalls =
                    listOf(
                        ProviderToolCall(
                            id = "call_fail_1",
                            function =
                                ProviderFunctionCall(
                                    name = "get_balance",
                                    arguments = "{\"accountId\":\"acc-1\"}",
                                ),
                        ),
                    ),
            ),
        )
        providerClient.enqueueResponse(
            ProviderCompletionResponse(
                id = "chat-fail-2",
                content = "I could not retrieve balance right now.",
                finishReason = ProviderFinishReason.STOP,
                usage = ProviderTokenUsage(promptTokens = 2, completionTokens = 2, totalTokens = 4),
            ),
        )

        val result =
            runBlocking {
                completionService.generateCompletion(
                    CompletionInput(
                        messages =
                            listOf(
                                CompletionMessageInput(role = ProviderMessageRole.USER, content = "get balance"),
                            ),
                        tools = listOf("get_balance"),
                        purpose = "tool_failure_test",
                        requestedByService = "assistant-test-service",
                    ),
                    UUID.randomUUID(),
                )
            }

        assertThat(result.content).contains("could not retrieve balance")
        assertThat(providerClient.capturedRequests()).hasSize(2)

        val executionCaptor = argumentCaptor<ToolExecutionEntity>()
        verify(toolExecutionRepository).save(executionCaptor.capture())
        assertThat(executionCaptor.firstValue.completionRequestId).isEqualTo(requestId)
        assertThat(executionCaptor.firstValue.status).isEqualTo(ToolExecutionStatus.FAILED)
        assertThat(executionCaptor.firstValue.error).contains("downstream timeout")
        assertThat(executionCaptor.firstValue.output).containsEntry("error", "downstream timeout")

        val secondRequestMessages = providerClient.capturedRequests()[1].messages
        assertThat(secondRequestMessages).anySatisfy { message ->
            assertThat(message.role).isEqualTo(ProviderMessageRole.TOOL)
            assertThat(message.content).contains("downstream timeout")
        }
    }

    private fun stubActiveModelAndPersistence(requestId: UUID) {
        val providerId = UUID.randomUUID()
        val modelId = UUID.randomUUID()

        whenever(llmModelRepository.findPrimaryActiveModel()).thenReturn(
            LLMModelEntity(
                id = modelId,
                providerId = providerId,
                modelName = "gpt-4o-mini",
                displayName = "GPT-4o Mini",
                capabilities = arrayOf("TOOLS", "JSON_MODE"),
                contextWindow = 128_000,
                maxOutputTokens = 4_096,
                costPerInputToken = BigDecimal("0.000001"),
                costPerOutputToken = BigDecimal("0.000002"),
                status = ModelStatus.ACTIVE,
                createdAt = Instant.now(),
                updatedAt = Instant.now(),
            ),
        )

        whenever(completionRequestRepository.save(any())).thenAnswer { invocation ->
            val request = invocation.arguments[0] as CompletionRequestEntity
            CompletionRequestEntity(
                id = requestId,
                accountId = request.accountId,
                requestedByService = request.requestedByService,
                userId = request.userId,
                promptId = request.promptId,
                promptVersionId = request.promptVersionId,
                messages = request.messages,
                variables = request.variables,
                options = request.options,
                purpose = request.purpose,
                metadata = request.metadata,
                createdAt = request.createdAt,
            )
        }

        whenever(completionResponseRepository.save(any())).thenAnswer { invocation ->
            val response = invocation.arguments[0] as CompletionResponseEntity
            CompletionResponseEntity(
                id = UUID.randomUUID(),
                requestId = response.requestId,
                accountId = response.accountId,
                providerId = response.providerId,
                modelId = response.modelId,
                response = response.response,
                finishReason = response.finishReason,
                promptTokens = response.promptTokens,
                completionTokens = response.completionTokens,
                totalTokens = response.totalTokens,
                latencyMs = response.latencyMs,
                cost = response.cost,
                escalate = response.escalate,
                completedAt = response.completedAt,
            )
        }
    }

    private fun buildTool(name: String): ToolEntity {
        return ToolEntity(
            id = UUID.randomUUID(),
            name = name,
            description = "Built-in tool $name",
            category = "builtin",
            inputSchema = mapOf("type" to "object", "properties" to emptyMap<String, Any?>()),
            outputSchema = mapOf("type" to "object"),
            executorService = "assistant",
            executorEndpoint = "/builtin/$name",
            timeoutMs = 1_000,
            status = ToolStatus.ACTIVE,
        )
    }
}
