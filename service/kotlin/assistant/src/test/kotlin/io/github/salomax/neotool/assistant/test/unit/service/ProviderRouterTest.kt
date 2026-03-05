package io.github.salomax.neotool.assistant.test.unit.service

import io.github.salomax.neotool.assistant.domain.enums.ModelStatus
import io.github.salomax.neotool.assistant.entity.LLMModelEntity
import io.github.salomax.neotool.assistant.entity.ModelRoutingRuleEntity
import io.github.salomax.neotool.assistant.repository.LLMModelRepository
import io.github.salomax.neotool.assistant.repository.ModelRoutingRuleRepository
import io.github.salomax.neotool.assistant.resilience.CircuitBreakerState
import io.github.salomax.neotool.assistant.service.NoAvailableModelException
import io.github.salomax.neotool.assistant.service.ProviderRouter
import io.github.salomax.neotool.assistant.service.ProviderRoutingRequest
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

class ProviderRouterTest {
    private lateinit var llmModelRepository: LLMModelRepository
    private lateinit var modelRoutingRuleRepository: ModelRoutingRuleRepository
    private lateinit var providerRouter: ProviderRouter

    @BeforeEach
    fun setUp() {
        llmModelRepository = mock()
        modelRoutingRuleRepository = mock()
        providerRouter = ProviderRouter(llmModelRepository, modelRoutingRuleRepository)
    }

    @Test
    fun `selectModel chooses next eligible model when primary provider circuit is OPEN`() {
        val providerA = UUID.randomUUID()
        val providerB = UUID.randomUUID()
        val modelA =
            model(
                modelName = "primary-cheap",
                providerId = providerA,
                costIn = "0.000001",
                costOut = "0.000001",
            )
        val modelB =
            model(
                modelName = "fallback-expensive",
                providerId = providerB,
                costIn = "0.000010",
                costOut = "0.000010",
            )

        whenever(llmModelRepository.findActiveModelsForRouting()).thenReturn(listOf(modelA, modelB))
        whenever(modelRoutingRuleRepository.findByEnabledTrueOrderByPriorityAscAndIdAsc()).thenReturn(emptyList())

        val selected =
            providerRouter.selectModel(
                ProviderRoutingRequest(
                    requiredCapabilities = listOf("JSON_MODE"),
                    estimatedPromptTokens = 100,
                    maxTokens = 200,
                    providerCircuitStates =
                        mapOf(
                            providerA to CircuitBreakerState.OPEN,
                            providerB to CircuitBreakerState.CLOSED,
                        ),
                ),
            )

        assertThat(selected.modelName).isEqualTo("fallback-expensive")
    }

    @Test
    fun `selectModel respects deterministic traffic shifting by routing key`() {
        val providerA = UUID.randomUUID()
        val providerB = UUID.randomUUID()
        val baselineModel =
            model(
                modelName = "baseline",
                providerId = providerA,
                costIn = "0.000001",
                costOut = "0.000001",
            )
        val shiftedModel =
            model(
                modelName = "shifted",
                providerId = providerB,
                costIn = "0.001000",
                costOut = "0.001000",
            )
        val shiftRule =
            ModelRoutingRuleEntity(
                id = UUID.randomUUID(),
                priority = 10,
                conditions = mapOf("service" to "api-gateway"),
                targetModelId = requireNotNull(shiftedModel.id),
                weightPercent = 35,
                enabled = true,
                createdAt = Instant.now(),
                updatedAt = Instant.now(),
            )

        whenever(llmModelRepository.findActiveModelsForRouting()).thenReturn(listOf(baselineModel, shiftedModel))
        whenever(modelRoutingRuleRepository.findByEnabledTrueOrderByPriorityAscAndIdAsc()).thenReturn(listOf(shiftRule))

        val selectedRoutingKey =
            (0..10_000)
                .asSequence()
                .map { index -> "routing-key-$index" }
                .firstOrNull { key ->
                    providerRouter
                        .selectModel(
                            ProviderRoutingRequest(
                                requestedByService = "api-gateway",
                                routingKey = key,
                                requiredCapabilities = listOf("JSON_MODE"),
                                estimatedPromptTokens = 100,
                                maxTokens = 50,
                            ),
                        ).modelName == "shifted"
                }

        assertThat(selectedRoutingKey).isNotNull

        val first =
            providerRouter.selectModel(
                ProviderRoutingRequest(
                    requestedByService = "api-gateway",
                    routingKey = selectedRoutingKey,
                    requiredCapabilities = listOf("JSON_MODE"),
                    estimatedPromptTokens = 100,
                    maxTokens = 50,
                ),
            )
        val second =
            providerRouter.selectModel(
                ProviderRoutingRequest(
                    requestedByService = "api-gateway",
                    routingKey = selectedRoutingKey,
                    requiredCapabilities = listOf("JSON_MODE"),
                    estimatedPromptTokens = 100,
                    maxTokens = 50,
                ),
            )

        assertThat(first.modelName).isEqualTo("shifted")
        assertThat(second.modelName).isEqualTo("shifted")
    }

