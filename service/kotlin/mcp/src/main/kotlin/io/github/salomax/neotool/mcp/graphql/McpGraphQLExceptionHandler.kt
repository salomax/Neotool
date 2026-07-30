package io.github.salomax.neotool.mcp.graphql

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
import org.slf4j.LoggerFactory
import java.util.concurrent.CompletableFuture
import io.github.salomax.neotool.common.graphql.payload.GraphQLError as PayloadGraphQLError

class McpGraphQLExceptionHandler : DataFetcherExceptionHandler {
    private val logger = LoggerFactory.getLogger(McpGraphQLExceptionHandler::class.java)
    private val nextHandler = GraphQLOptimisticLockExceptionHandler()

    override fun handleException(
        handlerParameters: DataFetcherExceptionHandlerParameters,
    ): CompletableFuture<DataFetcherExceptionHandlerResult> {
        val exception = handlerParameters.exception

        return when (exception) {
            is AuthenticationRequiredException -> {
                logger.debug("GraphQL authentication required")
                completedWithError(
                    handlerParameters = handlerParameters,
                    message = AUTHENTICATION_REQUIRED_MESSAGE,
                    code = AUTHENTICATION_REQUIRED_CODE,
                )
            }

            is AuthorizationDeniedException -> {
                logger.debug("GraphQL authorization denied")
                completedWithError(
                    handlerParameters = handlerParameters,
                    message = AUTHORIZATION_DENIED_MESSAGE,
                    code = AUTHORIZATION_DENIED_CODE,
                )
            }

            is DomainException -> {
                logger.debug("GraphQL domain exception: {}", exception.errorCode.code)
                completedWithError(
                    handlerParameters = handlerParameters,
                    message = exception.errorCode.defaultMessage,
                    code = exception.errorCode.code,
                    field = exception.field?.split(".")?.toList(),
                    parameters = exception.parameters.takeIf { it.isNotEmpty() },
                )
            }

            is IllegalArgumentException -> {
                logger.debug("GraphQL invalid input")
                completedWithError(
                    handlerParameters = handlerParameters,
                    message = CommonErrorCode.INVALID_INPUT.defaultMessage,
                    code = CommonErrorCode.INVALID_INPUT.code,
                    field = listOf("input"),
                )
            }

            is GraphQLPayloadException -> {
                logger.debug("GraphQL payload exception")
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

                CompletableFuture.completedFuture(
                    DataFetcherExceptionHandlerResult
                        .newResult()
                        .errors(
                            payloadErrors.map { payloadError ->
                                val errorBuilder = GraphQLError.newError().message(payloadError.message)
                                handlerParameters.path?.let { errorBuilder.path(it) }
                                try {
                                    handlerParameters.sourceLocation?.let { errorBuilder.location(it) }
                                } catch (_: NullPointerException) {
                                }
                                errorBuilder
                                    .extensions(
                                        buildExtensions(
                                            code = payloadError.code,
                                            field = payloadError.field,
                                            parameters = payloadError.parameters,
                                        ),
                                    ).build()
                            },
                        ).build(),
                )
            }

            else -> nextHandler.handleException(handlerParameters)
        }
    }

    private fun completedWithError(
        handlerParameters: DataFetcherExceptionHandlerParameters,
        message: String,
        code: String,
        field: List<String>? = null,
        parameters: Map<String, Any>? = null,
    ): CompletableFuture<DataFetcherExceptionHandlerResult> {
        val errorBuilder = GraphQLError.newError().message(message)
        handlerParameters.path?.let { errorBuilder.path(it) }
        try {
            handlerParameters.sourceLocation?.let { errorBuilder.location(it) }
        } catch (_: NullPointerException) {
        }

        return CompletableFuture.completedFuture(
            DataFetcherExceptionHandlerResult
                .newResult()
                .error(
                    errorBuilder
                        .extensions(buildExtensions(code, field, parameters))
                        .build(),
                ).build(),
        )
    }

    private fun buildExtensions(
        code: String,
        field: List<String>? = null,
        parameters: Map<String, Any>? = null,
    ): Map<String, Any?> {
        val extensions =
            mutableMapOf<String, Any?>(
                "code" to code,
                "service" to "mcp",
            )
        if (!field.isNullOrEmpty()) {
            extensions["field"] = field
        }
        if (!parameters.isNullOrEmpty()) {
            extensions["parameters"] = parameters
        }
        return extensions
    }

    private companion object {
        const val AUTHENTICATION_REQUIRED_CODE = "AUTH_AUTHENTICATION_REQUIRED"
        const val AUTHENTICATION_REQUIRED_MESSAGE = "Authentication required"
        const val AUTHORIZATION_DENIED_CODE = "AUTH_AUTHORIZATION_DENIED"
        const val AUTHORIZATION_DENIED_MESSAGE = "Access denied"
    }
}
