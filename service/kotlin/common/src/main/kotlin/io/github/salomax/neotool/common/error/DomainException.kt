package io.github.salomax.neotool.common.error

/**
 * Base exception for all domain exceptions in the system.
 *
 * Domain exceptions represent business rule violations or validation errors.
 * They carry an ErrorCode that the frontend can use for i18n translation.
 *
 * Best Practice: Use domain exceptions for:
 * - Validation failures
 * - Business rule violations
 * - Expected error conditions
 *
 * Do NOT use for:
 * - Unexpected errors (use RuntimeException)
 * - Infrastructure failures (use specific exceptions)
 */
abstract class DomainException(
    /**
     * The error code for frontend translation.
     */
    val errorCode: ErrorCode,
    /**
     * Optional field path for field-specific errors.
     * Example: "accountName", "user.email", "items[0].price"
     */
    val field: String? = null,
    /**
     * Optional parameters for message interpolation.
     * Example: mapOf("maxLength" to 100, "actualLength" to 150)
     */
    val parameters: Map<String, Any> = emptyMap(),
    /**
     * Optional cause for exception chaining.
     */
    cause: Throwable? = null,
) : RuntimeException(errorCode.defaultMessage, cause) {
    override fun toString(): String {
        return buildString {
            append("DomainException(")
            append("code=${errorCode.code}")
            if (field != null) append(", field=$field")
            if (parameters.isNotEmpty()) append(", parameters=$parameters")
            append(", message=$message")
            append(")")
        }
    }
}

/**
 * Exception for validation errors.
 * Use when input fails validation rules.
 */
class ValidationException(
    errorCode: ErrorCode,
    field: String? = null,
    parameters: Map<String, Any> = emptyMap(),
    cause: Throwable? = null,
) : DomainException(errorCode, field, parameters, cause) {
    constructor(
        errorCode: ErrorCode,
        field: String? = null,
        vararg params: Pair<String, Any>,
    ) : this(errorCode, field, params.toMap())
}

/**
 * Exception for resource not found errors.
 * Use when a requested resource doesn't exist.
 */
class NotFoundException(
    errorCode: ErrorCode,
    field: String? = null,
    parameters: Map<String, Any> = emptyMap(),
    cause: Throwable? = null,
) : DomainException(errorCode, field, parameters, cause) {
    constructor(
        errorCode: ErrorCode,
        resourceType: String,
        resourceId: Any,
    ) : this(
        errorCode,
        field = "id",
        parameters = mapOf("resourceType" to resourceType, "resourceId" to resourceId),
    )
}

/**
 * Exception for resource conflict errors.
 * Use when an operation conflicts with the current state.
 */
class ConflictException(
    errorCode: ErrorCode,
    field: String? = null,
    parameters: Map<String, Any> = emptyMap(),
    cause: Throwable? = null,
) : DomainException(errorCode, field, parameters, cause)

/**
 * Exception for authorization/permission errors.
 * Use when a user doesn't have permission to perform an action.
 */
class AuthorizationException(
    errorCode: ErrorCode,
    field: String? = null,
    parameters: Map<String, Any> = emptyMap(),
    cause: Throwable? = null,
) : DomainException(errorCode, field, parameters, cause)

/**
 * Exception for authentication errors.
 * Use when authentication is required or fails.
 */
class AuthenticationException(
    errorCode: ErrorCode,
    field: String? = null,
    parameters: Map<String, Any> = emptyMap(),
    cause: Throwable? = null,
) : DomainException(errorCode, field, parameters, cause)

/**
 * Exception for business rule violations.
 * Use when an operation violates business logic.
 */
class BusinessRuleException(
    errorCode: ErrorCode,
    field: String? = null,
    parameters: Map<String, Any> = emptyMap(),
    cause: Throwable? = null,
) : DomainException(errorCode, field, parameters, cause)
