package io.github.salomax.neotool.assets.graphql

import graphql.GraphQLError
import graphql.execution.DataFetcherExceptionHandler
import graphql.execution.DataFetcherExceptionHandlerParameters
import graphql.execution.DataFetcherExceptionHandlerResult
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

                val errorBuilder =
                    GraphQLError
                        .newError()
                        .message(exception.message ?: "Validation error")

                // Only set path and location if they're not null to avoid NPE
                handlerParameters.path?.let { errorBuilder.path(it) }
                try {
                    handlerParameters.sourceLocation?.let { errorBuilder.location(it) }
                } catch (e: NullPointerException) {
                    // sourceLocation can throw NPE if field is null, ignore it
                }

                val error =
                    errorBuilder
                        .extensions(buildExtensions("VALIDATION_ERROR"))
                        .build()

                CompletableFuture.completedFuture(
                    DataFetcherExceptionHandlerResult
                        .newResult()
                        .error(error)
                        .build(),
                )
            }

            is IllegalStateException -> {
                logger.debug("GraphQL state error: ${exception.message}")

                val errorBuilder =
                    GraphQLError
                        .newError()
                        .message(exception.message ?: "Operation failed")

                // Only set path and location if they're not null to avoid NPE
                handlerParameters.path?.let { errorBuilder.path(it) }
                try {
                    handlerParameters.sourceLocation?.let { errorBuilder.location(it) }
                } catch (e: NullPointerException) {
                    // sourceLocation can throw NPE if field is null, ignore it
                }

                val error =
                    errorBuilder
                        .extensions(buildExtensions("STATE_ERROR"))
                        .build()

                CompletableFuture.completedFuture(
                    DataFetcherExceptionHandlerResult
                        .newResult()
                        .error(error)
                        .build(),
                )
            }

            else -> {
                // Delegate to next handler (GraphQLOptimisticLockExceptionHandler)
                // which will handle optimistic locking exceptions or delegate to default handler
                nextHandler.handleException(handlerParameters)
            }
        }
    }

    private fun buildExtensions(
        code: String,
    ): Map<String, Any?> {
        return mapOf(
            "code" to code,
            "service" to "assets",
        )
    }
}


