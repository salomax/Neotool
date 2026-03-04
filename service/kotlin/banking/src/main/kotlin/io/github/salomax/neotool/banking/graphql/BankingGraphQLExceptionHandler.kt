package io.github.salomax.neotool.banking.graphql

import graphql.GraphQLError
import graphql.execution.DataFetcherExceptionHandler
import graphql.execution.DataFetcherExceptionHandlerParameters
import graphql.execution.DataFetcherExceptionHandlerResult
import io.github.salomax.neotool.common.error.CommonErrorCode
import io.github.salomax.neotool.common.error.DomainException
import io.github.salomax.neotool.common.exception.GraphQLOptimisticLockExceptionHandler
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
    private val nextHandler = GraphQLOptimisticLockExceptionHandler()

    override fun handleException(
        handlerParameters: DataFetcherExceptionHandlerParameters,
    ): CompletableFuture<DataFetcherExceptionHandlerResult> {
        val exception = handlerParameters.exception

        return when (exception) {
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
            is IllegalArgumentException -> {
                logger.debug("GraphQL input error: {}", exception.message)
                buildErrorResponse(
                    handlerParameters = handlerParameters,
                    message = exception.message ?: CommonErrorCode.INVALID_INPUT.defaultMessage,
                    code = CommonErrorCode.INVALID_INPUT.code,
                )
            }
            else -> nextHandler.handleException(handlerParameters)
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
