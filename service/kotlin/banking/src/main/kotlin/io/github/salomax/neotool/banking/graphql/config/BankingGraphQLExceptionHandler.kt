package io.github.salomax.neotool.banking.graphql.config

import graphql.GraphQLError
import graphql.execution.DataFetcherExceptionHandler
import graphql.execution.DataFetcherExceptionHandlerParameters
import graphql.execution.DataFetcherExceptionHandlerResult
import io.github.salomax.neotool.common.error.CommonErrorCode
import io.github.salomax.neotool.common.error.DomainException
import io.github.salomax.neotool.common.security.exception.AuthenticationRequiredException
import io.github.salomax.neotool.common.security.exception.AuthorizationDeniedException
import jakarta.validation.ConstraintViolationException
import org.hibernate.StaleObjectStateException
import org.hibernate.StaleStateException
import org.hibernate.dialect.lock.OptimisticEntityLockException
import org.slf4j.LoggerFactory
import java.util.Collections
import java.util.IdentityHashMap
import java.util.concurrent.CompletableFuture

/**
 * GraphQL-specific exception handler for banking exceptions.
 * Maps [DomainException] to GraphQL errors with standard extensions:
 * - code: machine-readable error code
 * - service: banking
 * - parameters/field: optional metadata
 */
class BankingGraphQLExceptionHandler : DataFetcherExceptionHandler {
    private val logger = LoggerFactory.getLogger(BankingGraphQLExceptionHandler::class.java)

    /**
     * Some runtimes (transaction managers, reflection, async boundaries) surface a wrapper whose type
     * we do not map, while still preserving the original message in the cause chain — e.g.
     * `Transaction <uuid> not found`. Treat that as a client-safe not-found instead of INTERNAL_ERROR.
     */
    private val transactionMissingMessageHeuristic =
        Regex("""(?i)\btransaction\b.{0,120}\bnot\s+found\b|\btransaction\s+not\s+found\b|\bnão\s+encontrad""")

    override fun handleException(
        handlerParameters: DataFetcherExceptionHandlerParameters,
    ): CompletableFuture<DataFetcherExceptionHandlerResult> {
        val root = handlerParameters.exception
        for (exception in root.linearCauseChain()) {
            mapKnownException(exception, handlerParameters)?.let { return it }
        }
        notFoundFromCauseMessages(root, handlerParameters)?.let { return it }
        logger.error("Unhandled GraphQL exception in banking resolver", root)
        return buildErrorResponse(
            handlerParameters = handlerParameters,
            message = CommonErrorCode.INTERNAL_ERROR.defaultMessage,
            code = CommonErrorCode.INTERNAL_ERROR.code,
        )
    }

    private fun notFoundFromCauseMessages(
        root: Throwable,
        handlerParameters: DataFetcherExceptionHandlerParameters,
    ): CompletableFuture<DataFetcherExceptionHandlerResult>? {
        val text = root.linearCauseChain().joinToString(" ") { it.message ?: "" }.trim()
        if (text.isEmpty() || !transactionMissingMessageHeuristic.containsMatchIn(text)) {
            return null
        }
        logger.debug("GraphQL not found mapped from cause-chain message heuristic")
        return buildErrorResponse(
            handlerParameters = handlerParameters,
            message = CommonErrorCode.RESOURCE_NOT_FOUND.defaultMessage,
            code = CommonErrorCode.RESOURCE_NOT_FOUND.code,
        )
    }

    /**
     * Walks `cause` links (cycle-safe). graphql-java, async fetchers, and transactional proxies often
     * wrap the failure type seen by [DataFetcherExceptionHandlerParameters.exception].
     */
    private fun Throwable.linearCauseChain(): List<Throwable> =
        buildList {
            val seen = Collections.newSetFromMap<Throwable>(IdentityHashMap())
            var current: Throwable? = this@linearCauseChain
            while (current != null) {
                if (!seen.add(current)) break
                add(current)
                current = current.cause
            }
        }

