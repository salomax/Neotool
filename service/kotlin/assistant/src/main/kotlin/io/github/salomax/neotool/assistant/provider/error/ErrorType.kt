package io.github.salomax.neotool.assistant.provider.error

/**
 * Typed provider error categories used across retry/circuit-breaker policies.
 */
enum class ErrorType {
    RATE_LIMIT,
    RATE_LIMIT_EXCEEDED,
    TIMEOUT,
    SERVER_ERROR,
    AUTH_ERROR,
    INVALID_REQUEST,
    CONTEXT_LENGTH_EXCEEDED,
    PROVIDER_RESPONSE_INVALID,
    PROVIDER_UNAVAILABLE,
    UNKNOWN,
}
