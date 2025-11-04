package io.github.salomax.neotool.graphql.payload

import jakarta.validation.ConstraintViolationException
import java.util.*

/**
 * Generic GraphQL payload system following Relay pattern
 */

/**
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
    override val success: Boolean = true
) : GraphQLPayload<T>

/**
 * Generic error payload
 */
data class ErrorPayload<T>(
    override val data: T? = null,
    override val errors: List<GraphQLError>,
    override val success: Boolean = false
) : GraphQLPayload<T>

/**
 * GraphQL error representation
 */
data class GraphQLError(
    val field: List<String>,
    val message: String,
    val code: String? = null
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
     * Create an error payload from an exception
     */
    fun <T> error(exception: Exception): GraphQLPayload<T> {
        val errors = when (exception) {
            is ConstraintViolationException -> {
                exception.constraintViolations.map { violation ->
                    GraphQLError(
                        field = listOf(violation.propertyPath.toString()),
                        message = violation.message,
                        code = "VALIDATION_ERROR"
                    )
                }
            }
            is IllegalArgumentException -> {
                listOf(
                    GraphQLError(
                        field = listOf("input"),
                        message = exception.message ?: "Invalid input",
                        code = "INVALID_INPUT"
                    )
                )
            }
            is NoSuchElementException -> {
                listOf(
                    GraphQLError(
                        field = listOf("id"),
                        message = "Resource not found",
                        code = "NOT_FOUND"
                    )
                )
            }
            else -> {
                listOf(
                    GraphQLError(
                        field = listOf("general"),
                        message = exception.message ?: "An unexpected error occurred",
                        code = "INTERNAL_ERROR"
                    )
                )
            }
        }
        return ErrorPayload(errors = errors)
    }
    
    /**
     * Create an error payload with custom errors
     */
    fun <T> error(errors: List<GraphQLError>): GraphQLPayload<T> {
        return ErrorPayload(errors = errors)
    }
    
    /**
     * Create an error payload with a single error
     */
    fun <T> error(field: String, message: String, code: String? = null): GraphQLPayload<T> {
        return ErrorPayload(
            errors = listOf(GraphQLError(field = listOf(field), message = message, code = code))
        )
    }
}
