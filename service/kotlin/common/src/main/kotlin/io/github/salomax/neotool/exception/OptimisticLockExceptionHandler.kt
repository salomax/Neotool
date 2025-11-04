package io.github.salomax.neotool.exception

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
 */
@Singleton
@Produces
@Requires(classes = [StaleObjectStateException::class, StaleStateException::class, OptimisticEntityLockException::class])
class OptimisticLockExceptionHandler : ExceptionHandler<Exception, HttpResponse<Map<String, Any>>> {

    private val logger = LoggerFactory.getLogger(OptimisticLockExceptionHandler::class.java)

    override fun handle(request: HttpRequest<*>, exception: Exception): HttpResponse<Map<String, Any>> {
        logger.warn("Optimistic locking conflict detected: ${exception.message}")
        
        val errorResponse = mapOf<String, Any>(
            "error" to "OPTIMISTIC_LOCK_CONFLICT",
            "message" to "The entity was modified by another user. Please refresh and try again.",
            "details" to (exception.message ?: "Unknown error")
        )
        
        return HttpResponse.status<Map<String, Any>>(HttpStatus.CONFLICT).body(errorResponse)
    }
}
