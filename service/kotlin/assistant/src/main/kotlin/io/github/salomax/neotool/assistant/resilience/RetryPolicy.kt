package io.github.salomax.neotool.assistant.resilience

import io.github.salomax.neotool.assistant.provider.error.ErrorType
import io.micronaut.context.annotation.ConfigurationProperties

/**
 * Retry configuration for transient provider failures.
 * Delays are in milliseconds.
 */
@ConfigurationProperties("llm.retry")
data class RetryPolicy(
    val maxAttempts: Int = 4,
    val initialDelay: Long = 250,
    val maxDelay: Long = 2000,
    val multiplier: Double = 2.0,
    val retryableErrors: Set<ErrorType> = DEFAULT_RETRYABLE_ERRORS,
) {
    init {
        require(maxAttempts > 0) { "maxAttempts must be greater than zero" }
        require(initialDelay >= 0) { "initialDelay must be greater than or equal to zero" }
        require(maxDelay >= 0) { "maxDelay must be greater than or equal to zero" }
        require(maxDelay >= initialDelay) { "maxDelay must be greater than or equal to initialDelay" }
        require(multiplier >= 1.0) { "multiplier must be greater than or equal to 1.0" }
    }

    fun isRetryable(errorType: ErrorType): Boolean = errorType in retryableErrors

    companion object {
        val DEFAULT_RETRYABLE_ERRORS: Set<ErrorType> =
            setOf(
                ErrorType.RATE_LIMIT,
                ErrorType.TIMEOUT,
                ErrorType.SERVER_ERROR,
            )
    }
}
