package io.github.salomax.neotool.common.exception

import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.annotation.Produces
import io.micronaut.http.server.exceptions.ExceptionHandler
import jakarta.inject.Singleton
import org.hibernate.StaleObjectStateException
import org.hibernate.StaleStateException
import org.hibernate.dialect.lock.OptimisticEntityLockException
import org.slf4j.LoggerFactory

/**
 * Generic exception handler for optimistic locking exceptions.
 * Handles both JPA and Hibernate optimistic locking exceptions.
 * 
 * Note: This handler only catches the specific optimistic lock exception types.
 * More specific handlers (like StaleObjectHandler) should take precedence.
 */
@Singleton
@Produces
@Requires(classes = [StaleObjectStateException::class, StaleStateException::class, OptimisticEntityLockException::class])
class OptimisticLockExceptionHandler : ExceptionHandler<Exception, HttpResponse<Map<String, Any>>> {

    private val logger = LoggerFactory.getLogger(OptimisticLockExceptionHandler::class.java)

    override fun handle(request: HttpRequest<*>, exception: Exception): HttpResponse<Map<String, Any>> {
        // Only handle specific optimistic lock exception types
        // This handler should not catch other exceptions - Micronaut should match more specific handlers first
        val isOptimisticLockException = exception is StaleObjectStateException || 
                                       exception is StaleStateException || 
                                       exception is OptimisticEntityLockException
        
        if (!isOptimisticLockException) {
            // This shouldn't happen if Micronaut's exception handling is working correctly,
            // Log the error for debugging
            logger.error("OptimisticLockExceptionHandler caught non-optimistic-lock exception: ${exception.javaClass.name} - ${exception.message}")
            logger.error("Exception stack trace:", exception)
            // Return a generic error response instead of re-throwing
            // Re-throwing would cause the exception to bubble up and might not be handled properly
            val errorResponse = mapOf<String, Any>(
                "error" to "INTERNAL_ERROR",
                "message" to "An unexpected error occurred",
                "details" to (exception.message ?: "Unknown error")
            )
            return HttpResponse.status<Map<String, Any>>(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse)
        }
        
        logger.warn("Optimistic locking conflict detected: ${exception.message}")
        
        val errorResponse = mapOf<String, Any>(
            "error" to "OPTIMISTIC_LOCK_CONFLICT",
            "message" to "The entity was modified by another user. Please refresh and try again.",
            "details" to (exception.message ?: "Unknown error")
        )
        
        return HttpResponse.status<Map<String, Any>>(HttpStatus.CONFLICT).body(errorResponse)
    }
}
