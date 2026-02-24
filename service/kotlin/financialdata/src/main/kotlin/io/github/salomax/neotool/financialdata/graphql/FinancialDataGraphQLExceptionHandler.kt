package io.github.salomax.neotool.financialdata.graphql

import graphql.GraphQLError
import graphql.execution.DataFetcherExceptionHandler
import graphql.execution.DataFetcherExceptionHandlerParameters
import graphql.execution.DataFetcherExceptionHandlerResult
import io.github.salomax.neotool.common.exception.GraphQLOptimisticLockExceptionHandler
import io.github.salomax.neotool.financialdata.domain.fixedincome.FixedIncomeInvalidEffectiveRateException
import java.util.concurrent.CompletableFuture

/**
 * GraphQL error mapping for Financial Data module.
 */
class FinancialDataGraphQLExceptionHandler : DataFetcherExceptionHandler {
    private val nextHandler = GraphQLOptimisticLockExceptionHandler()

    override fun handleException(
        handlerParameters: DataFetcherExceptionHandlerParameters,
    ): CompletableFuture<DataFetcherExceptionHandlerResult> {
        val exception = handlerParameters.exception
        return when (exception) {
            is FixedIncomeInvalidEffectiveRateException -> {
                val errorBuilder =
                    GraphQLError
                        .newError()
                        .message(exception.message ?: "Invalid effective annual rate")
                handlerParameters.path?.let { errorBuilder.path(it) }
                try {
                    handlerParameters.sourceLocation?.let { errorBuilder.location(it) }
                } catch (_: NullPointerException) {
                    // sourceLocation can be null depending on fetcher context
                }
                val error =
                    errorBuilder
                        .extensions(
                            mapOf(
                                "code" to FixedIncomeInvalidEffectiveRateException.CODE,
                                "service" to "financialdata",
                            ),
                        ).build()
                CompletableFuture.completedFuture(
                    DataFetcherExceptionHandlerResult
                        .newResult()
                        .error(error)
                        .build(),
                )
            }
            else -> nextHandler.handleException(handlerParameters)
        }
    }
}
