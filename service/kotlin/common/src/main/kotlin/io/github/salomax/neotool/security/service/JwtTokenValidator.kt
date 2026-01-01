package io.github.salomax.neotool.security.service

import io.github.salomax.neotool.security.config.JwtAlgorithm
import io.github.salomax.neotool.security.config.JwtConfig
import io.jsonwebtoken.Claims
import io.jsonwebtoken.Jwts
import jakarta.inject.Singleton
import mu.KotlinLogging
import java.security.PublicKey
import java.time.Instant
import java.util.UUID
import javax.crypto.SecretKey
import io.jsonwebtoken.security.Keys

/**
 * Service for JWT token validation.
 * 
 * This service is responsible for validating and parsing JWT tokens.
 * All modules (security, app, assets, assistant) use this service to validate tokens.
 *
 * Implements JWT validation best practices:
 * - Supports both HMAC-SHA256 (HS256) and RSA-SHA256 (RS256) algorithms
 * - Dual-mode validation: tries RS256 first, falls back to HS256 if RS256 fails
 * - Extracts key ID from token header for multi-key support
 * - Secure key management via KeyManager
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

    private fun getSecretKey(keyId: String? = null): SecretKey? {
        val secret = keyManager.getSecret(keyId ?: jwtConfig.keyId ?: "default")
        if (secret == null) {
            return null
        }
        if (secret.length < 32) {
            logger.warn { "JWT secret is less than 32 characters. Consider using a longer secret for production." }
        }
        return Keys.hmacShaKeyFor(secret.toByteArray())
    }

    private fun getPublicKey(keyId: String? = null): PublicKey? {
        return keyManager.getPublicKey(keyId ?: jwtConfig.keyId ?: "default")
    }

    /**
     * Validate and parse a JWT token.
     * Supports dual-mode validation: tries RS256 first, falls back to HS256 if RS256 fails.
     * Extracts key ID from token header if present, otherwise uses default key.
     *
     * @param token The JWT token string to validate
     * @return Claims if token is valid, null otherwise
     */
    fun validateToken(token: String): Claims? {
        // Extract key ID from token header if present
        val keyId = try {
            val parts = token.split(".")
            if (parts.size >= 2) {
                val headerJson = String(java.util.Base64.getUrlDecoder().decode(parts[0]))
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

        // Try RS256 first if public key is available
        val publicKey = getPublicKey(keyId)
        if (publicKey != null && (jwtConfig.algorithm == JwtAlgorithm.RS256 || jwtConfig.algorithm == JwtAlgorithm.AUTO)) {
            try {
                val claims = Jwts.parser()
                    .verifyWith(publicKey as java.security.interfaces.RSAPublicKey)
                    .build()
                    .parseSignedClaims(token)
                    .payload
                logger.debug { "Token validated successfully with RS256 (kid: $keyId)" }
                return claims
            } catch (e: Exception) {
                logger.debug { "RS256 validation failed: ${e.message}" }
                // Fall through to try HS256 if AUTO mode
                if (jwtConfig.algorithm == JwtAlgorithm.RS256) {
                    return null // RS256 was required but failed
                }
            }
        }

        // Try HS256 if secret is available and algorithm allows it
        val secretKey = getSecretKey(keyId)
        if (secretKey != null && (jwtConfig.algorithm == JwtAlgorithm.HS256 || jwtConfig.algorithm == JwtAlgorithm.AUTO)) {
            try {
                val claims = Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token)
                    .payload
                logger.debug { "Token validated successfully with HS256 (kid: $keyId)" }
                return claims
            } catch (e: Exception) {
                logger.debug { "HS256 validation failed: ${e.message}" }
                return null
            }
        }

        logger.warn { "No valid key available for token validation (kid: $keyId)" }
        return null
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

