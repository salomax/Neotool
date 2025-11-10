package io.github.salomax.neotool.exception

import graphql.GraphQLError
import graphql.execution.DataFetcherExceptionHandler
import graphql.execution.DataFetcherExceptionHandlerParameters
import graphql.execution.DataFetcherExceptionHandlerResult
import graphql.execution.SimpleDataFetcherExceptionHandler
import io.github.salomax.neotool.graphql.GraphQLPayloadException
import org.hibernate.StaleObjectStateException
import org.hibernate.StaleStateException
import org.hibernate.dialect.lock.OptimisticEntityLockException
import org.slf4j.LoggerFactory
import java.util.concurrent.CompletableFuture

/**
 * GraphQL-specific exception handler that handles:
 * 1. Optimistic locking exceptions (converts to proper GraphQL errors)
 * 2. GraphQLPayloadException (converts payload errors to GraphQL errors)
 * 3. All other exceptions (delegates to default handler)
 * 
 * This handler is used as the default exception handler for all GraphQL data fetchers.
 */
class GraphQLExceptionHandler : DataFetcherExceptionHandler {

    private val logger = LoggerFactory.getLogger(GraphQLExceptionHandler::class.java)
    private val defaultHandler = SimpleDataFetcherExceptionHandler()

    override fun handleException(handlerParameters: DataFetcherExceptionHandlerParameters): CompletableFuture<DataFetcherExceptionHandlerResult> {
        val exception = handlerParameters.exception
        
        return when (exception) {
            is StaleObjectStateException,
            is StaleStateException,
            is OptimisticEntityLockException -> {
                logger.warn("GraphQL optimistic locking conflict: ${exception.message}")
                
                val error = GraphQLError.newError()
                    .message("The entity was modified by another user. Please refresh and try again.")
                    .build()
                
                CompletableFuture.completedFuture(
                    DataFetcherExceptionHandlerResult.newResult()
                        .error(error)
                        .build()
                )
            }
            is GraphQLPayloadException -> {
                // Convert GraphQLPayloadException errors to proper GraphQL errors
                // This is the expected path for payload-based error handling (e.g., authentication errors)
                val graphQLErrors = exception.errors.map { payloadError ->
                    GraphQLError.newError()
                        .message(payloadError.message)
                        .path(handlerParameters.path)
                        .extensions(mapOf(
                            "code" to (payloadError.code ?: "ERROR"),
                            "field" to payloadError.field
                        ))
                        .build()
                }
                
                CompletableFuture.completedFuture(
                    DataFetcherExceptionHandlerResult.newResult()
                        .errors(graphQLErrors)
                        .build()
                )
            }
            else -> {
                // Log other exceptions at debug level (default handler will handle them)
                logger.debug("GraphQL data fetcher exception (delegating to default handler): ${exception.javaClass.simpleName} - ${exception.message}")
                // Let other exceptions be handled by the default handler
                // The default handler will convert them to GraphQL errors
                defaultHandler.handleException(handlerParameters)
            }
        }
    }
}
