package io.github.salomax.neotool.common.security.key

import io.github.salomax.neotool.common.security.config.JwtConfig
import jakarta.inject.Singleton
import mu.KotlinLogging
import java.security.PrivateKey
import java.security.PublicKey

/**
 * Key manager implementation that loads keys from files or environment variables.
 * This is the fallback implementation when Vault is not available.
 * Maintains backward compatibility with existing JwtService behavior.
 */
@Singleton
class FileKeyManager(
    private val jwtConfig: JwtConfig,
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
        // For file-based manager, keyId is ignored (single key pair)
        if (cachedPublicKey != null) {
            return cachedPublicKey
        }

        val key = JwtKeyManager.loadPublicKey(jwtConfig.publicKeyPath, jwtConfig.publicKey)
        if (key != null) {
            cachedPublicKey = key
            logger.debug { "Loaded public key from file/env" }
        } else {
            logger.debug { "No public key configured" }
        }
        return key
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
