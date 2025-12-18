package io.github.salomax.neotool.security.config

import io.micronaut.context.annotation.ConfigurationProperties
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank

/**
 * Configuration properties for JWT token generation and validation.
 *
 * Properties can be set via environment variables or application.yml:
 * - jwt.secret: Secret key for signing tokens (default: change-me-in-production)
 * - jwt.access-token-expiration-seconds: Access token expiration in seconds (default: 900 = 15 minutes)
 * - jwt.refresh-token-expiration-seconds: Refresh token expiration in seconds (default: 604800 = 7 days)
 */
@ConfigurationProperties("jwt")
data class JwtConfig(
    /**
     * Secret key for signing JWT tokens.
     * In production, use a strong random secret (at least 32 characters).
     * Can be set via JWT_SECRET environment variable.
     */
    @get:NotBlank
    val secret: String = System.getenv("JWT_SECRET") ?: "change-me-in-production-use-strong-random-secret-min-32-chars",
    /**
     * Access token expiration time in seconds.
     * Default: 900 seconds (15 minutes)
     * Can be set via JWT_ACCESS_TOKEN_EXPIRATION_SECONDS environment variable.
     * Minimum 1 minute
     */
    @get:Min(60)
    val accessTokenExpirationSeconds: Long =
        System.getenv("JWT_ACCESS_TOKEN_EXPIRATION_SECONDS")?.toLongOrNull() ?: 900L,
    /**
     * Refresh token expiration time in seconds.
     * Default: 604800 seconds (7 days)
     * Can be set via JWT_REFRESH_TOKEN_EXPIRATION_SECONDS environment variable.
     * Minimum 1 hour
     */
    @get:Min(3600)
    val refreshTokenExpirationSeconds: Long =
        System.getenv("JWT_REFRESH_TOKEN_EXPIRATION_SECONDS")?.toLongOrNull() ?: 604800L,
)
