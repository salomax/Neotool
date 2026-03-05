package io.github.salomax.neotool.security.graphql

import graphql.GraphQLError
import graphql.execution.DataFetcherExceptionHandler
import graphql.execution.DataFetcherExceptionHandlerParameters
import graphql.execution.DataFetcherExceptionHandlerResult
import io.github.salomax.neotool.common.error.CommonErrorCode
import io.github.salomax.neotool.common.error.DomainException
import io.github.salomax.neotool.common.exception.GraphQLOptimisticLockExceptionHandler
import io.github.salomax.neotool.common.graphql.GraphQLPayloadException
import io.github.salomax.neotool.common.security.exception.AuthenticationRequiredException
import io.github.salomax.neotool.common.security.exception.AuthorizationDeniedException
import io.github.salomax.neotool.security.error.SecurityErrorCode
import org.slf4j.LoggerFactory
import java.util.concurrent.CompletableFuture
import java.util.regex.Pattern
import io.github.salomax.neotool.common.graphql.payload.GraphQLError as PayloadGraphQLError

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
                completedWithError(
                    handlerParameters = handlerParameters,
                    message = SecurityErrorCode.AUTHENTICATION_REQUIRED.defaultMessage,
                    code = SecurityErrorCode.AUTHENTICATION_REQUIRED.code,
                )
            }

            is AuthorizationDeniedException -> {
                logger.debug("GraphQL authorization denied: ${exception.message}")

                // Extract action/permission from exception message if possible
                val action = extractActionFromMessage(exception.message)
                completedWithError(
                    handlerParameters = handlerParameters,
                    message = SecurityErrorCode.AUTHORIZATION_DENIED.defaultMessage,
                    code = SecurityErrorCode.AUTHORIZATION_DENIED.code,
                    action = action,
                )
            }

            is DomainException -> {
                logger.debug("GraphQL domain exception: ${exception.errorCode.code}")
                completedWithError(
                    handlerParameters = handlerParameters,
                    message = exception.errorCode.defaultMessage,
                    code = exception.errorCode.code,
                    field = exception.field?.split(".")?.toList(),
                    parameters = exception.parameters.takeIf { it.isNotEmpty() },
                )
            }

            is IllegalArgumentException -> {
                logger.debug("GraphQL invalid input: ${exception.message}")
                completedWithError(
                    handlerParameters = handlerParameters,
                    message = CommonErrorCode.INVALID_INPUT.defaultMessage,
                    code = CommonErrorCode.INVALID_INPUT.code,
                    field = listOf("input"),
                )
            }

            is GraphQLPayloadException -> {
                logger.debug("GraphQL payload exception: ${exception.message}")
                val payloadErrors =
                    exception.errors.ifEmpty {
                        listOf(
                            PayloadGraphQLError(
                                field = listOf("general"),
                                message = CommonErrorCode.INTERNAL_ERROR.defaultMessage,
                                code = CommonErrorCode.INTERNAL_ERROR.code,
                            ),
                        )
                    }

                val errors =
                    payloadErrors.map { payloadError ->
                        val errorBuilder =
                            GraphQLError
                                .newError()
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
                                    payloadError.code,
                                    null,
                                    payloadError.field,
                                    payloadError.parameters,
                                ),
                            ).build()
                    }

                CompletableFuture.completedFuture(
                    DataFetcherExceptionHandlerResult
                        .newResult()
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
        parameters: Map<String, Any>? = null,
    ): Map<String, Any?> {
        val extensions =
            mutableMapOf<String, Any?>(
                "code" to code,
                "service" to "security",
            )
        if (!action.isNullOrBlank()) {
            extensions["action"] = action
        }
        if (!field.isNullOrEmpty()) {
            extensions["field"] = field
        }
        if (!parameters.isNullOrEmpty()) {
            extensions["parameters"] = parameters
        }
        return extensions
    }

    private fun completedWithError(
        handlerParameters: DataFetcherExceptionHandlerParameters,
        message: String,
        code: String,
        action: String? = null,
        field: List<String>? = null,
        parameters: Map<String, Any>? = null,
    ): CompletableFuture<DataFetcherExceptionHandlerResult> {
        val errorBuilder =
            GraphQLError
                .newError()
                .message(message)

        handlerParameters.path?.let { errorBuilder.path(it) }
        try {
            handlerParameters.sourceLocation?.let { errorBuilder.location(it) }
        } catch (_: NullPointerException) {
            // sourceLocation can throw NPE if field is null, ignore it
        }

        val error =
            errorBuilder
                .extensions(buildExtensions(code, action, field, parameters))
                .build()

        return CompletableFuture.completedFuture(
            DataFetcherExceptionHandlerResult
                .newResult()
                .error(error)
                .build(),
        )
    }
}
