package io.github.salomax.neotool.security.service

import graphql.schema.DataFetchingEnvironment
import io.github.salomax.neotool.security.service.exception.AuthenticationRequiredException
import jakarta.inject.Singleton
import mu.KotlinLogging

/**
 * Provider for extracting and validating request principals from various sources.
 * Supports GraphQL context extraction and direct token validation for future REST/gRPC integration.
 */
@Singleton
class RequestPrincipalProvider(
    private val jwtService: JwtService,
) {
    private val logger = KotlinLogging.logger {}

    /**
     * Extract and validate request principal from GraphQL context.
     * Caches the principal in the GraphQL context to avoid revalidation in subsequent data fetchers.
     *
     * @param env The GraphQL DataFetchingEnvironment
     * @return RequestPrincipal with user ID, token, and permissions
     * @throws AuthenticationRequiredException if token is missing or invalid
     */
    fun fromGraphQl(env: DataFetchingEnvironment): RequestPrincipal {
        // Check cache first to avoid revalidation
        val cachedPrincipal =
            try {
                env.graphQlContext.getOrEmpty<RequestPrincipal>("requestPrincipal").orElse(null)
            } catch (e: Exception) {
                // Handle case where getOrEmpty() might throw for type mismatches
                null
            }
        if (cachedPrincipal != null) {
            return cachedPrincipal
        }

        // Extract token from GraphQL context
        // Token is stored as non-nullable String in GraphQLControllerBase
        // Use getOrEmpty() which is the recommended way to safely access context values
        val token =
            try {
                env.graphQlContext.getOrEmpty<String>("token").orElse(null)
            } catch (e: Exception) {
                // Handle case where getOrEmpty() might throw for type mismatches
                null
            }

        if (token == null || token.isBlank()) {
            throw AuthenticationRequiredException("Access token is required")
        }

        // Validate and create principal
        val principal = fromToken(token)

        // Cache principal in GraphQL context for subsequent data fetchers
        try {
            env.graphQlContext.put("requestPrincipal", principal)
        } catch (e: Exception) {
            logger.warn(e) { "Failed to cache request principal in GraphQL context" }
        }

        return principal
    }

    /**
     * Extract and validate request principal from a JWT token.
     * This method is public for future REST/gRPC integration.
     *
     * @param token The JWT token string
     * @return RequestPrincipal with user ID, token, and permissions
     * @throws AuthenticationRequiredException if token is missing, invalid, or not an access token
     */
    fun fromToken(token: String): RequestPrincipal {
        if (token.isBlank()) {
            throw AuthenticationRequiredException("Access token is required")
        }

        // Validate token is an access token
        if (!jwtService.isAccessToken(token)) {
            throw AuthenticationRequiredException("Invalid or expired access token")
        }

        // Extract user ID
        val userId =
            jwtService.getUserIdFromToken(token)
                ?: throw AuthenticationRequiredException("Invalid token: missing user ID")

        // Extract permissions from token
        val permissions = jwtService.getPermissionsFromToken(token) ?: emptyList()

        return RequestPrincipal(
            userId = userId,
            token = token,
            permissionsFromToken = permissions,
        )
    }
}
