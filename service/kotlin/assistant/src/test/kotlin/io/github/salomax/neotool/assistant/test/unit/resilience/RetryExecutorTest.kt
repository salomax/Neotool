package io.github.salomax.neotool.assistant.test.unit.resilience

import io.github.salomax.neotool.assistant.provider.error.ErrorType
import io.github.salomax.neotool.assistant.provider.error.ProviderClientException
import io.github.salomax.neotool.assistant.resilience.RetryExecutor
import io.github.salomax.neotool.assistant.resilience.RetryPolicy
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class RetryExecutorTest {
    @Test
    fun `retryable error retries up to maxAttempts`() {
        val capturedDelays = mutableListOf<Long>()
        val executor = RetryExecutor { delayMs -> capturedDelays.add(delayMs) }
        val policy = RetryPolicy(maxAttempts = 4, initialDelay = 50, maxDelay = 1_000, multiplier = 2.0)
        var attempts = 0

        assertThatThrownBy {
            runBlocking {
                executor.withRetry(policy) {
                    attempts += 1
                    throw ProviderClientException(
                        errorType = ErrorType.RATE_LIMIT,
                        message = "rate limited",
                    )
                }
            }
        }
            .isInstanceOf(ProviderClientException::class.java)

        assertThat(attempts).isEqualTo(4)
        assertThat(capturedDelays).containsExactly(50L, 100L, 200L)
    }

    @Test
    fun `non-retryable error does not retry`() {
        val capturedDelays = mutableListOf<Long>()
        val executor = RetryExecutor { delayMs -> capturedDelays.add(delayMs) }
        val policy = RetryPolicy(maxAttempts = 4, initialDelay = 50, maxDelay = 1_000, multiplier = 2.0)
        var attempts = 0

        assertThatThrownBy {
            runBlocking {
                executor.withRetry(policy) {
                    attempts += 1
                    throw ProviderClientException(
                        errorType = ErrorType.AUTH_ERROR,
                        message = "invalid api key",
                    )
                }
            }
        }
            .isInstanceOfSatisfying(ProviderClientException::class.java) { exception ->
                assertThat(exception.errorType).isEqualTo(ErrorType.AUTH_ERROR)
            }

        assertThat(attempts).isEqualTo(1)
        assertThat(capturedDelays).isEmpty()
    }

    @Test
    fun `success on second attempt returns response`() {
        val capturedDelays = mutableListOf<Long>()
        val executor = RetryExecutor { delayMs -> capturedDelays.add(delayMs) }
        val policy = RetryPolicy(maxAttempts = 4, initialDelay = 75, maxDelay = 1_000, multiplier = 2.0)
        var attempts = 0

        val result =
            runBlocking {
                executor.withRetry(policy) {
                    attempts += 1
                    if (attempts == 1) {
                        throw ProviderClientException(
                            errorType = ErrorType.TIMEOUT,
                            message = "timeout",
                        )
                    }
                    "ok"
                }
            }

        assertThat(result).isEqualTo("ok")
        assertThat(attempts).isEqualTo(2)
        assertThat(capturedDelays).containsExactly(75L)
    }

    @Test
    fun `delays follow exponential backoff progression with max delay cap`() {
        val capturedDelays = mutableListOf<Long>()
        val executor = RetryExecutor { delayMs -> capturedDelays.add(delayMs) }
        val policy = RetryPolicy(maxAttempts = 5, initialDelay = 50, maxDelay = 120, multiplier = 2.0)

        assertThatThrownBy {
            runBlocking {
                executor.withRetry(policy) {
                    throw ProviderClientException(
                        errorType = ErrorType.SERVER_ERROR,
                        message = "server error",
                    )
                }
            }
        }
            .isInstanceOf(ProviderClientException::class.java)

        assertThat(capturedDelays).containsExactly(50L, 100L, 120L, 120L)
    }
}
