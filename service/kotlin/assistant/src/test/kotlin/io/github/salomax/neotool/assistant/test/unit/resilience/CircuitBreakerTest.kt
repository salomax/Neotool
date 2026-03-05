package io.github.salomax.neotool.assistant.test.unit.resilience

import io.github.salomax.neotool.assistant.provider.error.ErrorType
import io.github.salomax.neotool.assistant.provider.error.ProviderClientException
import io.github.salomax.neotool.assistant.resilience.CircuitBreaker
import io.github.salomax.neotool.assistant.resilience.CircuitBreakerConfig
import io.github.salomax.neotool.assistant.resilience.CircuitBreakerState
import io.github.salomax.neotool.assistant.resilience.TimeProvider
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class CircuitBreakerTest {
    @Test
    fun `circuit opens after failure threshold`() {
        val time = MutableTimeProvider(now = 1_000L)
        val circuitBreaker =
            CircuitBreaker(
                config = CircuitBreakerConfig(failureThreshold = 3, failureWindowMs = 60_000, timeoutMs = 60_000),
                timeProvider = time,
            )

        repeat(3) {
            assertThatThrownBy {
                runBlocking {
                    circuitBreaker.execute {
                        throw ProviderClientException(
                            errorType = ErrorType.SERVER_ERROR,
                            message = "provider unavailable",
                        )
                    }
                }
            }.isInstanceOf(ProviderClientException::class.java)
        }

        assertThat(circuitBreaker.currentState()).isEqualTo(CircuitBreakerState.OPEN)
    }

    @Test
    fun `requests are rejected immediately while circuit is open`() {
        val time = MutableTimeProvider(now = 1_000L)
        val circuitBreaker =
            CircuitBreaker(
                config = CircuitBreakerConfig(failureThreshold = 1, failureWindowMs = 60_000, timeoutMs = 60_000),
                timeProvider = time,
            )

        assertThatThrownBy {
            runBlocking {
                circuitBreaker.execute {
                    throw ProviderClientException(
                        errorType = ErrorType.TIMEOUT,
                        message = "timeout",
                    )
                }
            }
        }.isInstanceOf(ProviderClientException::class.java)

        var callCount = 0
        assertThatThrownBy {
            runBlocking {
                circuitBreaker.execute {
                    callCount += 1
                    "ok"
                }
            }
        }
            .isInstanceOfSatisfying(ProviderClientException::class.java) { exception ->
                assertThat(exception.errorType).isEqualTo(ErrorType.PROVIDER_UNAVAILABLE)
            }

        assertThat(callCount).isEqualTo(0)
    }

    @Test
    fun `circuit closes after success threshold in half open`() {
        val time = MutableTimeProvider(now = 1_000L)
        val circuitBreaker =
            CircuitBreaker(
                config =
                    CircuitBreakerConfig(
                        failureThreshold = 1,
                        failureWindowMs = 60_000,
                        timeoutMs = 10_000,
                        halfOpenRequests = 1,
                        successThreshold = 2,
                    ),
                timeProvider = time,
            )

        assertThatThrownBy {
            runBlocking {
                circuitBreaker.execute {
                    throw ProviderClientException(
                        errorType = ErrorType.SERVER_ERROR,
                        message = "server error",
                    )
                }
            }
        }.isInstanceOf(ProviderClientException::class.java)
        assertThat(circuitBreaker.currentState()).isEqualTo(CircuitBreakerState.OPEN)

        time.now += 10_001
        assertThat(circuitBreaker.currentState()).isEqualTo(CircuitBreakerState.HALF_OPEN)

        runBlocking { circuitBreaker.execute { "first success" } }
        assertThat(circuitBreaker.currentState()).isEqualTo(CircuitBreakerState.HALF_OPEN)

        runBlocking { circuitBreaker.execute { "second success" } }
        assertThat(circuitBreaker.currentState()).isEqualTo(CircuitBreakerState.CLOSED)
    }

    private class MutableTimeProvider(
        var now: Long,
    ) : TimeProvider {
        override fun currentTimeMillis(): Long = now
    }
}
