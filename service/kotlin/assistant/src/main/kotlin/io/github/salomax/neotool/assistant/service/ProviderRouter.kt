package io.github.salomax.neotool.assistant.service

import io.github.salomax.neotool.assistant.entity.LLMModelEntity
import io.github.salomax.neotool.assistant.entity.ModelRoutingRuleEntity
import io.github.salomax.neotool.assistant.repository.LLMModelRepository
import io.github.salomax.neotool.assistant.repository.ModelRoutingRuleRepository
import io.github.salomax.neotool.assistant.resilience.CircuitBreakerState
import io.github.salomax.neotool.common.tracing.TraceExecution
import jakarta.inject.Singleton
import java.math.BigDecimal
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.UUID

/**
 * Selects the best eligible LLM model using hard constraints + weighted preferences.
 */
@TraceExecution
@Singleton
open class ProviderRouter(
    private val llmModelRepository: LLMModelRepository,
    private val modelRoutingRuleRepository: ModelRoutingRuleRepository,
) {
    open fun selectModel(request: ProviderRoutingRequest): LLMModelEntity {
        val requiredCapabilities = normalizeCapabilities(request.requiredCapabilities)
        val preferredModel = request.modelPreference?.trim()?.takeIf { it.isNotEmpty() }
        val requiredMaxTokens = request.maxTokens?.coerceAtLeast(0) ?: 0
        val requiredContextTokens = sumTokens(request.estimatedPromptTokens, requiredMaxTokens)

        val activeModels = llmModelRepository.findActiveModelsForRouting()

        val eligible =
            activeModels.filter { model ->
                isEligible(
                    model = model,
                    requiredCapabilities = requiredCapabilities,
                    preferredModel = preferredModel,
                    requiredContextTokens = requiredContextTokens,
                    requiredMaxTokens = requiredMaxTokens,
                    circuitState = request.providerCircuitStates[model.providerId] ?: CircuitBreakerState.CLOSED,
                )
            }

        if (eligible.isEmpty()) {
            throw NoAvailableModelException(
                "No ACTIVE model satisfies routing requirements " +
                    "(preferredModel=${preferredModel ?: "none"}, " +
                    "requiredCapabilities=${requiredCapabilities.joinToString(",")}, " +
                    "requiredContextTokens=$requiredContextTokens)",
            )
        }

        val rulePreferredTargetId = selectPreferredTargetFromRules(request, eligible)
        val scoredCandidates =
            eligible
                .map { model ->
                    val circuitState = request.providerCircuitStates[model.providerId] ?: CircuitBreakerState.CLOSED
                    ScoredCandidate(
                        model = model,
                        score =
                            scoreModel(
                                model = model,
                                request = request,
                                preferredTargetId = rulePreferredTargetId,
                                circuitState = circuitState,
                            ),
                    )
                }
                .sortedWith(
                    compareBy<ScoredCandidate> { it.score }
                        .thenBy { it.model.modelName.lowercase() }
                        .thenBy { it.model.id?.toString().orEmpty() },
                )

        return scoredCandidates.first().model
    }

    private fun isEligible(
        model: LLMModelEntity,
        requiredCapabilities: Set<String>,
        preferredModel: String?,
        requiredContextTokens: Int,
        requiredMaxTokens: Int,
        circuitState: CircuitBreakerState,
    ): Boolean {
        if (preferredModel != null && !model.modelName.equals(preferredModel, ignoreCase = true)) {
            return false
        }

        if (circuitState == CircuitBreakerState.OPEN) {
            return false
        }

        if (model.contextWindow < requiredContextTokens) {
            return false
        }

        if (requiredMaxTokens > 0 && model.maxOutputTokens < requiredMaxTokens) {
            return false
        }

        if (requiredCapabilities.isEmpty()) {
            return true
        }

        val available = model.capabilities.map { it.trim().uppercase() }.toSet()
        return requiredCapabilities.all { capability -> capability in available }
    }

    private fun scoreModel(
        model: LLMModelEntity,
        request: ProviderRoutingRequest,
        preferredTargetId: UUID?,
        circuitState: CircuitBreakerState,
    ): Double {
        val promptTokens = request.estimatedPromptTokens.coerceAtLeast(0).toLong()
        val completionTokens = request.maxTokens?.coerceAtLeast(0)?.toLong() ?: 0L
        val estimatedCost =
            model.costPerInputToken.multiply(BigDecimal.valueOf(promptTokens))
                .add(model.costPerOutputToken.multiply(BigDecimal.valueOf(completionTokens)))
        val costComponent = estimatedCost.toDouble() * COST_WEIGHT

        val latencyMs =
            model.id?.let { modelId -> request.modelLatencyMs[modelId] }
                ?: request.providerLatencyMs[model.providerId]
                ?: DEFAULT_LATENCY_MS
        val latencyComponent = latencyMs.toDouble() * LATENCY_WEIGHT

        val circuitComponent =
            when (circuitState) {
                CircuitBreakerState.CLOSED -> 0.0
                CircuitBreakerState.HALF_OPEN -> HALF_OPEN_PENALTY
                CircuitBreakerState.OPEN -> OPEN_PENALTY
            }

        val routingPreferenceComponent =
            if (preferredTargetId != null && preferredTargetId == model.id) {
                -ROUTING_RULE_BONUS
            } else {
                0.0
            }

        return costComponent + latencyComponent + circuitComponent + routingPreferenceComponent
    }

    private fun selectPreferredTargetFromRules(
        request: ProviderRoutingRequest,
        eligibleModels: List<LLMModelEntity>,
    ): UUID? {
        if (eligibleModels.isEmpty()) {
            return null
        }

        val rules = modelRoutingRuleRepository.findByEnabledTrueOrderByPriorityAscAndIdAsc()
        if (rules.isEmpty()) {
            return null
        }

        val eligibleById = eligibleModels.associateBy { it.id }
        val requestCapabilities = normalizeCapabilities(request.requiredCapabilities)
        val routingKey = buildRoutingKey(request, requestCapabilities)

        for (rule in rules) {
            val target = eligibleById[rule.targetModelId] ?: continue
            if (!matchesConditions(rule, request, requestCapabilities)) {
                continue
            }
            if (!isSelectedByWeight(rule, routingKey)) {
                continue
            }
            return target.id
        }

        return null
    }

    private fun matchesConditions(
        rule: ModelRoutingRuleEntity,
        request: ProviderRoutingRequest,
        requestCapabilities: Set<String>,
    ): Boolean {
        if (rule.conditions.isEmpty()) {
            return true
        }

        for ((key, rawValue) in rule.conditions) {
            when (key) {
                "service", "requestedByService" -> {
                    val expected = rawValue?.toString()?.trim().orEmpty()
                    if (expected.isNotEmpty() &&
                        !expected.equals(
                            request.requestedByService.trim(),
                            ignoreCase = true,
                        )
                    ) {
                        return false
                    }
                }

                "purpose" -> {
                    val expected = rawValue?.toString()?.trim().orEmpty()
                    if (expected.isNotEmpty() && !expected.equals(request.purpose.trim(), ignoreCase = true)) {
                        return false
                    }
                }

                "modelPreference" -> {
                    val expected = rawValue?.toString()?.trim().orEmpty()
                    val actual = request.modelPreference?.trim().orEmpty()
                    if (expected.isNotEmpty() && !expected.equals(actual, ignoreCase = true)) {
                        return false
                    }
                }

                "requiredCapabilities" -> {
                    val expected = normalizeCapabilities(extractStringList(rawValue))
                    if (!requestCapabilities.containsAll(expected)) {
                        return false
                    }
                }

                "requiredCapabilitiesAny" -> {
                    val expected = normalizeCapabilities(extractStringList(rawValue))
                    if (expected.isNotEmpty() && requestCapabilities.none { capability -> capability in expected }) {
                        return false
                    }
                }

                "metadata" -> {
                    val expected = rawValue as? Map<*, *> ?: return false
                    if (!matchesMetadata(expected, request.metadata.orEmpty())) {
                        return false
                    }
                }

                else -> return false
            }
        }

        return true
    }

    private fun matchesMetadata(
        expected: Map<*, *>,
        actual: Map<String, Any?>,
    ): Boolean {
        for ((expectedKey, expectedValue) in expected) {
            val key = expectedKey?.toString() ?: return false
            val actualValue = actual[key]
            if (actualValue?.toString() != expectedValue?.toString()) {
                return false
            }
        }
        return true
    }

    private fun isSelectedByWeight(
        rule: ModelRoutingRuleEntity,
        routingKey: String,
    ): Boolean {
        val weight = rule.weightPercent.coerceIn(0, 100)
        if (weight == 0) {
            return false
        }
        if (weight == 100) {
            return true
        }

        val ruleSeed = "${rule.id}:$routingKey"
        val bucket = deterministicBucket(ruleSeed)
        return bucket < weight
    }

    private fun deterministicBucket(seed: String): Int {
        val digest = MessageDigest.getInstance("SHA-256").digest(seed.toByteArray(StandardCharsets.UTF_8))
        val value = ByteBuffer.wrap(digest.copyOfRange(0, 4)).int and Int.MAX_VALUE
        return value % 100
    }

    private fun buildRoutingKey(
        request: ProviderRoutingRequest,
        requestCapabilities: Set<String>,
    ): String {
        request.routingKey?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }

        val metadataSegment =
            request.metadata
                .orEmpty()
                .entries
                .sortedBy { it.key }
                .joinToString("&") { (key, value) -> "$key=${value?.toString().orEmpty()}" }

        return buildString {
            append(request.accountId?.toString().orEmpty())
            append('|')
            append(request.requestedByService.trim())
            append('|')
            append(request.purpose.trim())
            append('|')
            append(request.modelPreference?.trim().orEmpty())
            append('|')
            append(requestCapabilities.sorted().joinToString(","))
            append('|')
            append(metadataSegment)
        }
    }

    private fun normalizeCapabilities(capabilities: List<String>): Set<String> {
        return capabilities.map { it.trim() }.filter { it.isNotEmpty() }.map { it.uppercase() }.toSet()
    }

    private fun extractStringList(value: Any?): List<String> {
        return when (value) {
            is List<*> -> value.mapNotNull { item -> item?.toString()?.trim()?.takeIf { it.isNotEmpty() } }
            is Array<*> -> value.mapNotNull { item -> item?.toString()?.trim()?.takeIf { it.isNotEmpty() } }
            is String -> value.split(',').map { it.trim() }.filter { it.isNotEmpty() }
            else -> emptyList()
        }
    }

    private fun sumTokens(
        promptTokens: Int,
        completionTokens: Int,
    ): Int {
        val sum = promptTokens.coerceAtLeast(0).toLong() + completionTokens.coerceAtLeast(0).toLong()
        return sum.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }

    private data class ScoredCandidate(
        val model: LLMModelEntity,
        val score: Double,
    )

    companion object {
        private const val COST_WEIGHT = 1_000.0
        private const val LATENCY_WEIGHT = 1.0
        private const val ROUTING_RULE_BONUS = 250_000.0
        private const val HALF_OPEN_PENALTY = 25_000.0
        private const val OPEN_PENALTY = 1_000_000.0
        private const val DEFAULT_LATENCY_MS = 250L
    }
}

data class ProviderRoutingRequest(
    val accountId: UUID? = null,
    val modelPreference: String? = null,
    val requiredCapabilities: List<String> = emptyList(),
    val estimatedPromptTokens: Int = 0,
    val maxTokens: Int? = null,
    val requestedByService: String = "",
    val purpose: String = "",
    val metadata: Map<String, Any?>? = null,
    val routingKey: String? = null,
    val providerCircuitStates: Map<UUID, CircuitBreakerState> = emptyMap(),
    val modelLatencyMs: Map<UUID, Long> = emptyMap(),
    val providerLatencyMs: Map<UUID, Long> = emptyMap(),
)

class NoAvailableModelException(
    message: String,
) : RuntimeException(message)