    private fun mapKnownException(
        exception: Throwable,
        handlerParameters: DataFetcherExceptionHandlerParameters,
    ): CompletableFuture<DataFetcherExceptionHandlerResult>? =
        when (exception) {
            is AuthenticationRequiredException -> {
                logger.debug("GraphQL authentication required")
                buildErrorResponse(
                    handlerParameters = handlerParameters,
                    message = "Authentication required",
                    code = "AUTH_AUTHENTICATION_REQUIRED",
                )
            }
            is AuthorizationDeniedException -> {
                logger.debug("GraphQL authorization denied")
                buildErrorResponse(
                    handlerParameters = handlerParameters,
                    message = "Access denied",
                    code = "AUTH_AUTHORIZATION_DENIED",
                )
            }
            is DomainException -> {
                logger.debug(
                    "GraphQL domain exception: code={}, message={}",
                    exception.errorCode.code,
                    exception.errorCode.defaultMessage,
                )
                buildErrorResponse(
                    handlerParameters = handlerParameters,
                    message = exception.errorCode.defaultMessage,
                    code = exception.errorCode.code,
                    parameters = exception.parameters.takeIf { it.isNotEmpty() },
                    field = exception.field?.split(".")?.toList(),
                )
            }
            is ConstraintViolationException -> {
                logger.debug("GraphQL validation error")
                val firstField =
                    exception.constraintViolations
                        .firstOrNull()
                        ?.propertyPath
                        ?.toString()
                        ?.split(".")
                        ?.filter { it.isNotBlank() }
                buildErrorResponse(
                    handlerParameters = handlerParameters,
                    message = CommonErrorCode.INVALID_INPUT.defaultMessage,
                    code = CommonErrorCode.INVALID_INPUT.code,
                    field = firstField?.takeIf { it.isNotEmpty() } ?: listOf("input"),
                )
            }
            is StaleObjectStateException,
            is StaleStateException,
            is OptimisticEntityLockException,
            -> {
                logger.warn("GraphQL optimistic lock conflict in banking resolver", exception)
                buildErrorResponse(
                    handlerParameters = handlerParameters,
                    message = CommonErrorCode.OPTIMISTIC_LOCK_ERROR.defaultMessage,
                    code = CommonErrorCode.OPTIMISTIC_LOCK_ERROR.code,
                )
            }
            is IllegalArgumentException -> {
                logger.debug("GraphQL input error: {}", exception.message)
                buildErrorResponse(
                    handlerParameters = handlerParameters,
                    message = CommonErrorCode.INVALID_INPUT.defaultMessage,
                    code = CommonErrorCode.INVALID_INPUT.code,
                )
            }
            is io.github.salomax.neotool.banking.client.import.nfce.NfceUnavailableException -> {
                logger.warn("GraphQL NFC-e fetch unavailable: {}", exception.message)
                buildErrorResponse(
                    handlerParameters = handlerParameters,
                    message = CommonErrorCode.SERVICE_UNAVAILABLE.defaultMessage,
                    code = CommonErrorCode.SERVICE_UNAVAILABLE.code,
                )
            }
            is io.github.salomax.neotool.banking.client.import.nfce.NfceInvalidException -> {
                logger.warn("GraphQL NFC-e payload invalid: {}", exception.message)
                buildErrorResponse(
                    handlerParameters = handlerParameters,
                    message = CommonErrorCode.INVALID_INPUT.defaultMessage,
                    code = CommonErrorCode.INVALID_INPUT.code,
                    field = listOf("qrCodeUrl"),
                )
            }
            is NoSuchElementException -> {
                logger.debug("GraphQL resource not found: {}", exception.message)
                buildErrorResponse(
                    handlerParameters = handlerParameters,
                    message = CommonErrorCode.RESOURCE_NOT_FOUND.defaultMessage,
                    code = CommonErrorCode.RESOURCE_NOT_FOUND.code,
                )
            }
            else -> null
        }

    private fun buildErrorResponse(
        handlerParameters: DataFetcherExceptionHandlerParameters,
        message: String,
        code: String,
        parameters: Map<String, Any>? = null,
        field: List<String>? = null,
    ): CompletableFuture<DataFetcherExceptionHandlerResult> {
        val errorBuilder = GraphQLError.newError().message(message)
        handlerParameters.path?.let { errorBuilder.path(it) }
        try {
            handlerParameters.sourceLocation?.let { errorBuilder.location(it) }
        } catch (_: NullPointerException) {
            // sourceLocation can be null depending on fetcher context.
        }

        val extensions = mutableMapOf<String, Any?>("code" to code, "service" to "banking")
        if (!parameters.isNullOrEmpty()) extensions["parameters"] = parameters
        if (!field.isNullOrEmpty()) extensions["field"] = field

        val error = errorBuilder.extensions(extensions).build()
        return CompletableFuture.completedFuture(
            DataFetcherExceptionHandlerResult.newResult().error(error).build(),
        )
    }
}
