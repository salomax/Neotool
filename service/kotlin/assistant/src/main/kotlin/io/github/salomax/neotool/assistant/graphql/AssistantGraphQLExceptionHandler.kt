package io.github.salomax.neotool.assistant.graphql

import graphql.GraphQLError
import graphql.execution.DataFetcherExceptionHandler
import graphql.execution.DataFetcherExceptionHandlerParameters
import graphql.execution.DataFetcherExceptionHandlerResult
import io.github.salomax.neotool.assistant.provider.error.ProviderClientException
import io.github.salomax.neotool.assistant.service.NoAvailableModelException
import io.github.salomax.neotool.assistant.tool.ToolNotFoundException
import io.github.salomax.neotool.common.error.CommonErrorCode
import io.github.salomax.neotool.common.error.DomainException
import io.github.salomax.neotool.common.graphql.GraphQLPayloadException
import io.github.salomax.neotool.common.security.exception.AuthenticationRequiredException
import io.github.salomax.neotool.common.security.exception.AuthorizationDeniedException
import org.slf4j.LoggerFactory
import java.util.concurrent.CompletableFuture
import io.github.salomax.neotool.common.graphql.payload.GraphQLError as PayloadGraphQLError

/**
 * GraphQL error mapping for Assistant module.
 */
class AssistantGraphQLExceptionHandler : DataFetcherExceptionHandler {
    private val logger = LoggerFactory.getLogger(AssistantGraphQLExceptionHandler::class.java)

    override fun handleException(
        handlerParameters: DataFetcherExceptionHandlerParameters,
    ): CompletableFuture<DataFetcherExceptionHandlerResult> {
        val exception = handlerParameters.exception
        return when (exception) {
            is AuthenticationRequiredException ->
                completedWithError(
                    handlerParameters = handlerParameters,
                    message = AUTHENTICATION_REQUIRED_MESSAGE,
                    code = AUTHENTICATION_REQUIRED_CODE,
                )

            is AuthorizationDeniedException ->
                completedWithError(
                    handlerParameters = handlerParameters,
                    message = AUTHORIZATION_DENIED_MESSAGE,
                    code = AUTHORIZATION_DENIED_CODE,
                )

            is ProviderClientException ->
                completedWithError(
                    handlerParameters = handlerParameters,
                    message = exception.message ?: CommonErrorCode.INTERNAL_ERROR.defaultMessage,
                    code = providerErrorCode(exception),
                )

            is NoAvailableModelException ->
                completedWithError(
                    handlerParameters = handlerParameters,
                    message = exception.message ?: "No available model satisfies routing requirements",
                    code = NO_AVAILABLE_MODEL_CODE,
                )

            is ToolNotFoundException ->
                completedWithError(
                    handlerParameters = handlerParameters,
                    message = exception.message ?: "Requested tool was not found or is not ACTIVE",
                    code = TOOL_NOT_FOUND_CODE,
                    field = listOf("input", "tools"),
                )

            is DomainException ->
                completedWithError(
                    handlerParameters = handlerParameters,
                    message = exception.errorCode.defaultMessage,
                    code = exception.errorCode.code,
                    field = exception.field?.split(".")?.toList(),
                    parameters = exception.parameters.takeIf { it.isNotEmpty() },
                )

            is IllegalArgumentException ->
                completedWithError(
                    handlerParameters = handlerParameters,
                    message = CommonErrorCode.INVALID_INPUT.defaultMessage,
                    code = CommonErrorCode.INVALID_INPUT.code,
                    field = listOf("input"),
                )

            is IllegalStateException -> {
                logger.error("Illegal state in assistant GraphQL resolver", exception)
                completedWithError(
                    handlerParameters = handlerParameters,
                    message = CommonErrorCode.INTERNAL_ERROR.defaultMessage,
                    code = CommonErrorCode.INTERNAL_ERROR.code,
                )
            }

            is GraphQLPayloadException -> {
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

                        handlerParameters.path?.let { errorBuilder.path(it) }
                        try {
                            handlerParameters.sourceLocation?.let { errorBuilder.location(it) }
                        } catch (_: NullPointerException) {
                            // sourceLocation can be null depending on fetcher context
                        }

                        errorBuilder
                            .extensions(
                                buildExtensions(
                                    code = payloadError.code,
                                    field = payloadError.field,
                                    parameters = payloadError.parameters,
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
                logger.error("Unhandled GraphQL exception in assistant resolver", exception)
                completedWithError(
                    handlerParameters = handlerParameters,
                    message = CommonErrorCode.INTERNAL_ERROR.defaultMessage,
                    code = CommonErrorCode.INTERNAL_ERROR.code,
                )
            }
        }
    }

    private fun completedWithError(
        handlerParameters: DataFetcherExceptionHandlerParameters,
        message: String,
        code: String,
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
            // sourceLocation can be null depending on fetcher context
        }

        val error =
            errorBuilder
                .extensions(buildExtensions(code, field, parameters))
                .build()

        return CompletableFuture.completedFuture(
            DataFetcherExceptionHandlerResult
                .newResult()
                .error(error)
                .build(),
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
                "service" to "assistant",
            )
        if (!field.isNullOrEmpty()) {
            extensions["field"] = field
        }
        if (!parameters.isNullOrEmpty()) {
            extensions["parameters"] = parameters
        }
        return extensions
    }

    private fun providerErrorCode(exception: ProviderClientException): String {
        return when (exception.errorType) {
            io.github.salomax.neotool.assistant.provider.error.ErrorType.RATE_LIMIT,
            io.github.salomax.neotool.assistant.provider.error.ErrorType.RATE_LIMIT_EXCEEDED,
            -> "ASSISTANT_RATE_LIMIT"

            io.github.salomax.neotool.assistant.provider.error.ErrorType.TIMEOUT ->
                "ASSISTANT_TIMEOUT"

            io.github.salomax.neotool.assistant.provider.error.ErrorType.SERVER_ERROR,
            io.github.salomax.neotool.assistant.provider.error.ErrorType.PROVIDER_UNAVAILABLE,
            -> "ASSISTANT_PROVIDER_UNAVAILABLE"

            io.github.salomax.neotool.assistant.provider.error.ErrorType.AUTH_ERROR ->
                "ASSISTANT_PROVIDER_AUTH_ERROR"

            io.github.salomax.neotool.assistant.provider.error.ErrorType.INVALID_REQUEST,
            io.github.salomax.neotool.assistant.provider.error.ErrorType.CONTEXT_LENGTH_EXCEEDED,
            io.github.salomax.neotool.assistant.provider.error.ErrorType.PROVIDER_RESPONSE_INVALID,
            -> "ASSISTANT_INVALID_REQUEST"

            io.github.salomax.neotool.assistant.provider.error.ErrorType.UNKNOWN ->
                CommonErrorCode.INTERNAL_ERROR.code
        }
    }

    private companion object {
        const val AUTHENTICATION_REQUIRED_CODE = "AUTH_AUTHENTICATION_REQUIRED"
        const val AUTHENTICATION_REQUIRED_MESSAGE = "Authentication required"
        const val AUTHORIZATION_DENIED_CODE = "AUTH_AUTHORIZATION_DENIED"
        const val AUTHORIZATION_DENIED_MESSAGE = "Access denied"
        const val NO_AVAILABLE_MODEL_CODE = "ASSISTANT_NO_AVAILABLE_MODEL"
        const val TOOL_NOT_FOUND_CODE = "ASSISTANT_TOOL_NOT_FOUND"
    }
}
