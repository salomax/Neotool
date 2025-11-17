package io.github.salomax.procureflow.common.exception

import graphql.GraphQLError
import graphql.execution.DataFetcherExceptionHandler
import graphql.execution.DataFetcherExceptionHandlerParameters
import graphql.execution.DataFetcherExceptionHandlerResult
import graphql.execution.SimpleDataFetcherExceptionHandler
import org.hibernate.StaleObjectStateException
import org.hibernate.StaleStateException
import org.hibernate.dialect.lock.OptimisticEntityLockException
import org.slf4j.LoggerFactory
import java.util.concurrent.CompletableFuture

/**
 * GraphQL-specific exception handler for optimistic locking exceptions.
 * Converts optimistic locking exceptions to proper GraphQL errors.
 */
class GraphQLOptimisticLockExceptionHandler : DataFetcherExceptionHandler {

    private val logger = LoggerFactory.getLogger(GraphQLOptimisticLockExceptionHandler::class.java)
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
            else -> {
                // Log unexpected exceptions at debug level (default handler will handle them)
                logger.debug("GraphQL data fetcher exception (handled by default handler): ${exception.javaClass.simpleName} - ${exception.message}")
                // Let other exceptions be handled by the default handler
                // The default handler will convert them to GraphQL errors
                defaultHandler.handleException(handlerParameters)
            }
        }
    }
}
