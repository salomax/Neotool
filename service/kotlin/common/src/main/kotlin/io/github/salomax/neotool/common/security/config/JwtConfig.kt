package io.github.salomax.neotool.common.security.config

import io.micronaut.context.annotation.ConfigurationProperties
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank

/**
 * JWT signing algorithm.
 */
enum class JwtAlgorithm {
    RS256, // RSA-SHA256 (asymmetric)
}

/**
 * Configuration properties for JWT token generation and validation.
 *
 * Properties can be set via environment variables or application.yml:
 * - jwt.algorithm: Algorithm to use (RS256) - default RS256
 * - jwt.private-key-path: Path to RSA private key file (PEM format) - required for RS256
 * - jwt.public-key-path: Path to RSA public key file (PEM format) - required for RS256
 * - jwt.private-key: Inline RSA private key (PEM or base64) - alternative to path
 * - jwt.public-key: Inline RSA public key (PEM or base64) - alternative to path
 * - jwt.key-id: Key identifier for JWKS (e.g., "kid-1")
 * - jwt.jwks-enabled: Enable JWKS endpoint (default: true)
 * - jwt.jwks-url: URL to fetch JWKS from (for validators)
 * - jwt.access-token-expiration-seconds: Access token expiration in seconds (default: 900 = 15 minutes)
 * - jwt.refresh-token-expiration-seconds: Refresh token expiration in seconds (default: 604800 = 7 days)
 */
@ConfigurationProperties("jwt")
data class JwtConfig(
    /**
     * Secret key (deprecated - not used for JWT signing/validation).
     * Kept for backward compatibility with KeyManager.getSecret() method.
     * JWT now uses RS256 algorithm which requires RSA key pairs, not secrets.
     * Can be set via JWT_SECRET environment variable.
     */
    val secret: String = System.getenv("JWT_SECRET") ?: "change-me-in-production-use-strong-random-secret-min-32-chars",
    /**
     * JWT signing algorithm.
     * Only RS256 (RSA-SHA256) is supported.
     * Requires private/public key pair for signing/validation.
     * Default: RS256
     * Can be set via JWT_ALGORITHM environment variable.
     */
    val algorithm: JwtAlgorithm =
        System.getenv("JWT_ALGORITHM")?.let {
            try {
                JwtAlgorithm.valueOf(it.uppercase())
            } catch (e: IllegalArgumentException) {
                JwtAlgorithm.RS256
            }
        } ?: JwtAlgorithm.RS256,
    /**
     * Path to RSA private key file (PEM format).
     * Required for RS256 signing.
     * Can be set via JWT_PRIVATE_KEY_PATH environment variable.
     */
    val privateKeyPath: String? = System.getenv("JWT_PRIVATE_KEY_PATH"),
    /**
     * Path to RSA public key file (PEM format).
     * Required for RS256 validation.
     * Can be set via JWT_PUBLIC_KEY_PATH environment variable.
     */
    val publicKeyPath: String? = System.getenv("JWT_PUBLIC_KEY_PATH"),
    /**
     * Inline RSA private key (PEM or base64-encoded).
     * Alternative to private-key-path, useful for environment variables.
     * Can be set via JWT_PRIVATE_KEY environment variable.
     */
    val privateKey: String? = System.getenv("JWT_PRIVATE_KEY"),
    /**
     * Inline RSA public key (PEM or base64-encoded).
     * Alternative to public-key-path, useful for environment variables.
     * Can be set via JWT_PUBLIC_KEY environment variable.
     */
    val publicKey: String? = System.getenv("JWT_PUBLIC_KEY"),
    /**
     * Key identifier for JWKS.
     * Used to identify which key was used to sign a token.
     * Can be set via JWT_KEY_ID environment variable.
     */
    val keyId: String? = System.getenv("JWT_KEY_ID") ?: "kid-1",
    /**
     * Enable JWKS endpoint.
     * When true, exposes /.well-known/jwks.json endpoint for public key distribution.
     * Default: true
     * Can be set via JWT_JWKS_ENABLED environment variable.
     */
    val jwksEnabled: Boolean = System.getenv("JWT_JWKS_ENABLED")?.toBoolean() ?: true,
    /**
     * URL to fetch JWKS from (for validator services).
     * If set, validators will fetch public keys from this URL instead of using configured keys.
     * Can be set via JWT_JWKS_URL environment variable.
     */
    val jwksUrl: String? = System.getenv("JWT_JWKS_URL"),
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
