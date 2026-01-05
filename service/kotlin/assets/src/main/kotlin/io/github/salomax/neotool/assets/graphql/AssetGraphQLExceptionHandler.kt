package io.github.salomax.neotool.assets.graphql

import graphql.GraphQLError
import graphql.execution.DataFetcherExceptionHandler
import graphql.execution.DataFetcherExceptionHandlerParameters
import graphql.execution.DataFetcherExceptionHandlerResult
import io.github.salomax.neotool.assets.exception.StorageUnavailableException
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
            is IllegalArgumentException -> {
                logger.debug("GraphQL validation error: ${exception.message}")
                buildErrorResponse(
                    handlerParameters,
                    exception.message ?: "Validation error",
                    "VALIDATION_ERROR",
                )
            }

            is IllegalStateException -> {
                logger.debug("GraphQL state error: ${exception.message}")
                buildErrorResponse(
                    handlerParameters,
                    exception.message ?: "Operation failed",
                    "STATE_ERROR",
                )
            }

            is StorageUnavailableException -> {
                logger.warn("Storage service unavailable: ${exception.message}", exception.cause)
                buildErrorResponse(
                    handlerParameters,
                    exception.message ?: "Storage service is currently unavailable. Please try again later.",
                    "STORAGE_UNAVAILABLE",
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
        errorCode: String,
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

        val error = errorBuilder.extensions(buildExtensions(errorCode)).build()

        return CompletableFuture.completedFuture(
            DataFetcherExceptionHandlerResult.newResult().error(error).build(),
        )
    }

    private fun buildExtensions(code: String): Map<String, Any?> {
        return mapOf(
            "code" to code,
            "service" to "assets",
        )
    }
}
