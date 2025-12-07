package io.github.salomax.neotool.security.graphql

import graphql.GraphQLError
import graphql.execution.DataFetcherExceptionHandler
import graphql.execution.DataFetcherExceptionHandlerParameters
import graphql.execution.DataFetcherExceptionHandlerResult
import io.github.salomax.neotool.common.exception.GraphQLOptimisticLockExceptionHandler
import io.github.salomax.neotool.security.service.exception.AuthenticationRequiredException
import io.github.salomax.neotool.security.service.exception.AuthorizationDeniedException
import org.slf4j.LoggerFactory
import java.util.concurrent.CompletableFuture
import java.util.regex.Pattern

/**
 * GraphQL-specific exception handler for security-related exceptions.
 * Converts AuthenticationRequiredException and AuthorizationDeniedException
 * to user-friendly GraphQL error messages without exposing sensitive details.
 *
 * This handler delegates to GraphQLOptimisticLockExceptionHandler for non-security exceptions,
 * which in turn delegates to the default handler for all other exceptions.
 */
class SecurityGraphQLExceptionHandler : DataFetcherExceptionHandler {
    private val logger = LoggerFactory.getLogger(SecurityGraphQLExceptionHandler::class.java)
    private val nextHandler = GraphQLOptimisticLockExceptionHandler()

    // Pattern to extract permission/action from AuthorizationDeniedException message
    // Format: "User <userId> lacks permission '<action>': <reason>"
    private val permissionPattern = Pattern.compile("lacks permission '([^']+)'")

    override fun handleException(
        handlerParameters: DataFetcherExceptionHandlerParameters,
    ): CompletableFuture<DataFetcherExceptionHandlerResult> {
        val exception = handlerParameters.exception

        return when (exception) {
            is AuthenticationRequiredException -> {
                logger.debug("GraphQL authentication required: ${exception.message}")

                val error =
                    GraphQLError.newError()
                        .message("Authentication required")
                        .build()

                CompletableFuture.completedFuture(
                    DataFetcherExceptionHandlerResult.newResult()
                        .error(error)
                        .build(),
                )
            }
            is AuthorizationDeniedException -> {
                logger.debug("GraphQL authorization denied: ${exception.message}")

                // Extract action/permission from exception message if possible
                val action = extractActionFromMessage(exception.message)
                val errorMessage =
                    if (action != null) {
                        "Permission denied: $action"
                    } else {
                        "Permission denied"
                    }

                val error =
                    GraphQLError.newError()
                        .message(errorMessage)
                        .build()

                CompletableFuture.completedFuture(
                    DataFetcherExceptionHandlerResult.newResult()
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

    /**
     * Extract the permission/action from AuthorizationDeniedException message.
     * Returns null if extraction fails.
     */
    private fun extractActionFromMessage(message: String?): String? {
        if (message == null) {
            return null
        }

        val matcher = permissionPattern.matcher(message)
        return if (matcher.find()) {
            matcher.group(1)
        } else {
            null
        }
    }
}
