package io.github.salomax.neotool.assistant.test.unit.observability

import io.github.salomax.neotool.assistant.observability.AssistantMetrics
import io.github.salomax.neotool.assistant.provider.model.ProviderTokenUsage
import io.github.salomax.neotool.assistant.resilience.CircuitBreakerState
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.util.concurrent.TimeUnit

class AssistantMetricsTest {
    private val registry = SimpleMeterRegistry()
    private val metrics = AssistantMetrics(registry)

    @Test
    fun `recordRequest tracks request counter and duration histogram`() {
        metrics.recordRequest(
            service = "assistant-api",
            model = "gpt-4o-mini",
            status = "success",
            outputFormat = "TEXT",
            durationMs = 150,
        )

        val requests =
            registry.find("assistant_requests_total")
                .tags(
                    "service", "assistant-api", "model", "gpt-4o-mini", "status", "success",
                    "output_format", "text", "stream", "false",
                )
                .counter()
                ?.count()

        val timer =
            registry.find("assistant_request_duration_ms")
                .tags("service", "assistant-api", "model", "gpt-4o-mini")
                .timer()

        assertThat(requests).isEqualTo(1.0)
        assertThat(timer).isNotNull
        assertThat(timer!!.totalTime(TimeUnit.MILLISECONDS)).isEqualTo(150.0)
    }

    @Test
    fun `recordTokenUsage and recordCost track totals`() {
        metrics.recordTokenUsage(
            service = "assistant-api",
            model = "gpt-4o-mini",
            usage = ProviderTokenUsage(promptTokens = 11, completionTokens = 7, totalTokens = 18),
        )
        metrics.recordCost(
            service = "assistant-api",
            model = "gpt-4o-mini",
            cost = BigDecimal("0.003210"),
        )

        val promptTokens =
            registry.find("assistant_tokens_total")
                .tags("service", "assistant-api", "model", "gpt-4o-mini", "token_type", "prompt")
                .counter()
                ?.count()
        val completionTokens =
            registry.find("assistant_tokens_total")
                .tags("service", "assistant-api", "model", "gpt-4o-mini", "token_type", "completion")
                .counter()
                ?.count()
        val totalTokens =
            registry.find("assistant_tokens_total")
                .tags("service", "assistant-api", "model", "gpt-4o-mini", "token_type", "total")
                .counter()
                ?.count()
        val totalCost =
            registry.find("assistant_cost_total")
                .tags("service", "assistant-api", "model", "gpt-4o-mini")
                .counter()
                ?.count()

        assertThat(promptTokens).isEqualTo(11.0)
        assertThat(completionTokens).isEqualTo(7.0)
        assertThat(totalTokens).isEqualTo(18.0)
        assertThat(totalCost).isEqualTo(0.00321)
    }

    @Test
    fun `incrementProviderError tracks provider error counter`() {
        metrics.incrementProviderError(provider = "OpenAIProviderClient", errorType = "RATE_LIMIT")

        val value =
            registry.find("assistant_provider_errors_total")
                .tags("provider", "openaiproviderclient", "error_type", "rate_limit")
                .counter()
                ?.count()

        assertThat(value).isEqualTo(1.0)
    }

    @Test
    fun `incrementEscalation tracks escalation counter by reason`() {
        metrics.incrementEscalation("no_confident_answer")

        val value =
            registry.find("assistant_escalations_total")
                .tags("reason", "no_confident_answer")
                .counter()
                ?.count()

        assertThat(value).isEqualTo(1.0)
    }

    @Test
    fun `recordRequest sanitizes user service label`() {
        metrics.recordRequest(
            service = "user-7fc18adf-2de9-4022-84cb-3d44f7424a66",
            model = "gpt-4o-mini",
            status = "success",
            outputFormat = "TEXT",
            durationMs = 10,
        )

        val count =
            registry.find("assistant_requests_total")
                .tags(
                    "service", "user", "model", "gpt-4o-mini", "status", "success",
                    "output_format", "text", "stream", "false",
                )
                .counter()
                ?.count()

        assertThat(count).isEqualTo(1.0)
    }

    @Test
    fun `recordRequest tracks stream label when enabled`() {
        metrics.recordRequest(
            service = "assistant-api",
            model = "gpt-4o-mini",
            status = "success",
            outputFormat = "TEXT",
            durationMs = 20,
            stream = true,
        )

        val count =
            registry.find("assistant_requests_total")
                .tags(
                    "service", "assistant-api", "model", "gpt-4o-mini", "status", "success",
                    "output_format", "text", "stream", "true",
                )
                .counter()
                ?.count()

        assertThat(count).isEqualTo(1.0)
    }

    @Test
    fun `recordCircuitBreakerState tracks provider gauge`() {
        metrics.recordCircuitBreakerState("OpenAIProviderClient", CircuitBreakerState.CLOSED)
        metrics.recordCircuitBreakerState("OpenAIProviderClient", CircuitBreakerState.OPEN)

        val value =
            registry.find("assistant_circuit_breaker_state")
                .tags("provider", "openaiproviderclient")
                .gauge()
                ?.value()

        assertThat(value).isEqualTo(2.0)
    }

    @Test
    fun `incrementRateLimitExceeded tracks counter by scope`() {
        metrics.incrementRateLimitExceeded("ACCOUNT")

        val value =
            registry.find("assistant_rate_limit_exceeded_total")
                .tags("scope", "account")
                .counter()
                ?.count()

        assertThat(value).isEqualTo(1.0)
    }

    @Test
    fun `incrementBudgetAlert tracks counter by scope`() {
        metrics.incrementBudgetAlert("FEATURE")

        val value =
            registry.find("assistant_budget_alert_total")
                .tags("scope", "feature")
                .counter()
                ?.count()

        assertThat(value).isEqualTo(1.0)
    }
}
