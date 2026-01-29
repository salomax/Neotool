package io.github.salomax.neotool.comms.email.metrics

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class EmailSendMetricsTest {
    private val registry = SimpleMeterRegistry()
    private val metrics = EmailSendMetrics(registry)

    @Test
    fun `increments processed counter`() {
        metrics.incrementProcessed()

        val count = registry.find("comms.email.processed").counter()?.count() ?: 0.0
        assertThat(count).isEqualTo(1.0)
    }

    @Test
    fun `increments dlq counter`() {
        metrics.incrementDlq()

        val count = registry.find("comms.email.dlq.count").counter()?.count() ?: 0.0
        assertThat(count).isEqualTo(1.0)
    }

    @Test
    fun `increments retry counter`() {
        metrics.incrementRetry()

        val count = registry.find("comms.email.retry.count").counter()?.count() ?: 0.0
        assertThat(count).isEqualTo(1.0)
    }

    @Test
    fun `increments error counter with type`() {
        metrics.incrementError("validation")

        val count =
            registry.find("comms.email.error.count")
                .tag("type", "validation")
                .counter()
                ?.count() ?: 0.0
        assertThat(count).isEqualTo(1.0)
    }

    @Test
    fun `increments dlq publish failure counter`() {
        metrics.incrementDlqPublishFailure()

        val count = registry.find("comms.email.dlq.publish.failure").counter()?.count() ?: 0.0
        assertThat(count).isEqualTo(1.0)
    }
}
