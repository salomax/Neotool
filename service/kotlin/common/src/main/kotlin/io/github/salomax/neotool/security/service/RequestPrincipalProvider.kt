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
    private val principalDecoder: TokenPrincipalDecoder,
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
        val cachedPrincipal =
            try {
                env.graphQlContext.getOrEmpty<RequestPrincipal>("requestPrincipal").orElse(null)
            } catch (e: Exception) {
                null
            }
        if (cachedPrincipal != null) {
            return cachedPrincipal
        }

        val token =
            try {
                env.graphQlContext.getOrEmpty<String>("token").orElse(null)
            } catch (e: Exception) {
                null
            }

        if (token.isNullOrBlank()) {
            throw AuthenticationRequiredException("Access token is required")
        }

        val principal = fromToken(token)

        try {
            env.graphQlContext.put("requestPrincipal", principal)
        } catch (e: Exception) {
            logger.warn(e) { "Failed to cache request principal in GraphQL context" }
        }

        return principal
    }

    /**
     * Extract and validate request principal from a token via the configured decoder.
     *
     * @param token The token string (e.g., JWT)
     * @return RequestPrincipal
     * @throws AuthenticationRequiredException if token is missing or invalid
     */
    fun fromToken(token: String): RequestPrincipal {
        if (token.isBlank()) {
            throw AuthenticationRequiredException("Access token is required")
        }
        return principalDecoder.fromToken(token)
    }
}
