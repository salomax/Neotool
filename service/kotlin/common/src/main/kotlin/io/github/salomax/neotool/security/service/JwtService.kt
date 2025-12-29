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
     * Generate a JWT service token for service-to-service communication.
     *
     * Service tokens are used for interservice authentication and authorization.
     * They include the service identity and permissions.
     *
     * @param serviceId The service ID to include in the token subject claim
     * @param targetAudience The target service identifier (aud claim)
     * @param permissions List of permission names for the service
     * @return A signed JWT token string
     */
    fun generateServiceToken(
        serviceId: UUID,
        targetAudience: String,
        permissions: List<String>,
    ): String {
        val now = Instant.now()
        val expiration = now.plusSeconds(jwtConfig.accessTokenExpirationSeconds)

        return Jwts.builder()
            .subject(serviceId.toString())
            .claim("type", "service")
            .claim("aud", targetAudience)
            .claim("permissions", permissions)
            .issuedAt(Date.from(now))
            .expiration(Date.from(expiration))
            .signWith(secretKey)
            .compact()
    }

    /**
     * Generate a JWT service token with propagated user context.
     *
     * This token includes both the service identity and the user context.
     * The user permissions are computed by the issuer (not provided by the caller).
     *
     * @param serviceId The service ID to include in the token subject claim
     * @param targetAudience The target service identifier (aud claim)
     * @param permissions List of permission names for the service
     * @param userId The user ID being propagated
     * @param userPermissions List of permission names for the user (computed by issuer)
     * @return A signed JWT token string
     */
    fun generateServiceTokenWithUserContext(
        serviceId: UUID,
        targetAudience: String,
        permissions: List<String>,
        userId: UUID,
        userPermissions: List<String>,
    ): String {
        val now = Instant.now()
        val expiration = now.plusSeconds(jwtConfig.accessTokenExpirationSeconds)

        return Jwts.builder()
            .subject(serviceId.toString())
            .claim("type", "service")
            .claim("aud", targetAudience)
            .claim("permissions", permissions)
            .claim("user_id", userId.toString())
            .claim("user_permissions", userPermissions)
            .issuedAt(Date.from(now))
            .expiration(Date.from(expiration))
            .signWith(secretKey)
            .compact()
    }

    /**
     * Check if a token is a service token.
     *
     * @param token The JWT token string
     * @return true if token is a valid service token, false otherwise
     */
    fun isServiceToken(token: String): Boolean {
        val claims = validateToken(token) ?: return false
        return claims["type"] == "service"
    }

    /**
     * Extract service ID from a validated service JWT token.
     *
     * @param token The JWT token string
     * @return Service ID if token is valid and is a service token, null otherwise
     */
    fun getServiceIdFromToken(token: String): UUID? {
        val claims = validateToken(token) ?: return null
        if (claims["type"] != "service") {
            return null
        }
        return try {
            UUID.fromString(claims.subject)
        } catch (e: Exception) {
            logger.warn { "Invalid service ID in token subject: ${e.message}" }
            null
        }
    }

    /**
     * Extract audience from a validated JWT token.
     *
     * @param token The JWT token string
     * @return Audience if token is valid and contains aud claim, null otherwise
     */
    fun getAudienceFromToken(token: String): String? {
        val claims = validateToken(token) ?: return null
        return claims.audience?.firstOrNull() ?: claims["aud"]?.toString()
    }

    /**
     * Extract user ID from a service token with user context.
     *
     * @param token The JWT token string
     * @return User ID if token is valid, is a service token, and contains user_id claim, null otherwise
     */
    fun getUserIdFromServiceToken(token: String): UUID? {
        val claims = validateToken(token) ?: return null
        if (claims["type"] != "service") {
            return null
        }
        val userIdClaim = claims["user_id"] ?: return null
        return try {
            UUID.fromString(userIdClaim.toString())
        } catch (e: Exception) {
            logger.warn { "Invalid user ID in service token: ${e.message}" }
            null
        }
    }

    /**
     * Extract user permissions from a service token with user context.
     *
     * @param token The JWT token string
     * @return List of user permission names if token is valid and contains user_permissions claim, null otherwise
     */
    fun getUserPermissionsFromServiceToken(token: String): List<String>? {
        val claims = validateToken(token) ?: return null
        if (claims["type"] != "service") {
            return null
        }
        val userPermissionsClaim = claims["user_permissions"] ?: return null
        return try {
            @Suppress("UNCHECKED_CAST")
            when (val permissions = userPermissionsClaim) {
                is List<*> -> permissions.mapNotNull { it?.toString() }
                else -> {
                    logger.warn { "Invalid user_permissions claim type in token: ${permissions.javaClass.name}" }
                    null
                }
            }
        } catch (e: Exception) {
            logger.warn { "Error extracting user permissions from service token: ${e.message}" }
            null
        }
    }

    /**
     * Check if a token is an access token.
     *
     * @param token The JWT token string
     * @return true if token is a valid access token (type="access" and not a service token), false otherwise
     */
    fun isAccessToken(token: String): Boolean {
        val claims = validateToken(token) ?: return false
        val type = claims["type"]?.toString()
        return type == "access"
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
