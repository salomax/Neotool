package io.github.salomax.neotool.common.graphql.payload

import io.github.salomax.neotool.common.error.CommonErrorCode
import io.github.salomax.neotool.common.error.DomainException
import io.github.salomax.neotool.common.error.ErrorCode
import jakarta.validation.ConstraintViolationException

/**
 * Generic GraphQL payload system following Relay pattern
 *
 * Base payload interface for all GraphQL mutations
 */
interface GraphQLPayload<T> {
    val data: T?
    val errors: List<GraphQLError>
    val success: Boolean
}

/**
 * Generic success payload
 */
data class SuccessPayload<T>(
    override val data: T,
    override val errors: List<GraphQLError> = emptyList(),
    override val success: Boolean = true,
) : GraphQLPayload<T>

/**
 * Generic error payload
 */
data class ErrorPayload<T>(
    override val data: T? = null,
    override val errors: List<GraphQLError>,
    override val success: Boolean = false,
) : GraphQLPayload<T>

/**
 * GraphQL error representation with mandatory error code.
 *
 * The error code is used by the frontend for i18n translation.
 * The message is used for logging and as a fallback.
 */
data class GraphQLError(
    /**
     * Field path where the error occurred.
     * Examples: ["accountName"], ["user", "email"], ["items", "0", "price"]
     */
    val field: List<String>,
    /**
     * Human-readable error message for logging/debugging.
     * NOT shown to end users - frontend uses the code for i18n.
     */
    val message: String,
    /**
     * Machine-readable error code for frontend i18n translation.
     * This is MANDATORY - every error must have a code.
     */
    val code: String,
    /**
     * Optional parameters for message interpolation.
     * Example: {"maxLength": 100, "actualLength": 150}
     */
    val parameters: Map<String, Any>? = null,
)

/**
 * Generic payload factory for creating consistent GraphQL responses
 */
object GraphQLPayloadFactory {
    /**
     * Create a success payload with data
     */
    fun <T> success(data: T): GraphQLPayload<T> {
        return SuccessPayload(data = data)
    }

    /**
     * Create an error payload from an exception.
     * Handles DomainException with error codes, and other standard exceptions.
     */
    fun <T> error(exception: Exception): GraphQLPayload<T> {
        val errors =
            when (exception) {
                // Handle our custom DomainException with error codes
                is DomainException -> {
                    listOf(
                        GraphQLError(
                            field = exception.field?.split(".")?.toList() ?: listOf("general"),
                            message = exception.errorCode.defaultMessage,
                            code = exception.errorCode.code,
                            parameters = exception.parameters.takeIf { it.isNotEmpty() },
                        ),
                    )
                }
                // Handle Jakarta Bean Validation errors
                is ConstraintViolationException -> {
                    exception.constraintViolations.map { violation ->
                        GraphQLError(
                            field = listOf(violation.propertyPath.toString()),
                            message = violation.message,
                            code = CommonErrorCode.VALIDATION_ERROR.code,
                        )
                    }
                }
                // Handle standard IllegalArgumentException
                is IllegalArgumentException -> {
                    listOf(
                        GraphQLError(
                            field = listOf("input"),
                            message = exception.message ?: CommonErrorCode.INVALID_INPUT.defaultMessage,
                            code = CommonErrorCode.INVALID_INPUT.code,
                        ),
                    )
                }
                // Handle NoSuchElementException
                is NoSuchElementException -> {
                    listOf(
                        GraphQLError(
                            field = listOf("id"),
                            message = exception.message ?: CommonErrorCode.NOT_FOUND.defaultMessage,
                            code = CommonErrorCode.NOT_FOUND.code,
                        ),
                    )
                }
                // Handle all other exceptions as internal errors
                else -> {
                    listOf(
                        GraphQLError(
                            field = listOf("general"),
                            message = exception.message ?: CommonErrorCode.INTERNAL_ERROR.defaultMessage,
                            code = CommonErrorCode.INTERNAL_ERROR.code,
                        ),
                    )
                }
            }
        return ErrorPayload(errors = errors)
    }

    /**
     * Create an error payload from a DomainException.
     * This is a type-safe convenience method.
     */
    fun <T> error(exception: DomainException): GraphQLPayload<T> {
        return error(exception as Exception)
    }

    /**
     * Create an error payload from an ErrorCode.
     */
    fun <T> error(
        errorCode: ErrorCode,
        field: String? = null,
        parameters: Map<String, Any>? = null,
    ): GraphQLPayload<T> {
        return ErrorPayload(
            errors =
                listOf(
                    GraphQLError(
                        field = field?.split(".")?.toList() ?: listOf("general"),
                        message = errorCode.defaultMessage,
                        code = errorCode.code,
                        parameters = parameters,
                    ),
                ),
        )
    }

    /**
     * Create an error payload with custom errors
     */
    fun <T> error(errors: List<GraphQLError>): GraphQLPayload<T> {
        return ErrorPayload(errors = errors)
    }

    /**
     * Create an error payload with a single error.
     * Use the ErrorCode overload instead when possible.
     */
    fun <T> error(
        field: String,
        message: String,
        code: String,
    ): GraphQLPayload<T> {
        return ErrorPayload(
            errors = listOf(GraphQLError(field = listOf(field), message = message, code = code)),
        )
    }
}
