package io.github.salomax.neotool.common.security.jwt

import io.github.salomax.neotool.common.security.config.JwtConfig
import io.github.salomax.neotool.common.security.exception.AuthenticationRequiredException
import io.github.salomax.neotool.common.security.key.KeyManager
import io.github.salomax.neotool.common.security.key.KeyManagerFactory
import io.jsonwebtoken.Claims
import io.jsonwebtoken.JwtException
import io.jsonwebtoken.Jwts
import jakarta.inject.Singleton
import mu.KotlinLogging
import java.security.PublicKey
import java.security.interfaces.RSAPublicKey
import java.time.Instant
import java.util.Base64
import java.util.UUID

/**
 * Service for JWT token validation.
 *
 * This service is responsible for validating and parsing JWT tokens.
 * All modules (security, app, assets, assistant) use this service to validate tokens.
 *
 * Implements JWT validation best practices:
 * - Uses RSA-SHA256 (RS256) algorithm only
 * - Extracts key ID from token header for multi-key support
 * - Secure key management via KeyManager
 * - Throws exceptions on validation failure for proper error handling
 *
 * @see https://tools.ietf.org/html/rfc7519
 */
@Singleton
class JwtTokenValidator(
    private val jwtConfig: JwtConfig,
    private val keyManagerFactory: KeyManagerFactory,
) {
    private val logger = KotlinLogging.logger {}

    private val keyManager: KeyManager by lazy {
        keyManagerFactory.getKeyManager()
    }

    private fun getPublicKey(keyId: String? = null): PublicKey? =
        keyManager.getPublicKey(keyId ?: jwtConfig.keyId ?: "default")

    /**
     * Validate and parse a JWT token using RS256 algorithm.
     * Extracts key ID from token header if present, otherwise uses default key.
     *
     * @param token The JWT token string to validate
     * @return Claims if token is valid
     * @throws AuthenticationRequiredException if public key is missing or token validation fails
     * @throws JwtException if token validation fails (wrapped in AuthenticationRequiredException)
     */
    @Throws(AuthenticationRequiredException::class)
    fun validateToken(token: String): Claims {
        // Extract key ID from token header if present
        val keyId =
            try {
                val parts = token.split(".")
                if (parts.size >= 2) {
                    val headerJson =
                        String(
                            Base64
                                .getUrlDecoder()
                                .decode(parts[0]),
                        )
                    // Simple JSON parsing for "kid" field
                    val kidMatch = Regex("\"kid\"\\s*:\\s*\"([^\"]+)\"").find(headerJson)
                    kidMatch?.groupValues?.get(1)
                } else {
                    null
                }
            } catch (e: Exception) {
                logger.debug { "Failed to extract key ID from token header: ${e.message}" }
                null
            } ?: jwtConfig.keyId ?: "default"

        // Get public key for RS256 validation
        val publicKey =
            getPublicKey(keyId)
                ?: throw AuthenticationRequiredException(
                    "JWT public key is missing for key ID: $keyId. Cannot validate token without public key.",
                )

        try {
            val claims =
                Jwts
                    .parser()
                    .verifyWith(publicKey as RSAPublicKey)
                    .build()
                    .parseSignedClaims(token)
                    .payload
            logger.debug { "Token validated successfully with RS256 (kid: $keyId)" }
            return claims
        } catch (e: JwtException) {
            throw AuthenticationRequiredException("Token validation failed: ${e.message}")
        } catch (e: Exception) {
            throw AuthenticationRequiredException("Token validation failed: ${e.message}")
        }
    }

    /**
     * Extract user ID from a validated JWT token.
     *
     * @param token The JWT token string
     * @return User ID if token is valid
     * @throws AuthenticationRequiredException if token validation fails
     */
    @Throws(AuthenticationRequiredException::class)
    fun getUserIdFromToken(token: String): UUID? {
        val claims = validateToken(token)
        return try {
            UUID.fromString(claims.subject)
        } catch (e: Exception) {
            throw AuthenticationRequiredException("Invalid user ID in token subject: ${e.message}")
        }
    }

    /**
     * Extract permissions from a validated JWT token.
     *
     * @param token The JWT token string
     * @return List of permission names if token contains permissions claim, empty list otherwise
     * @throws AuthenticationRequiredException if token validation fails
     */
    @Throws(AuthenticationRequiredException::class)
    fun getPermissionsFromToken(token: String): List<String>? {
        val claims = validateToken(token)
        return try {
            val permissionsClaim = claims["permissions"]
            if (permissionsClaim == null) {
                return null
            }
            // Handle List type casting - permissions are stored as List<String>
            @Suppress("UNCHECKED_CAST")
            when (val permissions = permissionsClaim) {
                is List<*> -> {
                    permissions.mapNotNull { it?.toString() }
                }

                else -> {
                    logger.warn { "Invalid permissions claim type in token: ${permissions.javaClass.name}" }
                    null
                }
            }
        } catch (e: AuthenticationRequiredException) {
            throw e
        } catch (e: Exception) {
            logger.warn { "Error extracting permissions from token: ${e.message}" }
            null
        }
    }

    /**
     * Check if a token is a service token.
     *
     * @param token The JWT token string
     * @return true if token is a valid service token, false otherwise
     * @throws AuthenticationRequiredException if token validation fails
     */
    @Throws(AuthenticationRequiredException::class)
    fun isServiceToken(token: String): Boolean {
        val claims = validateToken(token)
        return claims["type"] == "service"
    }

    /**
     * Extract service ID from a validated service JWT token.
     *
     * @param token The JWT token string
     * @return Service ID if token is valid and is a service token, null otherwise
     * @throws AuthenticationRequiredException if token validation fails
     */
    @Throws(AuthenticationRequiredException::class)
    fun getServiceIdFromToken(token: String): UUID? {
        val claims = validateToken(token)
        if (claims["type"] != "service") {
            return null
        }
        return try {
            UUID.fromString(claims.subject)
        } catch (e: Exception) {
            throw AuthenticationRequiredException("Invalid service ID in token subject: ${e.message}")
        }
    }

    /**
     * Extract audience from a validated JWT token.
     *
     * @param token The JWT token string
     * @return Audience if token contains aud claim, null otherwise
     * @throws AuthenticationRequiredException if token validation fails
     */
    @Throws(AuthenticationRequiredException::class)
    fun getAudienceFromToken(token: String): String? {
        val claims = validateToken(token)
        return claims.audience?.firstOrNull() ?: claims["aud"]?.toString()
    }

    /**
     * Extract user ID from a service token with user context.
     *
     * @param token The JWT token string
     * @return User ID if token is valid, is a service token, and contains user_id claim, null otherwise
     * @throws AuthenticationRequiredException if token validation fails
     */
    @Throws(AuthenticationRequiredException::class)
    fun getUserIdFromServiceToken(token: String): UUID? {
        val claims = validateToken(token)
        if (claims["type"] != "service") {
            return null
        }
        val userIdClaim = claims["user_id"] ?: return null
        return try {
            UUID.fromString(userIdClaim.toString())
        } catch (e: Exception) {
            throw AuthenticationRequiredException("Invalid user ID in service token: ${e.message}")
        }
    }

    /**
     * Extract user permissions from a service token with user context.
     *
     * @param token The JWT token string
     * @return List of user permission names if token contains user_permissions claim, null otherwise
     * @throws AuthenticationRequiredException if token validation fails
     */
    @Throws(AuthenticationRequiredException::class)
    fun getUserPermissionsFromServiceToken(token: String): List<String>? {
        val claims = validateToken(token)
        if (claims["type"] != "service") {
            return null
        }
        val userPermissionsClaim = claims["user_permissions"] ?: return null
        return try {
            @Suppress("UNCHECKED_CAST")
            when (val permissions = userPermissionsClaim) {
                is List<*> -> {
                    permissions.mapNotNull { it?.toString() }
                }

                else -> {
                    logger.warn { "Invalid user_permissions claim type in token: ${permissions.javaClass.name}" }
                    null
                }
            }
        } catch (e: AuthenticationRequiredException) {
            throw e
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
     * @throws AuthenticationRequiredException if token validation fails
     */
    @Throws(AuthenticationRequiredException::class)
    fun isAccessToken(token: String): Boolean {
        val claims = validateToken(token)
        val type = claims["type"]?.toString()
        return type == "access"
    }

    /**
     * Check if a token is a refresh token.
     *
     * @param token The JWT token string
     * @return true if token is a valid refresh token, false otherwise
     * @throws AuthenticationRequiredException if token validation fails
     */
    @Throws(AuthenticationRequiredException::class)
    fun isRefreshToken(token: String): Boolean {
        val claims = validateToken(token)
        return claims["type"] == "refresh"
    }

    /**
     * Get the expiration time of a token.
     *
     * @param token The JWT token string
     * @return Expiration Instant if token is valid, null otherwise
     * @throws AuthenticationRequiredException if token validation fails
     */
    @Throws(AuthenticationRequiredException::class)
    fun getTokenExpiration(token: String): Instant? {
        val claims = validateToken(token)
        return claims.expiration?.toInstant()
    }

    /**
     * Extract token type from a validated JWT token.
     *
     * @param token The JWT token string
     * @return Token type ("access" or "refresh") if token is valid, null otherwise
     * @throws AuthenticationRequiredException if token validation fails
     */
    @Throws(AuthenticationRequiredException::class)
    fun getTokenType(token: String): String? {
        val claims = validateToken(token)
        return claims["type"]?.toString()
    }

    /**
     * Extract email from a validated JWT token.
     *
     * @param token The JWT token string
     * @return Email if token contains email claim, null otherwise
     * @throws AuthenticationRequiredException if token validation fails
     */
    @Throws(AuthenticationRequiredException::class)
    fun getEmailFromToken(token: String): String? {
        val claims = validateToken(token)
        return claims["email"]?.toString()
    }
}
