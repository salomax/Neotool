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
     * Supports both user access tokens and service tokens with optional user context.
     * This method is public for future REST/gRPC integration.
     *
     * @param token The JWT token string
     * @return RequestPrincipal with principal type, identity, token, and permissions
     * @throws AuthenticationRequiredException if token is missing, invalid, or not a valid token
     */
    fun fromToken(token: String): RequestPrincipal {
        if (token.isBlank()) {
            throw AuthenticationRequiredException("Access token is required")
        }

        // Check if token is a service token
        if (jwtService.isServiceToken(token)) {
            // Extract service ID
            val serviceId =
                jwtService.getServiceIdFromToken(token)
                    ?: throw AuthenticationRequiredException("Invalid service token: missing service ID")

            // Extract service permissions
            val servicePermissions = jwtService.getPermissionsFromToken(token) ?: emptyList()

            // Extract user context if present (user context is computed by issuer, not caller)
            val userId = jwtService.getUserIdFromServiceToken(token)
            val userPermissions = jwtService.getUserPermissionsFromServiceToken(token)

            return RequestPrincipal(
                principalType = PrincipalType.SERVICE,
                userId = userId,
                serviceId = serviceId,
                token = token,
                permissionsFromToken = servicePermissions,
                userPermissions = userPermissions,
            )
        }

        // Handle user access token (existing flow)
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
            principalType = PrincipalType.USER,
            userId = userId,
            serviceId = null,
            token = token,
            permissionsFromToken = permissions,
            userPermissions = null,
        )
    }
}
