package io.github.salomax.neotool.security.service.jwt

import io.github.salomax.neotool.common.security.config.JwtConfig
import io.github.salomax.neotool.common.security.key.KeyManager
import io.github.salomax.neotool.common.security.principal.AuthContext
import io.github.salomax.neotool.security.key.SecurityKeyManagerFactory
import io.jsonwebtoken.Jwts
import jakarta.inject.Singleton
import mu.KotlinLogging
import java.security.PrivateKey
import java.time.Instant
import java.util.Date
import java.util.UUID

/**
 * Service for JWT token generation (issuance).
 *
 * This service is responsible for signing and issuing JWT tokens.
 * Only the security module should use this service to generate tokens.
 *
 * Implements JWT best practices:
 * - Uses RSA-SHA256 (RS256) algorithm only
 * - Includes standard claims: sub (subject/userId), iat (issued at), exp (expiration), iss (issuer)
 * - Configurable token expiration times
 * - Secure key management via KeyManager
 *
 * @see https://tools.ietf.org/html/rfc7519
 */
@Singleton
class JwtTokenIssuer(
    private val jwtConfig: JwtConfig,
    private val keyManagerFactory: SecurityKeyManagerFactory,
) {
    private val logger = KotlinLogging.logger {}

    private val keyManager: KeyManager by lazy {
        keyManagerFactory.getKeyManager()
    }

    private fun getPrivateKey(keyId: String? = null): PrivateKey? =
        keyManager.getPrivateKey(keyId ?: jwtConfig.keyId ?: "default")

    /**
     * Get the current key ID for JWKS.
     */
    fun getKeyId(): String? = jwtConfig.keyId

    /**
     * Sign a JWT builder with RS256 algorithm using the private key.
     */
    private fun signBuilder(builder: io.jsonwebtoken.JwtBuilder): io.jsonwebtoken.JwtBuilder {
        val keyId = getKeyId()

        // Add key ID to header if available (for JWKS)
        val builderWithHeader =
            if (keyId != null) {
                builder.header().add("kid", keyId).and()
            } else {
                builder
            }

        val privateKey = requireNotNull(getPrivateKey()) { "RS256 requires private key" }
        return builderWithHeader.signWith(privateKey)
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
            Jwts
                .builder()
                .subject(userId.toString())
                .claim("email", email)
                .claim("type", "access")
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiration))
                .issuer("neotool-security-service")

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
    fun generateAccessToken(authContext: AuthContext): String =
        generateAccessToken(
            userId = authContext.userId,
            email = authContext.email,
            permissions = authContext.permissions,
        )

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
            Jwts
                .builder()
                .subject(userId.toString())
                .claim("type", "refresh")
                .id(UUID.randomUUID().toString()) // Add unique JTI to ensure token uniqueness
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiration))
                .issuer("neotool-security-service"),
        ).compact()
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
            Jwts
                .builder()
                .subject(serviceId.toString())
                .claim("type", "service")
                .claim("aud", targetAudience)
                .claim("permissions", permissions)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiration))
                .issuer("neotool-security-service"),
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
            Jwts
                .builder()
                .subject(serviceId.toString())
                .claim("type", "service")
                .claim("aud", targetAudience)
                .claim("permissions", permissions)
                .claim("user_id", userId.toString())
                .claim("user_permissions", userPermissions)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiration))
                .issuer("neotool-security-service"),
        ).compact()
    }
}
