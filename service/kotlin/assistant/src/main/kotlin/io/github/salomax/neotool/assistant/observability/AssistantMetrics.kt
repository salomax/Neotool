package io.github.salomax.neotool.assistant.observability

import io.github.salomax.neotool.assistant.provider.model.ProviderTokenUsage
import io.github.salomax.neotool.assistant.resilience.CircuitBreakerState
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import jakarta.inject.Singleton
import java.math.BigDecimal
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@Singleton
open class AssistantMetrics(
    private val meterRegistry: MeterRegistry? = null,
) {
    private val circuitBreakerStateValues = ConcurrentHashMap<String, AtomicInteger>()

    open fun recordRequest(
        service: String,
        model: String,
        status: String,
        outputFormat: String,
        durationMs: Int,
        stream: Boolean = false,
    ) {
        val registry = meterRegistry ?: return

        val safeService = normalizeServiceTag(service)
        val safeModel = normalizeTag(model)
        val safeStatus = normalizeTag(status)
        val safeOutputFormat = normalizeTag(outputFormat)
        val safeStream = stream.toString()

        Counter.builder(REQUESTS_TOTAL)
            .description("Total number of assistant completion requests")
            .tag("service", safeService)
            .tag("model", safeModel)
            .tag("status", safeStatus)
            .tag("output_format", safeOutputFormat)
            .tag("stream", safeStream)
            .register(registry)
            .increment()

        Timer.builder(REQUEST_DURATION_MS)
            .description("Assistant completion request duration in milliseconds")
            .tag("service", safeService)
            .tag("model", safeModel)
            .register(registry)
            .record(durationMs.coerceAtLeast(0).toLong(), TimeUnit.MILLISECONDS)
    }

    open fun recordTokenUsage(
        service: String,
        model: String,
        usage: ProviderTokenUsage,
    ) {
        val registry = meterRegistry ?: return
        val safeService = normalizeServiceTag(service)
        val safeModel = normalizeTag(model)

        incrementTokenCounter(registry, safeService, safeModel, "prompt", usage.promptTokens)
        incrementTokenCounter(registry, safeService, safeModel, "completion", usage.completionTokens)
        incrementTokenCounter(registry, safeService, safeModel, "total", usage.totalTokens)
    }

    open fun recordCost(
        service: String,
        model: String,
        cost: BigDecimal,
    ) {
        val registry = meterRegistry ?: return
        val safeService = normalizeServiceTag(service)
        val safeModel = normalizeTag(model)
        val nonNegativeCost = cost.max(BigDecimal.ZERO).toDouble()

        Counter.builder(COST_TOTAL)
            .description("Total assistant completion cost")
            .tag("service", safeService)
            .tag("model", safeModel)
            .register(registry)
            .increment(nonNegativeCost)
    }

    open fun incrementProviderError(
        provider: String,
        errorType: String,
    ) {
        val registry = meterRegistry ?: return

        Counter.builder(PROVIDER_ERRORS_TOTAL)
            .description("Total assistant provider client errors")
            .tag("provider", normalizeTag(provider))
            .tag("error_type", normalizeTag(errorType))
            .register(registry)
            .increment()
    }

    open fun incrementEscalation(reason: String) {
        val registry = meterRegistry ?: return
        Counter.builder(ESCALATIONS_TOTAL)
            .description("Total number of assistant escalations")
            .tag("reason", normalizeTag(reason))
            .register(registry)
            .increment()
    }

    open fun incrementRateLimitExceeded(scope: String) {
        val registry = meterRegistry ?: return
        Counter.builder(RATE_LIMIT_EXCEEDED_TOTAL)
            .description("Total number of assistant requests rejected by rate limiting")
            .tag("scope", normalizeTag(scope))
            .register(registry)
            .increment()
    }

    open fun incrementBudgetAlert(scope: String) {
        val registry = meterRegistry ?: return
        Counter.builder(BUDGET_ALERT_TOTAL)
            .description("Total number of assistant usage budget threshold alerts")
            .tag("scope", normalizeTag(scope))
            .register(registry)
            .increment()
    }

    open fun recordCircuitBreakerState(
        provider: String,
        state: CircuitBreakerState,
    ) {
        val registry = meterRegistry ?: return
        val safeProvider = normalizeTag(provider)
        val stateValue = mapCircuitBreakerState(state)

        val holder =
            circuitBreakerStateValues.computeIfAbsent(safeProvider) {
                val initial = AtomicInteger(stateValue)
                Gauge.builder(CIRCUIT_BREAKER_STATE) { initial.get().toDouble() }
                    .description("Assistant provider circuit breaker state (0=CLOSED, 1=HALF_OPEN, 2=OPEN)")
                    .tag("provider", safeProvider)
                    .register(registry)
                initial
            }

        holder.set(stateValue)
    }

    private fun incrementTokenCounter(
        registry: MeterRegistry,
        service: String,
        model: String,
        tokenType: String,
        amount: Int,
    ) {
        val safeAmount = amount.coerceAtLeast(0).toDouble()
        Counter.builder(TOKENS_TOTAL)
            .description("Total assistant tokens consumed")
            .tag("service", service)
            .tag("model", model)
            .tag("token_type", tokenType)
            .register(registry)
            .increment(safeAmount)
    }

    private fun normalizeServiceTag(service: String): String {
        val trimmed = service.trim()
        if (trimmed.isBlank()) return UNKNOWN_TAG
        if (trimmed.startsWith(USER_SERVICE_PREFIX, ignoreCase = true)) return USER_SERVICE_TAG
        return normalizeTag(trimmed)
    }

    private fun normalizeTag(value: String): String {
        val normalized =
            value.trim()
                .lowercase()
                .replace(INVALID_TAG_CHAR_REGEX, "_")
                .trim('_')
                .take(MAX_TAG_LENGTH)

        return normalized.ifBlank { UNKNOWN_TAG }
    }

    private fun mapCircuitBreakerState(state: CircuitBreakerState): Int {
        return when (state) {
            CircuitBreakerState.CLOSED -> 0
            CircuitBreakerState.HALF_OPEN -> 1
            CircuitBreakerState.OPEN -> 2
        }
    }

    companion object {
        private const val REQUESTS_TOTAL = "assistant_requests_total"
        private const val REQUEST_DURATION_MS = "assistant_request_duration_ms"
        private const val TOKENS_TOTAL = "assistant_tokens_total"
        private const val COST_TOTAL = "assistant_cost_total"
        private const val PROVIDER_ERRORS_TOTAL = "assistant_provider_errors_total"
        private const val CIRCUIT_BREAKER_STATE = "assistant_circuit_breaker_state"
        private const val ESCALATIONS_TOTAL = "assistant_escalations_total"
        private const val RATE_LIMIT_EXCEEDED_TOTAL = "assistant_rate_limit_exceeded_total"
        private const val BUDGET_ALERT_TOTAL = "assistant_budget_alert_total"

        private const val USER_SERVICE_PREFIX = "user-"
        private const val USER_SERVICE_TAG = "user"
        private const val UNKNOWN_TAG = "unknown"
        private const val MAX_TAG_LENGTH = 64

        private val INVALID_TAG_CHAR_REGEX = Regex("[^a-z0-9._-]")
    }
}
