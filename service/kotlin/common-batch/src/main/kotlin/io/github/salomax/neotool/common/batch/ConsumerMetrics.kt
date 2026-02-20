package io.github.salomax.neotool.common.batch

/**
 * Interface for consumer metrics.
 */
interface ConsumerMetrics {
    /**
     * Increment the processed counter.
     */
    fun incrementProcessed()

    /**
     * Record processing duration.
     *
     * @param durationMs Duration in milliseconds
     */
    fun recordProcessingDuration(durationMs: Double)

    /**
     * Increment the DLQ counter.
     */
    fun incrementDlq()

    /**
     * Increment the retry counter.
     */
    fun incrementRetry()

    /**
     * Increment the error counter.
     */
    fun incrementError()

    /**
     * Increment the error counter with a type tag.
     *
     * @param type Error type (e.g., "validation", "processing", "dlq")
     */
    fun incrementError(type: String)

    /**
     * Increment DLQ publish failure counter.
     */
    fun incrementDlqPublishFailure()
}
