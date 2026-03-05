package io.github.salomax.neotool.assistant.test.unit.service

import com.fasterxml.jackson.databind.ObjectMapper
import io.github.salomax.neotool.assistant.domain.enums.ModelStatus
import io.github.salomax.neotool.assistant.domain.enums.OutputFormat
import io.github.salomax.neotool.assistant.domain.enums.PromptVersionStatus
import io.github.salomax.neotool.assistant.entity.CompletionRequestEntity
import io.github.salomax.neotool.assistant.entity.CompletionResponseEntity
import io.github.salomax.neotool.assistant.entity.LLMModelEntity
import io.github.salomax.neotool.assistant.entity.PromptVersionEntity
import io.github.salomax.neotool.assistant.entity.UsageLogEntity
import io.github.salomax.neotool.assistant.observability.AssistantMetrics
import io.github.salomax.neotool.assistant.provider.error.ErrorType
import io.github.salomax.neotool.assistant.provider.error.ProviderClientException
import io.github.salomax.neotool.assistant.provider.model.ProviderCompletionResponse
import io.github.salomax.neotool.assistant.provider.model.ProviderFinishReason
import io.github.salomax.neotool.assistant.provider.model.ProviderMessageRole
import io.github.salomax.neotool.assistant.provider.model.ProviderTokenUsage
import io.github.salomax.neotool.assistant.provider.model.ResponseFormatType
import io.github.salomax.neotool.assistant.provider.openai.OpenAIMapper
import io.github.salomax.neotool.assistant.repository.CompletionRequestRepository
import io.github.salomax.neotool.assistant.repository.CompletionResponseRepository
import io.github.salomax.neotool.assistant.repository.LLMModelRepository
import io.github.salomax.neotool.assistant.repository.PromptRepository
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
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

class CompletionServiceJsonModeTest {
    private lateinit var promptRepository: PromptRepository
    private lateinit var promptService: PromptService
    private lateinit var llmModelRepository: LLMModelRepository
    private lateinit var completionRequestRepository: CompletionRequestRepository
    private lateinit var completionResponseRepository: CompletionResponseRepository
    private lateinit var assistantMetrics: AssistantMetrics
    private lateinit var providerClient: MockProviderClient
    private lateinit var toolService: ToolService
    private lateinit var toolRegistry: ToolRegistry
    private lateinit var rateLimiterService: RateLimiterService
    private lateinit var usageBudgetService: UsageBudgetService
    private lateinit var usageTrackingService: UsageTrackingService
    private lateinit var openAIMapper: OpenAIMapper
    private lateinit var circuitBreaker: CircuitBreaker
    private lateinit var retryPolicy: RetryPolicy
    private lateinit var retryExecutor: RetryExecutor
    private lateinit var completionService: CompletionService

