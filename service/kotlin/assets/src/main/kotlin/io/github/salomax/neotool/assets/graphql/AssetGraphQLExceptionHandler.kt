package io.github.salomax.neotool.assets.graphql

import graphql.GraphQLError
import graphql.execution.DataFetcherExceptionHandler
import graphql.execution.DataFetcherExceptionHandlerParameters
import graphql.execution.DataFetcherExceptionHandlerResult
import io.github.salomax.neotool.assets.error.AssetErrorCode
import io.github.salomax.neotool.assets.exception.StorageUnavailableException
import io.github.salomax.neotool.common.error.CommonErrorCode
import io.github.salomax.neotool.common.error.DomainException
import io.github.salomax.neotool.common.exception.GraphQLOptimisticLockExceptionHandler
import org.slf4j.LoggerFactory
import java.util.concurrent.CompletableFuture

/**
 * GraphQL-specific exception handler for asset-related exceptions.
 * Converts asset service exceptions to user-friendly GraphQL error messages
 * without exposing sensitive details.
 *
 * This handler delegates to GraphQLOptimisticLockExceptionHandler for
 * optimistic locking exceptions, which in turn delegates to the default
 * handler for all other exceptions.
 */
class AssetGraphQLExceptionHandler : DataFetcherExceptionHandler {
    private val logger = LoggerFactory.getLogger(AssetGraphQLExceptionHandler::class.java)
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
                logger.debug("GraphQL validation error: ${exception.message}")
                buildErrorResponse(
                    handlerParameters = handlerParameters,
                    message = exception.message ?: CommonErrorCode.INVALID_INPUT.defaultMessage,
                    code = CommonErrorCode.INVALID_INPUT.code,
                )
            }

            is IllegalStateException -> {
                logger.debug("GraphQL state error: ${exception.message}")
                buildErrorResponse(
                    handlerParameters = handlerParameters,
                    message = exception.message ?: CommonErrorCode.INVALID_STATE.defaultMessage,
                    code = CommonErrorCode.INVALID_STATE.code,
                )
            }

            is StorageUnavailableException -> {
                logger.warn("Storage service unavailable: ${exception.message}", exception.cause)
                buildErrorResponse(
                    handlerParameters = handlerParameters,
                    message = exception.message ?: "Storage service is currently unavailable. Please try again later.",
                    code = AssetErrorCode.ASSET_STORAGE_UNAVAILABLE.code,
                )
            }

            else -> {
                // Delegate to next handler (GraphQLOptimisticLockExceptionHandler)
                // which will handle optimistic locking exceptions or delegate to default handler
                nextHandler.handleException(handlerParameters)
            }
        }
    }

    /**
     * Builds a GraphQL error response with safe handling of optional fields.
     * Safely sets path and location without throwing NPEs.
     */
    private fun buildErrorResponse(
        handlerParameters: DataFetcherExceptionHandlerParameters,
        message: String,
        code: String,
        parameters: Map<String, Any>? = null,
        field: List<String>? = null,
    ): CompletableFuture<DataFetcherExceptionHandlerResult> {
        val errorBuilder = GraphQLError.newError().message(message)

        // Safely set path if available
        handlerParameters.path?.let { errorBuilder.path(it) }

        // Safely set location if available (sourceLocation can throw NPE when accessed if null)
        try {
            val sourceLocation = handlerParameters.sourceLocation
            if (sourceLocation != null) {
                errorBuilder.location(sourceLocation)
            }
        } catch (e: NullPointerException) {
            // sourceLocation property access can throw NPE if underlying field is null
            // This is a known issue with GraphQL Java library - ignore silently
            logger.trace("Source location not available for error: $message")
        }

        val extensions = mutableMapOf<String, Any?>("code" to code, "service" to "assets")
        if (!parameters.isNullOrEmpty()) extensions["parameters"] = parameters
        if (!field.isNullOrEmpty()) extensions["field"] = field

        val error = errorBuilder.extensions(extensions).build()

        return CompletableFuture.completedFuture(
            DataFetcherExceptionHandlerResult.newResult().error(error).build(),
        )
    }
}
