package io.github.salomax.neotool.common.error

/**
 * Base interface for all error codes in the system.
 * Error codes are machine-readable identifiers that the frontend uses for i18n translation.
 *
 * Best Practice: Error codes should be:
 * - SCREAMING_SNAKE_CASE
 * - Descriptive and specific
 * - Stable (never changed once released)
 * - Domain-prefixed when needed (e.g., ACCOUNT_*, AUTH_*, etc.)
 */
interface ErrorCode {
    /**
     * The unique error code identifier.
     * This is what gets sent to the frontend for translation.
     */
    val code: String

    /**
     * Default English message for logging and fallback.
     * Not shown to end users - used for debugging.
     */
    val defaultMessage: String

    /**
     * HTTP status code hint (for REST APIs).
     * For GraphQL, this is informational only.
     */
    val httpStatus: Int
        get() = 400 // Default to 400 Bad Request
}

/**
 * Common error codes used across all domains.
 */
enum class CommonErrorCode(
    override val code: String,
    override val defaultMessage: String,
    override val httpStatus: Int = 400,
) : ErrorCode {
    // Generic errors
    UNKNOWN_ERROR("UNKNOWN_ERROR", "An unexpected error occurred", 500),
    INTERNAL_ERROR("INTERNAL_ERROR", "Internal server error", 500),
    INVALID_INPUT("INVALID_INPUT", "Invalid input provided", 400),
    VALIDATION_ERROR("VALIDATION_ERROR", "Validation failed", 400),

    // Not found
    NOT_FOUND("NOT_FOUND", "Resource not found", 404),
    RESOURCE_NOT_FOUND("RESOURCE_NOT_FOUND", "The requested resource was not found", 404),

    // Conflict/state errors
    CONFLICT("CONFLICT", "Resource conflict", 409),
    INVALID_STATE("INVALID_STATE", "Operation not valid in current state", 409),
    OPTIMISTIC_LOCK_ERROR("OPTIMISTIC_LOCK_ERROR", "Resource was modified by another user", 409),

    // Rate limiting
    RATE_LIMIT_EXCEEDED("RATE_LIMIT_EXCEEDED", "Rate limit exceeded", 429),

    // Service availability
    SERVICE_UNAVAILABLE("SERVICE_UNAVAILABLE", "Service temporarily unavailable", 503),
    STORAGE_UNAVAILABLE("STORAGE_UNAVAILABLE", "Storage service unavailable", 503),
}
