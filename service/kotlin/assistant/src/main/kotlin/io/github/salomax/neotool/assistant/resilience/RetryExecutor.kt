package io.github.salomax.neotool.assistant.resilience

import io.github.salomax.neotool.assistant.provider.error.ProviderClientException
import io.github.salomax.neotool.common.tracing.TraceExecution
import jakarta.inject.Singleton
import kotlinx.coroutines.delay
import mu.KotlinLogging
import kotlin.math.pow

@TraceExecution
@Singleton
open class RetryExecutor(
    private val sleeper: suspend (Long) -> Unit = { delay(it) },
) {
    private val logger = KotlinLogging.logger {}

    open suspend fun <T> withRetry(
        retryPolicy: RetryPolicy,
        block: suspend () -> T,
    ): T {
        var attempt = 1

        while (true) {
            try {
                return block()
            } catch (e: ProviderClientException) {
                if (!shouldRetry(e, retryPolicy, attempt)) {
                    throw e
                }

                val delayMs = calculateDelay(retryPolicy, attempt)
                logger.warn {
                    "event=provider_retry attempt=$attempt nextAttempt=${attempt + 1} " +
                        "delayMs=$delayMs errorType=${e.errorType.name}"
                }
                sleeper(delayMs)
                attempt += 1
            }
        }
    }

    private fun shouldRetry(
        exception: ProviderClientException,
        retryPolicy: RetryPolicy,
        attempt: Int,
    ): Boolean {
        if (attempt >= retryPolicy.maxAttempts) {
            return false
        }
        return retryPolicy.isRetryable(exception.errorType)
    }

    private fun calculateDelay(
        retryPolicy: RetryPolicy,
        attempt: Int,
    ): Long {
        if (retryPolicy.initialDelay == 0L) {
            return 0L
        }

        val exponent = (attempt - 1).coerceAtLeast(0)
        val scaled = retryPolicy.initialDelay.toDouble() * retryPolicy.multiplier.pow(exponent.toDouble())
        val clamped = scaled.coerceAtMost(retryPolicy.maxDelay.toDouble())
        return clamped.toLong().coerceAtLeast(0L)
    }
}
