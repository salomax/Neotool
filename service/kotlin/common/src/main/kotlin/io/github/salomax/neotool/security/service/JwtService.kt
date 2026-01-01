package io.github.salomax.neotool.security.service

import io.github.salomax.neotool.security.config.JwtAlgorithm
import io.github.salomax.neotool.security.config.JwtConfig
import io.jsonwebtoken.Claims
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.JwtParser
import io.jsonwebtoken.security.Keys
import jakarta.inject.Singleton
import mu.KotlinLogging
import java.security.PrivateKey
import java.security.PublicKey
import java.time.Instant
import java.util.Date
import java.util.UUID
import javax.crypto.SecretKey

/**
 * Service for JWT token generation and validation.
 *
 * @deprecated This service has been split into:
 * - [JwtTokenIssuer] (in security module) - for token generation
 * - [JwtTokenValidator] (in common module) - for token validation
 *
 * Migration guide:
 * - For token generation: Use [JwtTokenIssuer] instead
 * - For token validation: Use [JwtTokenValidator] instead
 *
 * This class is kept for backward compatibility but will be removed in a future version.
 *
 * Implements JWT best practices:
 * - Supports both HMAC-SHA256 (HS256) and RSA-SHA256 (RS256) algorithms
 * - Includes standard claims: sub (subject/userId), iat (issued at), exp (expiration), iss (issuer)
 * - Configurable token expiration times
 * - Secure key management via configuration
 * - Dual-mode validation: tries RS256 first, falls back to HS256
 *
 * @see https://tools.ietf.org/html/rfc7519
 */
@Deprecated(
    message = "Use JwtTokenIssuer for token generation and JwtTokenValidator for token validation",
    replaceWith = ReplaceWith("JwtTokenIssuer or JwtTokenValidator", imports = ["io.github.salomax.neotool.security.service.JwtTokenIssuer", "io.github.salomax.neotool.security.service.JwtTokenValidator"])
)
@Singleton
class JwtService(
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

    private fun getPrivateKey(keyId: String? = null): PrivateKey? {
        return keyManager.getPrivateKey(keyId ?: jwtConfig.keyId ?: "default")
    }

    private fun getPublicKey(keyId: String? = null): PublicKey? {
        return keyManager.getPublicKey(keyId ?: jwtConfig.keyId ?: "default")
    }

    // Backward compatibility: lazy properties for single key pair
    private val secretKey: SecretKey? by lazy {
        getSecretKey()
    }

    private val privateKey: PrivateKey? by lazy {
        getPrivateKey()
    }

    private val publicKey: PublicKey? by lazy {
        getPublicKey()
    }

    /**
     * Get the signing key based on configured algorithm.
     */
    private val signingKey: Any by lazy {
        when (jwtConfig.algorithm) {
            JwtAlgorithm.RS256 -> {
                privateKey ?: throw IllegalStateException("RS256 requires private key. Configure jwt.private-key-path or jwt.private-key")
            }
            JwtAlgorithm.HS256 -> {
                secretKey ?: throw IllegalStateException("HS256 requires secret. Configure jwt.secret")
            }
            JwtAlgorithm.AUTO -> {
                privateKey ?: secretKey ?: throw IllegalStateException("AUTO mode requires either private key (RS256) or secret (HS256)")
            }
        }
    }

    /**
     * Get the current signing algorithm.
     */
    fun getAlgorithm(): JwtAlgorithm {
        return when {
            jwtConfig.algorithm == JwtAlgorithm.RS256 -> JwtAlgorithm.RS256
            jwtConfig.algorithm == JwtAlgorithm.HS256 -> JwtAlgorithm.HS256
            privateKey != null -> JwtAlgorithm.RS256
            else -> JwtAlgorithm.HS256
        }
    }

    /**
     * Get the current key ID for JWKS.
     */
    fun getKeyId(): String? {
        return jwtConfig.keyId
    }

    /**
     * Sign a JWT builder with the appropriate key and algorithm.
     */
    private fun signBuilder(builder: io.jsonwebtoken.JwtBuilder): io.jsonwebtoken.JwtBuilder {
        val algorithm = getAlgorithm()
        val keyId = getKeyId()

        // Add key ID to header if available (for JWKS)
        val builderWithHeader = if (keyId != null) {
            builder.header().add("kid", keyId).and()
        } else {
            builder
        }

        return when (algorithm) {
            JwtAlgorithm.RS256 -> {
                requireNotNull(privateKey) { "RS256 requires private key" }
                builderWithHeader.signWith(privateKey)
            }
            JwtAlgorithm.HS256 -> {
                requireNotNull(secretKey) { "HS256 requires secret key" }
                builderWithHeader.signWith(secretKey)
            }
            JwtAlgorithm.AUTO -> {
                // AUTO mode: prefer RS256 if available
                if (privateKey != null) {
                    builderWithHeader.signWith(privateKey)
                } else {
                    requireNotNull(secretKey) { "AUTO mode requires either private key or secret" }
                    builderWithHeader.signWith(secretKey)
                }
            }
        }
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
                .issuer("neotool-security-service") // Add issuer claim

        // Always include permissions claim as array (empty [] when no permissions)
        builder.claim("permissions", permissions ?: emptyList<String>())

        return signBuilder(builder).compact()
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

        return signBuilder(
            Jwts.builder()
                .subject(userId.toString())
                .claim("type", "refresh")
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiration))
                .issuer("neotool-security-service")
        ).compact()
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

        return signBuilder(
            Jwts.builder()
                .subject(serviceId.toString())
                .claim("type", "service")
                .claim("aud", targetAudience)
                .claim("permissions", permissions)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiration))
                .issuer("neotool-security-service")
        ).compact()
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

        return signBuilder(
            Jwts.builder()
                .subject(serviceId.toString())
                .claim("type", "service")
                .claim("aud", targetAudience)
                .claim("permissions", permissions)
                .claim("user_id", userId.toString())
                .claim("user_permissions", userPermissions)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiration))
                .issuer("neotool-security-service")
        ).compact()
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
