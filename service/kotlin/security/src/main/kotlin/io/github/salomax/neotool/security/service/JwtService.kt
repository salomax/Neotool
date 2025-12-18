package io.github.salomax.neotool.security.service

import io.github.salomax.neotool.security.config.JwtConfig
import io.jsonwebtoken.Claims
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import jakarta.inject.Singleton
import mu.KotlinLogging
import java.time.Instant
import java.util.Date
import java.util.UUID
import javax.crypto.SecretKey

/**
 * Service for JWT token generation and validation.
 *
 * Implements JWT best practices:
 * - Uses HMAC-SHA256 (HS256) algorithm for signing
 * - Includes standard claims: sub (subject/userId), iat (issued at), exp (expiration)
 * - Configurable token expiration times
 * - Secure secret key management via configuration
 *
 * @see https://tools.ietf.org/html/rfc7519
 */
@Singleton
class JwtService(
    private val jwtConfig: JwtConfig,
) {
    private val logger = KotlinLogging.logger {}

    private val secretKey: SecretKey by lazy {
        val secret = jwtConfig.secret
        if (secret.length < 32) {
            logger.warn { "JWT secret is less than 32 characters. Consider using a longer secret for production." }
        }
        Keys.hmacShaKeyFor(secret.toByteArray())
    }

    /**
     * Generate a JWT access token for a user.
     *
     * Access tokens are short-lived (default: 15 minutes) and used for API authentication.
     *
     * @param userId The user ID to include in the token subject claim
     * @param email The user's email (included as a custom claim)
     * @param permissions Optional list of permission names to include in the token claims
     * @return A signed JWT token string
     */
    fun generateAccessToken(
        userId: UUID,
        email: String,
        permissions: List<String>? = null,
    ): String {
        val now = Instant.now()
        val expiration = now.plusSeconds(jwtConfig.accessTokenExpirationSeconds)

        val builder =
            Jwts.builder()
                .subject(userId.toString())
                .claim("email", email)
                .claim("type", "access")
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiration))

        // Always include permissions claim as array (empty [] when no permissions)
        builder.claim("permissions", permissions ?: emptyList<String>())

        return builder
            .signWith(secretKey)
            .compact()
    }

    /**
     * Generate a JWT access token from an AuthContext.
     *
     * This method extracts userId, email, and permissions from the AuthContext
     * to build the JWT token. Token issuance is agnostic of how the user authenticated.
     *
     * Access tokens are short-lived (default: 15 minutes) and used for API authentication.
     *
     * @param authContext The normalized authentication context containing user identity and permissions
     * @return A signed JWT token string
     */
    fun generateAccessToken(authContext: AuthContext): String {
        return generateAccessToken(
            userId = authContext.userId,
            email = authContext.email,
            permissions = authContext.permissions,
        )
    }

    /**
     * Generate a JWT refresh token for a user.
     *
     * Refresh tokens are long-lived (default: 7 days) and used to obtain new access tokens.
     * They should be stored securely (e.g., in database) and can be revoked.
     *
     * @param userId The user ID to include in the token subject claim
     * @return A signed JWT token string
     */
    fun generateRefreshToken(userId: UUID): String {
        val now = Instant.now()
        val expiration = now.plusSeconds(jwtConfig.refreshTokenExpirationSeconds)

        return Jwts.builder()
            .subject(userId.toString())
            .claim("type", "refresh")
            .issuedAt(Date.from(now))
            .expiration(Date.from(expiration))
            .signWith(secretKey)
            .compact()
    }

    /**
     * Validate and parse a JWT token.
     *
     * @param token The JWT token string to validate
     * @return Claims if token is valid, null otherwise
     */
    fun validateToken(token: String): Claims? {
        return try {
            Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .payload
        } catch (e: Exception) {
            logger.debug { "Token validation failed: ${e.message}" }
            null
        }
    }

    /**
     * Extract user ID from a validated JWT token.
     *
     * @param token The JWT token string
     * @return User ID if token is valid, null otherwise
     */
    fun getUserIdFromToken(token: String): UUID? {
        val claims = validateToken(token) ?: return null
        return try {
            UUID.fromString(claims.subject)
        } catch (e: Exception) {
            logger.warn { "Invalid user ID in token subject: ${e.message}" }
            null
        }
    }

    /**
     * Extract permissions from a validated JWT token.
     *
     * @param token The JWT token string
     * @return List of permission names if token is valid and contains permissions claim, null otherwise
     */
    fun getPermissionsFromToken(token: String): List<String>? {
        val claims = validateToken(token) ?: return null
        return try {
            val permissionsClaim = claims["permissions"]
            if (permissionsClaim == null) {
                return null
            }
            // Handle List type casting - permissions are stored as List<String>
            @Suppress("UNCHECKED_CAST")
            when (val permissions = permissionsClaim) {
                is List<*> -> permissions.mapNotNull { it?.toString() }
                else -> {
                    logger.warn { "Invalid permissions claim type in token: ${permissions.javaClass.name}" }
                    null
                }
            }
        } catch (e: Exception) {
            logger.warn { "Error extracting permissions from token: ${e.message}" }
            null
        }
    }

    /**
     * Check if a token is an access token.
     *
     * @param token The JWT token string
     * @return true if token is a valid access token, false otherwise
     */
    fun isAccessToken(token: String): Boolean {
        val claims = validateToken(token) ?: return false
        return claims["type"] == "access"
    }

    /**
     * Check if a token is a refresh token.
     *
     * @param token The JWT token string
     * @return true if token is a valid refresh token, false otherwise
     */
    fun isRefreshToken(token: String): Boolean {
        val claims = validateToken(token) ?: return false
        return claims["type"] == "refresh"
    }

    /**
     * Get the expiration time of a token.
     *
     * @param token The JWT token string
     * @return Expiration Instant if token is valid, null otherwise
     */
    fun getTokenExpiration(token: String): Instant? {
        val claims = validateToken(token) ?: return null
        return claims.expiration?.toInstant()
    }

    /**
     * Extract token type from a validated JWT token.
     *
     * @param token The JWT token string
     * @return Token type ("access" or "refresh") if token is valid, null otherwise
     */
    fun getTokenType(token: String): String? {
        val claims = validateToken(token) ?: return null
        return claims["type"]?.toString()
    }

    /**
     * Extract email from a validated JWT token.
     *
     * @param token The JWT token string
     * @return Email if token is valid and contains email claim, null otherwise
     */
    fun getEmailFromToken(token: String): String? {
        val claims = validateToken(token) ?: return null
        return claims["email"]?.toString()
    }
}
