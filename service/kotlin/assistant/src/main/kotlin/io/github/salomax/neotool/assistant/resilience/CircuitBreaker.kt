package io.github.salomax.neotool.assistant.resilience

import io.github.salomax.neotool.assistant.provider.error.ErrorType
import io.github.salomax.neotool.assistant.provider.error.ProviderClientException
import io.github.salomax.neotool.common.tracing.TraceExecution
import io.micronaut.context.annotation.ConfigurationProperties
import jakarta.inject.Singleton
import java.util.ArrayDeque
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Provider circuit breaker with CLOSED, OPEN, HALF_OPEN states.
 */
@TraceExecution
@Singleton
open class CircuitBreaker(
    private val config: CircuitBreakerConfig,
    private val timeProvider: TimeProvider,
) {
    private val lock = ReentrantLock()
    private val failureTimestamps = ArrayDeque<Long>()

    @Volatile
    private var state: CircuitBreakerState = CircuitBreakerState.CLOSED

    private var openUntilMs: Long = 0L
    private var halfOpenInFlight: Int = 0
    private var halfOpenSuccesses: Int = 0

    open suspend fun <T> execute(block: suspend () -> T): T {
        val permit = acquirePermit()

        return try {
            val result = block()
            onSuccess(permit)
            result
        } catch (e: ProviderClientException) {
            if (shouldCountFailure(e)) {
                onFailure(permit)
            } else {
                onIgnoredFailure(permit)
            }
            throw e
        } catch (e: Exception) {
            onFailure(permit)
            throw e
        }
    }

    open fun currentState(): CircuitBreakerState {
        val now = timeProvider.currentTimeMillis()
        return lock.withLock {
            transitionFromOpenIfTimeout(now)
            state
        }
    }

    private fun acquirePermit(): Permit {
        val now = timeProvider.currentTimeMillis()
        return lock.withLock {
            transitionFromOpenIfTimeout(now)
            when (state) {
                CircuitBreakerState.CLOSED -> Permit(CircuitBreakerState.CLOSED)
                CircuitBreakerState.OPEN -> throwOpenException()
                CircuitBreakerState.HALF_OPEN -> {
                    if (halfOpenInFlight >= config.halfOpenRequests) {
                        throwOpenException()
                    }
                    halfOpenInFlight += 1
                    Permit(CircuitBreakerState.HALF_OPEN)
                }
            }
        }
    }

    private fun onSuccess(permit: Permit) {
        val now = timeProvider.currentTimeMillis()
        lock.withLock {
            transitionFromOpenIfTimeout(now)

            when (permit.stateAtAcquire) {
                CircuitBreakerState.CLOSED -> {
                    pruneFailures(now)
                }

                CircuitBreakerState.HALF_OPEN -> {
                    if (state != CircuitBreakerState.HALF_OPEN) return
                    halfOpenInFlight = (halfOpenInFlight - 1).coerceAtLeast(0)
                    halfOpenSuccesses += 1
                    if (halfOpenSuccesses >= config.successThreshold) {
                        closeCircuit()
                    }
                }

                CircuitBreakerState.OPEN -> Unit
            }
        }
    }

    private fun onFailure(permit: Permit) {
        val now = timeProvider.currentTimeMillis()
        lock.withLock {
            transitionFromOpenIfTimeout(now)

            when (permit.stateAtAcquire) {
                CircuitBreakerState.CLOSED -> {
                    if (state != CircuitBreakerState.CLOSED) return
                    failureTimestamps.addLast(now)
                    pruneFailures(now)
                    if (failureTimestamps.size >= config.failureThreshold) {
                        openCircuit(now)
                    }
                }

                CircuitBreakerState.HALF_OPEN -> {
                    if (state != CircuitBreakerState.HALF_OPEN) return
                    halfOpenInFlight = (halfOpenInFlight - 1).coerceAtLeast(0)
                    openCircuit(now)
                }

                CircuitBreakerState.OPEN -> Unit
            }
        }
    }

    private fun onIgnoredFailure(permit: Permit) {
        if (permit.stateAtAcquire != CircuitBreakerState.HALF_OPEN) {
            return
        }

        lock.withLock {
            if (state == CircuitBreakerState.HALF_OPEN) {
                halfOpenInFlight = (halfOpenInFlight - 1).coerceAtLeast(0)
            }
        }
    }

    private fun pruneFailures(now: Long) {
        val minAllowedTimestamp = now - config.failureWindowMs
        while (failureTimestamps.isNotEmpty() && failureTimestamps.first() < minAllowedTimestamp) {
            failureTimestamps.removeFirst()
        }
    }

    private fun openCircuit(now: Long) {
        state = CircuitBreakerState.OPEN
        openUntilMs = now + config.timeoutMs
        halfOpenInFlight = 0
        halfOpenSuccesses = 0
    }

    private fun closeCircuit() {
        state = CircuitBreakerState.CLOSED
        failureTimestamps.clear()
        halfOpenInFlight = 0
        halfOpenSuccesses = 0
        openUntilMs = 0L
    }

    private fun transitionFromOpenIfTimeout(now: Long) {
        if (state == CircuitBreakerState.OPEN && now >= openUntilMs) {
            state = CircuitBreakerState.HALF_OPEN
            halfOpenInFlight = 0
            halfOpenSuccesses = 0
            failureTimestamps.clear()
        }
    }

    private fun throwOpenException(): Nothing {
        throw ProviderClientException(
            errorType = ErrorType.PROVIDER_UNAVAILABLE,
            message = "Provider is temporarily unavailable: circuit breaker is OPEN",
        )
    }

    private fun shouldCountFailure(exception: ProviderClientException): Boolean {
        return exception.errorType != ErrorType.INVALID_REQUEST &&
            exception.errorType != ErrorType.CONTEXT_LENGTH_EXCEEDED
    }

    private data class Permit(
        val stateAtAcquire: CircuitBreakerState,
    )
}

@ConfigurationProperties("llm.circuit-breaker")
data class CircuitBreakerConfig(
    val failureThreshold: Int = 5,
    val failureWindowMs: Long = 60_000,
    val timeoutMs: Long = 60_000,
    val halfOpenRequests: Int = 1,
    val successThreshold: Int = 2,
) {
    init {
        require(failureThreshold > 0) { "failureThreshold must be greater than zero" }
        require(failureWindowMs > 0) { "failureWindowMs must be greater than zero" }
        require(timeoutMs > 0) { "timeoutMs must be greater than zero" }
        require(halfOpenRequests > 0) { "halfOpenRequests must be greater than zero" }
        require(successThreshold > 0) { "successThreshold must be greater than zero" }
    }
}

enum class CircuitBreakerState {
    CLOSED,
    OPEN,
    HALF_OPEN,
}

fun interface TimeProvider {
    fun currentTimeMillis(): Long
}

@Singleton
class SystemTimeProvider : TimeProvider {
    override fun currentTimeMillis(): Long = System.currentTimeMillis()
}
