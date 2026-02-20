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
 * with error codes for frontend i18n translation.
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
            // Handle our custom DomainException with error codes
            is DomainException -> {
                logger.debug("Domain exception: ${exception.errorCode.code} - ${exception.message}")
                buildErrorResponse(
                    handlerParameters,
                    message = exception.errorCode.defaultMessage,
                    code = exception.errorCode.code,
                    parameters = exception.parameters,
                )
            }

            // Handle storage unavailable (legacy exception, should be converted to DomainException)
            is StorageUnavailableException -> {
                logger.warn("Storage service unavailable: ${exception.message}", exception.cause)
                buildErrorResponse(
                    handlerParameters,
                    message = AssetErrorCode.STORAGE_UNAVAILABLE.defaultMessage,
                    code = AssetErrorCode.STORAGE_UNAVAILABLE.code,
                )
            }

            // Handle standard IllegalArgumentException
            is IllegalArgumentException -> {
                logger.debug("GraphQL validation error: ${exception.message}")
                buildErrorResponse(
                    handlerParameters,
                    message = exception.message ?: CommonErrorCode.VALIDATION_ERROR.defaultMessage,
                    code = CommonErrorCode.VALIDATION_ERROR.code,
                )
            }

            // Handle IllegalStateException
            is IllegalStateException -> {
                logger.debug("GraphQL state error: ${exception.message}")
                buildErrorResponse(
                    handlerParameters,
                    message = exception.message ?: CommonErrorCode.INVALID_STATE.defaultMessage,
                    code = CommonErrorCode.INVALID_STATE.code,
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
     * Safely sets path, location, and extensions without throwing NPEs.
     */
    private fun buildErrorResponse(
        handlerParameters: DataFetcherExceptionHandlerParameters,
        message: String,
        code: String,
        parameters: Map<String, Any>? = null,
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

        val error = errorBuilder.extensions(buildExtensions(code, parameters)).build()

        return CompletableFuture.completedFuture(
            DataFetcherExceptionHandlerResult.newResult().error(error).build(),
        )
    }

    /**
     * Build extensions map with error code and optional parameters.
     */
    private fun buildExtensions(
        code: String,
        parameters: Map<String, Any>? = null,
    ): Map<String, Any?> {
        val extensions =
            mutableMapOf<String, Any?>(
                "code" to code,
                "service" to "assets",
            )

        // Add parameters if present
        parameters?.let {
            extensions["parameters"] = it
        }

        return extensions
    }
}
