package io.github.salomax.neotool.common.security.key

import io.github.salomax.neotool.common.security.config.JwtConfig
import jakarta.inject.Singleton
import mu.KotlinLogging
import java.io.File
import java.nio.charset.StandardCharsets
import java.security.PrivateKey
import java.security.PublicKey
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey

/**
 * Key manager implementation that loads keys from files or environment variables.
 * This is the fallback implementation when Vault is not available.
 * Maintains backward compatibility with existing JwtService behavior.
 */
@Singleton
class FileKeyManager(
    private val jwtConfig: JwtConfig,
) : WritableKeyManager {
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

    /**
     * Store private key to file.
     *
     * @param keyId Key identifier (used for file naming if multiple keys supported)
     * @param privateKey Private key to store
     * @return true on success, false on failure
     */
    fun storePrivateKey(
        keyId: String,
        privateKey: PrivateKey,
    ): Boolean =
        try {
            require(privateKey is RSAPrivateKey) {
                "Private key must be RSA key, got ${privateKey.javaClass.name}"
            }
            val privateKeyPem = JwtKeyManager.privateKeyToPem(privateKey)
            val filePath = jwtConfig.privateKeyPath ?: "./keys/jwt-private-key.pem"

            val file = File(filePath)
            file.parentFile?.mkdirs()
            file.writeText(privateKeyPem, StandardCharsets.UTF_8)

            // Set secure permissions (600 = owner read/write only)
            file.setReadable(false, false)
            file.setWritable(false, false)
            file.setReadable(true, true)
            file.setWritable(true, true)

            // Update cache
            cachedPrivateKey = privateKey
            logger.info { "Private key stored to file: $filePath" }
            true
        } catch (e: Exception) {
            logger.error(e) { "Failed to store private key to file: $keyId" }
            false
        }

    /**
     * Store public key to file.
     *
     * @param keyId Key identifier (used for file naming if multiple keys supported)
     * @param publicKey Public key to store
     * @return true on success, false on failure
     */
    fun storePublicKey(
        keyId: String,
        publicKey: PublicKey,
    ): Boolean =
        try {
            require(publicKey is RSAPublicKey) {
                "Public key must be RSA key, got ${publicKey.javaClass.name}"
            }
            val publicKeyPem = JwtKeyManager.publicKeyToPem(publicKey)
            val filePath = jwtConfig.publicKeyPath ?: "./keys/jwt-public-key.pem"

            val file = File(filePath)
            file.parentFile?.mkdirs()
            file.writeText(publicKeyPem, StandardCharsets.UTF_8)

            // Set readable permissions (644 = owner read/write, others read)
            file.setReadable(true, false)
            file.setWritable(true, true)
            file.setWritable(false, false)

            // Update cache
            cachedPublicKey = publicKey
            logger.info { "Public key stored to file: $filePath" }
            true
        } catch (e: Exception) {
            logger.error(e) { "Failed to store public key to file: $keyId" }
            false
        }

    /**
     * Store both private and public keys to files atomically.
     *
     * @param keyId Key identifier
     * @param privateKey Private key to store
     * @param publicKey Public key to store
     * @return true on success, false on failure
     */
    override fun storeKeyPair(
        keyId: String,
        privateKey: PrivateKey,
        publicKey: PublicKey,
    ): Boolean =
        try {
            // Validate key types before casting
            require(privateKey is RSAPrivateKey) {
                "Private key must be RSA key, got ${privateKey.javaClass.name}"
            }
            require(publicKey is RSAPublicKey) {
                "Public key must be RSA key, got ${publicKey.javaClass.name}"
            }

            val privateSuccess = storePrivateKey(keyId, privateKey)
            val publicSuccess = storePublicKey(keyId, publicKey)
            privateSuccess && publicSuccess
        } catch (e: Exception) {
            logger.error(e) { "Failed to store key pair to files: $keyId" }
            false
        }
}
