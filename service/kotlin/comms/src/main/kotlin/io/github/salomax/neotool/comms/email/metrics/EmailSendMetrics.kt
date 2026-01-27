package io.github.salomax.neotool.comms.email.metrics

import io.github.salomax.neotool.common.batch.ConsumerMetrics
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import jakarta.inject.Singleton

@Singleton
class EmailSendMetrics(
    private val meterRegistry: MeterRegistry,
) : ConsumerMetrics {
    private val processedCounter: Counter =
        Counter.builder("comms.email.processed")
            .description("Total number of email requests processed successfully")
            .register(meterRegistry)

    private val processingTimer: Timer =
        Timer.builder("comms.email.processing.duration")
            .description("Time taken to process an email request")
            .register(meterRegistry)

    private val dlqCounter: Counter =
        Counter.builder("comms.email.dlq.count")
            .description("Total number of email messages sent to DLQ")
            .register(meterRegistry)

    private val retryCounter: Counter =
        Counter.builder("comms.email.retry.count")
            .description("Total number of email retry attempts")
            .register(meterRegistry)

    private val errorCounter: Counter =
        Counter.builder("comms.email.error.count")
            .description("Total number of email processing errors")
            .tag("type", "processing")
            .register(meterRegistry)

    private fun getErrorCounter(type: String): Counter {
        return Counter.builder("comms.email.error.count")
            .description("Total number of email processing errors")
            .tag("type", type)
            .register(meterRegistry)
    }

    private val dlqPublishFailureCounter: Counter =
        Counter.builder("comms.email.dlq.publish.failure")
            .description("Total number of email DLQ publish failures")
            .register(meterRegistry)

    override fun incrementProcessed() {
        processedCounter.increment()
    }

    override fun recordProcessingDuration(durationMs: Double) {
        processingTimer.record(durationMs.toLong(), java.util.concurrent.TimeUnit.MILLISECONDS)
    }

    override fun incrementDlq() {
        dlqCounter.increment()
    }

    override fun incrementRetry() {
        retryCounter.increment()
    }

    override fun incrementError() {
        errorCounter.increment()
    }

    override fun incrementError(type: String) {
        getErrorCounter(type).increment()
    }

    override fun incrementDlqPublishFailure() {
        dlqPublishFailureCounter.increment()
    }
}
