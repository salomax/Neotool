package io.github.salomax.neotool.banking.graphql.config

import graphql.GraphQLError
import graphql.execution.DataFetcherExceptionHandler
import graphql.execution.DataFetcherExceptionHandlerParameters
import graphql.execution.DataFetcherExceptionHandlerResult
import io.github.salomax.neotool.common.error.CommonErrorCode
import io.github.salomax.neotool.common.error.DomainException
import io.github.salomax.neotool.common.security.exception.AuthenticationRequiredException
import io.github.salomax.neotool.common.security.exception.AuthorizationDeniedException
import jakarta.validation.ConstraintViolationException
import org.hibernate.StaleObjectStateException
import org.hibernate.StaleStateException
import org.hibernate.dialect.lock.OptimisticEntityLockException
import org.slf4j.LoggerFactory
import java.util.concurrent.CompletableFuture

/**
 * GraphQL-specific exception handler for banking exceptions.
 * Maps [DomainException] to GraphQL errors with standard extensions:
 * - code: machine-readable error code
 * - service: banking
 * - parameters/field: optional metadata
 */
class BankingGraphQLExceptionHandler : DataFetcherExceptionHandler {
    private val logger = LoggerFactory.getLogger(BankingGraphQLExceptionHandler::class.java)

    override fun handleException(
        handlerParameters: DataFetcherExceptionHandlerParameters,
    ): CompletableFuture<DataFetcherExceptionHandlerResult> {
        val exception = handlerParameters.exception

        return when (exception) {
            is AuthenticationRequiredException -> {
                logger.debug("GraphQL authentication required")
                buildErrorResponse(
                    handlerParameters = handlerParameters,
                    message = "Authentication required",
                    code = "AUTH_AUTHENTICATION_REQUIRED",
                )
            }
            is AuthorizationDeniedException -> {
                logger.debug("GraphQL authorization denied")
                buildErrorResponse(
                    handlerParameters = handlerParameters,
                    message = "Access denied",
                    code = "AUTH_AUTHORIZATION_DENIED",
                )
            }
            is DomainException -> {
                logger.debug(
                    "GraphQL domain exception: code={}, message={}",
                    exception.errorCode.code,
                    exception.errorCode.defaultMessage,
                )
                buildErrorResponse(
                    handlerParameters = handlerParameters,
                    message = exception.errorCode.defaultMessage,
                    code = exception.errorCode.code,
                    parameters = exception.parameters.takeIf { it.isNotEmpty() },
                    field = exception.field?.split(".")?.toList(),
                )
            }
            is ConstraintViolationException -> {
                logger.debug("GraphQL validation error")
                val firstField =
                    exception.constraintViolations
                        .firstOrNull()
                        ?.propertyPath
                        ?.toString()
                        ?.split(".")
                        ?.filter { it.isNotBlank() }
                buildErrorResponse(
                    handlerParameters = handlerParameters,
                    message = CommonErrorCode.INVALID_INPUT.defaultMessage,
                    code = CommonErrorCode.INVALID_INPUT.code,
                    field = firstField?.takeIf { it.isNotEmpty() } ?: listOf("input"),
                )
            }
            is StaleObjectStateException,
            is StaleStateException,
            is OptimisticEntityLockException,
            -> {
                logger.warn("GraphQL optimistic lock conflict in banking resolver", exception)
                buildErrorResponse(
                    handlerParameters = handlerParameters,
                    message = CommonErrorCode.OPTIMISTIC_LOCK_ERROR.defaultMessage,
                    code = CommonErrorCode.OPTIMISTIC_LOCK_ERROR.code,
                )
            }
            is IllegalArgumentException -> {
                logger.debug("GraphQL input error: {}", exception.message)
                buildErrorResponse(
                    handlerParameters = handlerParameters,
                    message = CommonErrorCode.INVALID_INPUT.defaultMessage,
                    code = CommonErrorCode.INVALID_INPUT.code,
                )
            }
            else -> {
                logger.error("Unhandled GraphQL exception in banking resolver", exception)
                buildErrorResponse(
                    handlerParameters = handlerParameters,
                    message = CommonErrorCode.INTERNAL_ERROR.defaultMessage,
                    code = CommonErrorCode.INTERNAL_ERROR.code,
                )
            }
        }
    }

    private fun buildErrorResponse(
        handlerParameters: DataFetcherExceptionHandlerParameters,
        message: String,
        code: String,
        parameters: Map<String, Any>? = null,
        field: List<String>? = null,
    ): CompletableFuture<DataFetcherExceptionHandlerResult> {
        val errorBuilder = GraphQLError.newError().message(message)
        handlerParameters.path?.let { errorBuilder.path(it) }
        try {
            handlerParameters.sourceLocation?.let { errorBuilder.location(it) }
        } catch (_: NullPointerException) {
            // sourceLocation can be null depending on fetcher context.
        }

        val extensions = mutableMapOf<String, Any?>("code" to code, "service" to "banking")
        if (!parameters.isNullOrEmpty()) extensions["parameters"] = parameters
        if (!field.isNullOrEmpty()) extensions["field"] = field

        val error = errorBuilder.extensions(extensions).build()
        return CompletableFuture.completedFuture(
            DataFetcherExceptionHandlerResult.newResult().error(error).build(),
        )
    }
}