    @Test
    fun `selectModel applies hard requirements before routing preferences`() {
        val preferredTarget =
            model(
                modelName = "rule-target",
                providerId = UUID.randomUUID(),
                capabilities = arrayOf("TOOLS"),
            )
        val eligibleModel =
            model(
                modelName = "eligible-json",
                providerId = UUID.randomUUID(),
                capabilities = arrayOf("JSON_MODE", "TOOLS"),
            )
        val rule =
            ModelRoutingRuleEntity(
                id = UUID.randomUUID(),
                priority = 1,
                conditions = emptyMap(),
                targetModelId = requireNotNull(preferredTarget.id),
                weightPercent = 100,
                enabled = true,
                createdAt = Instant.now(),
                updatedAt = Instant.now(),
            )

        whenever(llmModelRepository.findActiveModelsForRouting()).thenReturn(listOf(preferredTarget, eligibleModel))
        whenever(modelRoutingRuleRepository.findByEnabledTrueOrderByPriorityAscAndIdAsc()).thenReturn(listOf(rule))

        val selected =
            providerRouter.selectModel(
                ProviderRoutingRequest(
                    requiredCapabilities = listOf("JSON_MODE"),
                    estimatedPromptTokens = 200,
                    maxTokens = 100,
                ),
            )

        assertThat(selected.modelName).isEqualTo("eligible-json")
    }

    @Test
    fun `selectModel throws NoAvailableModelException when no model satisfies requirements`() {
        whenever(
            llmModelRepository.findActiveModelsForRouting(),
        ).thenReturn(
            listOf(
                model(
                    modelName = "small-context",
                    providerId = UUID.randomUUID(),
                    contextWindow = 100,
                ),
            ),
        )
        whenever(modelRoutingRuleRepository.findByEnabledTrueOrderByPriorityAscAndIdAsc()).thenReturn(emptyList())

        assertThatThrownBy {
            providerRouter.selectModel(
                ProviderRoutingRequest(
                    requiredCapabilities = listOf("JSON_MODE"),
                    estimatedPromptTokens = 600,
                    maxTokens = 600,
                ),
            )
        }
            .isInstanceOf(NoAvailableModelException::class.java)
            .hasMessageContaining("No ACTIVE model satisfies routing requirements")
    }

    private fun model(
        modelName: String,
        providerId: UUID,
        capabilities: Array<String> = arrayOf("JSON_MODE", "TOOLS"),
        contextWindow: Int = 200_000,
        maxOutputTokens: Int = 8_000,
        costIn: String = "0.000001",
        costOut: String = "0.000002",
    ): LLMModelEntity {
        return LLMModelEntity(
            id = UUID.randomUUID(),
            providerId = providerId,
            modelName = modelName,
            displayName = modelName,
            capabilities = capabilities,
            contextWindow = contextWindow,
            maxOutputTokens = maxOutputTokens,
            costPerInputToken = BigDecimal(costIn),
            costPerOutputToken = BigDecimal(costOut),
            status = ModelStatus.ACTIVE,
            createdAt = Instant.now(),
            updatedAt = Instant.now(),
        )
    }
}
