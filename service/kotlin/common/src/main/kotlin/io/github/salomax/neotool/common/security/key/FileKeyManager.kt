package io.github.salomax.neotool.common.security.key

import io.github.salomax.neotool.common.security.config.JwtConfig
import jakarta.inject.Singleton
import mu.KotlinLogging
import java.security.PrivateKey
import java.security.PublicKey

/**
 * Key manager implementation that loads keys from files, environment variables, or JWKS endpoint.
 * Supports JWKS for validator services (assets, app, assistant) that don't need Vault access.
 * This is the fallback implementation when Vault is not available.
 * Maintains backward compatibility with existing JwtService behavior.
 */
@Singleton
class FileKeyManager(
    private val jwtConfig: JwtConfig,
    private val jwksClient: JwksClient?,
) : KeyManager {
    private val logger = KotlinLogging.logger {}

    // Cache keys in memory to avoid repeated file reads
    // Using @Volatile for thread-safe visibility
    @Volatile
    private var cachedPrivateKey: PrivateKey? = null

    @Volatile
    private var cachedPublicKey: PublicKey? = null

    @Volatile
    private var cachedSecret: String? = null

    override fun getPrivateKey(keyId: String): PrivateKey? {
        // For file-based manager, keyId is ignored (single key pair)
        // In future, could support multiple key pairs by keyId
        if (cachedPrivateKey != null) {
            return cachedPrivateKey
        }

        val key = JwtKeyManager.loadPrivateKey(jwtConfig.privateKeyPath, jwtConfig.privateKey)
        if (key != null) {
            cachedPrivateKey = key
            logger.debug { "Loaded private key from file/env" }
        } else {
            logger.debug { "No private key configured" }
        }
        return key
    }

    override fun getPublicKey(keyId: String): PublicKey? {
        // If JWKS URL is configured, use JWKS client to fetch public keys
        val jwksUrl = jwtConfig.jwksUrl
        if (!jwksUrl.isNullOrBlank()) {
            if (jwksClient == null) {
                logger.warn {
                    "JWKS URL is configured ($jwksUrl) but JwksClient is not available. " +
                        "Check if HttpClient is properly configured."
                }
            } else {
                logger.debug { "Fetching public key from JWKS endpoint: $jwksUrl for kid: $keyId" }
                try {
                    val key = jwksClient.getPublicKey(jwksUrl, keyId)
                    if (key != null) {
                        logger.info { "Successfully fetched public key from JWKS for kid: $keyId" }
                        return key
                    } else {
                        logger.warn { "Failed to fetch public key from JWKS for kid: $keyId from $jwksUrl" }
                    }
                } catch (e: Exception) {
                    logger.error(e) { "Error fetching public key from JWKS endpoint $jwksUrl for kid: $keyId" }
                }
            }
        } else {
            logger.debug { "JWKS URL is not configured, falling back to file/env-based keys" }
        }

        // Fallback to file/env-based keys (for backward compatibility or when JWKS is not configured)
        if (cachedPublicKey != null) {
            logger.debug { "Using cached public key from file/env" }
            return cachedPublicKey
        }

        val key = JwtKeyManager.loadPublicKey(jwtConfig.publicKeyPath, jwtConfig.publicKey)
        if (key != null) {
            cachedPublicKey = key
            logger.debug { "Loaded public key from file/env" }
        } else {
            logger.warn {
                "No public key configured via file/env and JWKS is not available. " +
                    "Cannot validate JWT tokens."
            }
        }
        return key
    }

    /**
     * Set the public key at runtime.
     * This is used by services that fetch the key from an external source (like Vault) at startup
     * and need to inject it into this manager for validation validation.
     *
     * @param publicKey The public key to set
     */
    fun setPublicKey(publicKey: PublicKey) {
        this.cachedPublicKey = publicKey
        logger.info { "Public key injected at runtime" }
    }

    override fun getSecret(keyId: String): String? {
        // For file-based manager, keyId is ignored (single secret)
        if (cachedSecret != null) {
            return cachedSecret
        }

        val secret =
            jwtConfig.secret.takeIf {
                it.isNotBlank() &&
                    it != "change-me-in-production-use-strong-random-secret-min-32-chars"
            }
        if (secret != null) {
            cachedSecret = secret
            logger.debug { "Loaded secret from config" }
        } else {
            logger.debug { "No secret configured" }
        }
        return secret
    }

    override fun isAvailable(): Boolean {
        // File-based manager is always available (may not have keys, but manager itself is available)
        return true
    }
}
