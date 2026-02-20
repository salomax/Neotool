package io.github.salomax.neotool.common.batch

import io.micronaut.context.annotation.ConfigurationProperties

/**
 * Configuration for Kafka consumer retry behavior.
 */
@ConfigurationProperties("batch.consumer")
data class ConsumerConfig(
    val maxRetries: Int = 3,
    val initialRetryDelayMs: Long = 1000L,
    val maxRetryDelayMs: Long = 10000L,
    val retryBackoffMultiplier: Double = 2.0,
    val commitTimeoutSeconds: Long = 5L,
    val enableDlqFallback: Boolean = false,
    val dlqMaxRetries: Int = 3,
    /**
     * Timeout in seconds for graceful shutdown to wait for in-flight work to complete.
     * During shutdown, the consumer will pause partitions and wait up to this duration
     * for all in-flight messages to finish processing before closing.
     * Default: 30 seconds
     */
    val shutdownTimeoutSeconds: Long = 30L,
)
