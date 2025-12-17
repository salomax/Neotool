package io.github.salomax.neotool.app.batch.swapi.metrics

import io.github.salomax.neotool.common.batch.ConsumerMetrics
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import jakarta.inject.Singleton

/**
 * Micrometer metrics for SWAPI people processing.
 */
@Singleton
class PeopleMetrics(
    private val meterRegistry: MeterRegistry,
) : ConsumerMetrics {
    private val processedCounter: Counter =
        Counter.builder("swapi.people.processed")
            .description("Total number of people records processed successfully")
            .register(meterRegistry)

    private val processingTimer: Timer =
        Timer.builder("swapi.people.processing.duration")
            .description("Time taken to process a people record")
            .register(meterRegistry)

    private val dlqCounter: Counter =
        Counter.builder("swapi.people.dlq.count")
            .description("Total number of messages sent to DLQ")
            .register(meterRegistry)

    private val retryCounter: Counter =
        Counter.builder("swapi.people.retry.count")
            .description("Total number of retry attempts")
            .register(meterRegistry)

    private val errorCounter: Counter =
        Counter.builder("swapi.people.error.count")
            .description("Total number of processing errors")
            .tag("type", "processing")
            .register(meterRegistry)

    /**
     * Get or create an error counter with the specified type tag.
     */
    private fun getErrorCounter(type: String): Counter {
        return Counter.builder("swapi.people.error.count")
            .description("Total number of processing errors")
            .tag("type", type)
            .register(meterRegistry)
    }

    private val dlqPublishFailureCounter: Counter =
        Counter.builder("swapi.people.dlq.publish.failure")
            .description("Total number of DLQ publish failures")
            .register(meterRegistry)

    /**
     * Increment the processed counter
     */
    override fun incrementProcessed() {
        processedCounter.increment()
    }

    /**
     * Record processing duration
     */
    override fun recordProcessingDuration(durationMs: Double) {
        processingTimer.record(durationMs.toLong(), java.util.concurrent.TimeUnit.MILLISECONDS)
    }

    /**
     * Increment the DLQ counter
     */
    override fun incrementDlq() {
        dlqCounter.increment()
    }

    /**
     * Increment the retry counter
     */
    override fun incrementRetry() {
        retryCounter.increment()
    }

    /**
     * Increment the error counter
     */
    override fun incrementError() {
        errorCounter.increment()
    }

    /**
     * Increment the error counter with a type tag.
     */
    override fun incrementError(type: String) {
        getErrorCounter(type).increment()
    }

    /**
     * Increment DLQ publish failure counter
     */
    override fun incrementDlqPublishFailure() {
        dlqPublishFailureCounter.increment()
    }
}
