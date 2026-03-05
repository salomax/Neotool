package io.github.salomax.neotool.assets.graphql

import graphql.GraphQLError
import graphql.execution.DataFetcherExceptionHandler
import graphql.execution.DataFetcherExceptionHandlerParameters
import graphql.execution.DataFetcherExceptionHandlerResult
import io.github.salomax.neotool.assets.error.AssetErrorCode
import io.github.salomax.neotool.assets.exception.StorageUnavailableException
import io.github.salomax.neotool.common.error.CommonErrorCode
import io.github.salomax.neotool.common.error.DomainException
import io.github.salomax.neotool.common.graphql.GraphQLPayloadException
import io.github.salomax.neotool.common.security.exception.AuthenticationRequiredException
import io.github.salomax.neotool.common.security.exception.AuthorizationDeniedException
import org.hibernate.StaleObjectStateException
import org.hibernate.StaleStateException
import org.hibernate.dialect.lock.OptimisticEntityLockException
import org.slf4j.LoggerFactory
import java.util.concurrent.CompletableFuture
import io.github.salomax.neotool.common.graphql.payload.GraphQLError as PayloadGraphQLError

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

            is StaleObjectStateException,
            is StaleStateException,
            is OptimisticEntityLockException,
            -> {
                logger.warn("GraphQL optimistic lock conflict")
                completedWithError(
                    handlerParameters = handlerParameters,
                    message = CommonErrorCode.OPTIMISTIC_LOCK_ERROR.defaultMessage,
                    code = CommonErrorCode.OPTIMISTIC_LOCK_ERROR.code,
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

            is IllegalStateException -> {
                logger.error("GraphQL illegal state in assets resolver", exception)
                completedWithError(
                    handlerParameters = handlerParameters,
                    message = CommonErrorCode.INTERNAL_ERROR.defaultMessage,
                    code = CommonErrorCode.INTERNAL_ERROR.code,
                )
            }

            is StorageUnavailableException -> {
                logger.warn("Storage service unavailable", exception)
                completedWithError(
                    handlerParameters = handlerParameters,
                    message = AssetErrorCode.ASSET_STORAGE_UNAVAILABLE.defaultMessage,
                    code = AssetErrorCode.ASSET_STORAGE_UNAVAILABLE.code,
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
                            // sourceLocation can throw NPE if field is null in GraphQL Java internals.
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
                logger.error("Unhandled GraphQL exception in assets resolver", exception)
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
            // sourceLocation can throw NPE if field is null in GraphQL Java internals.
        }

        val error =
            errorBuilder
                .extensions(buildExtensions(code, field, parameters))
                .build()

        return CompletableFuture.completedFuture(
            DataFetcherExceptionHandlerResult.newResult().error(error).build(),
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
                "service" to "assets",
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