    @BeforeEach
    fun setUp() {
        promptRepository = mock()
        promptService = mock()
        llmModelRepository = mock()
        completionRequestRepository = mock()
        completionResponseRepository = mock()
        assistantMetrics = mock()
        providerClient = MockProviderClient()
        toolService = mock()
        toolRegistry = mock()
        rateLimiterService = mock()
        usageBudgetService = mock()
        usageTrackingService = mock()
        openAIMapper = OpenAIMapper(ObjectMapper())
        circuitBreaker = CircuitBreaker(CircuitBreakerConfig(), SystemTimeProvider())
        retryPolicy = RetryPolicy()
        retryExecutor = RetryExecutor { }
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
                openAIMapper = openAIMapper,
                circuitBreaker = circuitBreaker,
                retryPolicy = retryPolicy,
                retryExecutor = retryExecutor,
                assistantMetrics = assistantMetrics,
                usageTrackingService = usageTrackingService,
                objectMapper = ObjectMapper(),
            )
    }

    @Test
    fun `generateCompletion with outputFormat JSON sets provider json mode and stores outputFormat option`() {
        val accountId = UUID.randomUUID()
        stubActiveModelAndPersistence()

        providerClient.setDefaultResponse(
            ProviderCompletionResponse(
                id = "chatcmpl-json-mode",
                content = """{"answer":"ok"}""",
                finishReason = ProviderFinishReason.STOP,
                usage = ProviderTokenUsage(promptTokens = 9, completionTokens = 5, totalTokens = 14),
            ),
        )

        val input =
            CompletionInput(
                messages =
                    listOf(
                        CompletionMessageInput(
                            role = ProviderMessageRole.USER,
                            content = "Return JSON",
                        ),
                    ),
                outputFormat = OutputFormat.JSON,
                purpose = "assistant_json_mode",
                requestedByService = "assistant-test-service",
            )

        val result = runBlocking { completionService.generateCompletion(input, accountId) }

        assertThat(result.content).isEqualTo("""{"answer":"ok"}""")
        assertThat(providerClient.capturedRequests()).hasSize(1)
        assertThat(
            providerClient.capturedRequests().first().responseFormat?.type,
        ).isEqualTo(ResponseFormatType.JSON_OBJECT)

        val requestCaptor = argumentCaptor<CompletionRequestEntity>()
        verify(completionRequestRepository).save(requestCaptor.capture())
        val options = requestCaptor.firstValue.options
        assertThat(options).isNotNull
        assertThat(options!!["outputFormat"]).isEqualTo("JSON")
        assertThat(options["responseFormat"]).isEqualTo("JSON_OBJECT")
    }

    @Test
    fun `generateCompletion with outputFormat JSON throws typed error for malformed provider JSON`() {
        val accountId = UUID.randomUUID()
        stubActiveModelOnly()
        providerClient.setDefaultResponse(
            ProviderCompletionResponse(
                id = "chatcmpl-json-invalid",
                content = "{\"answer\":\"ok\"",
                finishReason = ProviderFinishReason.STOP,
                usage = ProviderTokenUsage(promptTokens = 3, completionTokens = 2, totalTokens = 5),
            ),
        )

        val input =
            CompletionInput(
                messages =
                    listOf(
                        CompletionMessageInput(
                            role = ProviderMessageRole.USER,
                            content = "Return invalid JSON",
                        ),
                    ),
                outputFormat = OutputFormat.JSON,
                purpose = "assistant_json_mode",
                requestedByService = "assistant-test-service",
            )

        assertThatThrownBy {
            runBlocking { completionService.generateCompletion(input, accountId) }
        }
            .isInstanceOfSatisfying(ProviderClientException::class.java) { exception ->
                assertThat(exception.errorType).isEqualTo(ErrorType.PROVIDER_RESPONSE_INVALID)
                assertThat(exception.message).contains("invalid JSON")
            }

        verify(completionRequestRepository, never()).save(any())
        verify(completionResponseRepository, never()).save(any())
    }

    @Test
    fun `generateCompletion applies prompt version outputFormat JSON when input outputFormat is not set`() {
        val accountId = UUID.randomUUID()
        val promptId = UUID.randomUUID()
        val promptVersionId = UUID.randomUUID()
        stubActiveModelAndPersistence()

        whenever(promptRepository.findActiveVersion(promptId)).thenReturn(
            PromptVersionEntity(
                id = promptVersionId,
                promptId = promptId,
                version = 3,
                status = PromptVersionStatus.ACTIVE,
                template = "Template",
                outputFormat = OutputFormat.JSON,
            ),
        )
        whenever(
            promptService.renderTemplate(promptVersionId, mapOf("customerName" to "Ana")),
        ).thenReturn("System prompt")

        providerClient.setDefaultResponse(
            ProviderCompletionResponse(
                id = "chatcmpl-prompt-json",
                content = """{"result":"ok"}""",
                finishReason = ProviderFinishReason.STOP,
                usage = ProviderTokenUsage(promptTokens = 8, completionTokens = 4, totalTokens = 12),
            ),
        )

        val input =
            CompletionInput(
                promptId = promptId,
                promptVariables = mapOf("customerName" to "Ana"),
                messages =
                    listOf(
                        CompletionMessageInput(
                            role = ProviderMessageRole.USER,
                            content = "Return account summary as JSON",
                        ),
                    ),
                purpose = "assistant_prompt_json_default",
                requestedByService = "assistant-test-service",
            )

        runBlocking { completionService.generateCompletion(input, accountId) }

        assertThat(providerClient.capturedRequests()).hasSize(1)
        assertThat(
            providerClient.capturedRequests().first().responseFormat?.type,
        ).isEqualTo(ResponseFormatType.JSON_OBJECT)
    }

    private fun stubActiveModelOnly() {
        whenever(llmModelRepository.findPrimaryActiveModel()).thenReturn(buildActiveModel())
    }

    private fun stubActiveModelAndPersistence() {
        stubActiveModelOnly()
        whenever(completionRequestRepository.save(any())).thenAnswer { invocation ->
            val request = invocation.arguments[0] as CompletionRequestEntity
            CompletionRequestEntity(
                id = UUID.randomUUID(),
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

    private fun buildActiveModel(): LLMModelEntity {
        return LLMModelEntity(
            id = UUID.randomUUID(),
            providerId = UUID.randomUUID(),
            modelName = "gpt-4o-mini",
            displayName = "GPT-4o Mini",
            capabilities = arrayOf("JSON_MODE", "TOOLS"),
            contextWindow = 128_000,
            maxOutputTokens = 4_096,
            costPerInputToken = BigDecimal("0.000001"),
            costPerOutputToken = BigDecimal("0.000002"),
            status = ModelStatus.ACTIVE,
            createdAt = Instant.now(),
            updatedAt = Instant.now(),
        )
    }
}
