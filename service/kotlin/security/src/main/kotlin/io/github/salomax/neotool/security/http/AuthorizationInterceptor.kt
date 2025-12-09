package io.github.salomax.neotool.security.http

import io.github.salomax.neotool.security.service.AuthorizationManager
import io.github.salomax.neotool.security.service.RequestPrincipalProvider
import io.github.salomax.neotool.security.service.exception.AuthenticationRequiredException
import io.micronaut.aop.InterceptorBean
import io.micronaut.aop.MethodInterceptor
import io.micronaut.aop.MethodInvocationContext
import io.micronaut.http.context.ServerRequestContext
import jakarta.inject.Singleton
import mu.KotlinLogging

/**
 * Interceptor for [RequiresAuthorization] annotation that enforces permission-based
 * authorization on REST endpoints.
 *
 * This interceptor:
 * 1. Extracts the Bearer token from the Authorization header
 * 2. Validates the token and creates a RequestPrincipal via [RequestPrincipalProvider]
 * 3. Checks authorization using [AuthorizationManager]
 * 4. Proceeds with method execution if authorized
 *
 * Future REST modules can use [RequiresAuthorization] annotation on their controllers
 * to automatically get authorization enforcement without implementing the logic themselves.
 */
@Singleton
@InterceptorBean(RequiresAuthorization::class)
class AuthorizationInterceptor(
    private val requestPrincipalProvider: RequestPrincipalProvider,
    private val authorizationManager: AuthorizationManager,
) : MethodInterceptor<Any, Any> {
    private val logger = KotlinLogging.logger {}

    override fun intercept(context: MethodInvocationContext<Any, Any>): Any? {
        // Check if annotation is present
        val annotation =
            context.annotationMetadata
                .findAnnotation(RequiresAuthorization::class.java)
                .orElse(null) ?: return context.proceed()

        // Get permission from annotation
        val permission = annotation.stringValue("permission").orElse("")
        if (permission.isBlank()) {
            logger.warn { "RequiresAuthorization annotation found but permission is blank" }
            return context.proceed()
        }

        // Get current HTTP request from context
        val request =
            ServerRequestContext.currentRequest<Any>()
                .orElseThrow {
                    logger.error { "RequiresAuthorization used but no HTTP request context available" }
                    AuthenticationRequiredException("No HTTP request context available")
                }

        // Extract Authorization header
        val authHeader =
            request.headers.get("Authorization")
                ?: throw AuthenticationRequiredException("Authentication required")

        // Extract Bearer token
        val token =
            extractBearerToken(authHeader)
                ?: throw AuthenticationRequiredException("Invalid authorization header format")

        // Validate token and create principal
        val principal =
            try {
                requestPrincipalProvider.fromToken(token)
            } catch (e: AuthenticationRequiredException) {
                logger.debug(e) { "Token validation failed: ${e.message}" }
                throw e
            }

        // Check authorization
        try {
            authorizationManager.require(principal, permission)
        } catch (e: Exception) {
            logger.debug(e) { "Authorization check failed for permission '$permission': ${e.message}" }
            throw e
        }

        // Authorization passed, proceed with method execution
        return context.proceed()
    }

    /**
     * Extract Bearer token from Authorization header.
     * Supports format: "Bearer <token>" or "bearer <token>"
     *
     * @param authHeader The Authorization header value
     * @return The token string, or null if format is invalid
     */
    private fun extractBearerToken(authHeader: String): String? {
        if (!authHeader.startsWith("Bearer ", ignoreCase = true)) {
            return null
        }
        val token = authHeader.substring(7).trim()
        return if (token.isNotBlank()) token else null
    }
}
