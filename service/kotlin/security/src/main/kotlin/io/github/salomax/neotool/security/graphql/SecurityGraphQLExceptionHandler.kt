package io.github.salomax.neotool.security.graphql

import graphql.GraphQLError
import graphql.execution.DataFetcherExceptionHandler
import graphql.execution.DataFetcherExceptionHandlerParameters
import graphql.execution.DataFetcherExceptionHandlerResult
import io.github.salomax.neotool.common.exception.GraphQLOptimisticLockExceptionHandler
import io.github.salomax.neotool.common.graphql.GraphQLPayloadException
import io.github.salomax.neotool.common.graphql.payload.GraphQLError as PayloadGraphQLError
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

                val errorBuilder = GraphQLError.newError()
                    .message("Authentication required")
                
                // Only set path and location if they're not null to avoid NPE
                // when GraphQL tries to derive them from DataFetchingEnvironment
                // Note: sourceLocation access can throw NPE if field is null, so we wrap it in try-catch
                handlerParameters.path?.let { errorBuilder.path(it) }
                try {
                    handlerParameters.sourceLocation?.let { errorBuilder.location(it) }
                } catch (e: NullPointerException) {
                    // sourceLocation can throw NPE if field is null, ignore it
                }
                
                val error = errorBuilder
                    .extensions(buildExtensions("UNAUTHENTICATED"))
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

                val errorBuilder = GraphQLError.newError()
                    .message(errorMessage)
                
                // Only set path and location if they're not null to avoid NPE
                // Note: sourceLocation access can throw NPE if field is null, so we wrap it in try-catch
                handlerParameters.path?.let { errorBuilder.path(it) }
                try {
                    handlerParameters.sourceLocation?.let { errorBuilder.location(it) }
                } catch (e: NullPointerException) {
                    // sourceLocation can throw NPE if field is null, ignore it
                }
                
                val error = errorBuilder
                    .extensions(buildExtensions("FORBIDDEN", action))
                    .build()

                CompletableFuture.completedFuture(
                    DataFetcherExceptionHandlerResult.newResult()
                        .error(error)
                        .build(),
                )
            }
            is GraphQLPayloadException -> {
                logger.debug("GraphQL payload exception: ${exception.message}")
                val payloadErrors =
                    exception.errors.ifEmpty {
                        listOf(
                            PayloadGraphQLError(
                                field = listOf("general"),
                                message = exception.message ?: "Operation failed",
                                code = "GRAPHQL_PAYLOAD_ERROR",
                            ),
                        )
                    }

                val errors =
                    payloadErrors.map { payloadError ->
                        val errorBuilder = GraphQLError.newError()
                            .message(payloadError.message)
                        
                        // Only set path and location if they're not null to avoid NPE
                        // Note: sourceLocation access can throw NPE if field is null, so we wrap it in try-catch
                        handlerParameters.path?.let { errorBuilder.path(it) }
                        try {
                            handlerParameters.sourceLocation?.let { errorBuilder.location(it) }
                        } catch (e: NullPointerException) {
                            // sourceLocation can throw NPE if field is null, ignore it
                        }
                        
                        errorBuilder
                            .extensions(
                                buildExtensions(
                                    payloadError.code ?: "GRAPHQL_PAYLOAD_ERROR",
                                    null,
                                    payloadError.field,
                                ),
                            )
                            .build()
                    }

                CompletableFuture.completedFuture(
                    DataFetcherExceptionHandlerResult.newResult()
                        .errors(errors)
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

    private fun buildExtensions(
        code: String,
        action: String? = null,
        field: List<String>? = null,
    ): Map<String, Any?> {
        val extensions = mutableMapOf<String, Any?>(
            "code" to code,
            "service" to "security",
        )
        if (!action.isNullOrBlank()) {
            extensions["action"] = action
        }
        if (!field.isNullOrEmpty()) {
            extensions["field"] = field
        }
        return extensions
    }
}
