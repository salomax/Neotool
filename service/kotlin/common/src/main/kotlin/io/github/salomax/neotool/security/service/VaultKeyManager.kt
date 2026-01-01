package io.github.salomax.neotool.security.service

import io.github.salomax.neotool.security.config.VaultConfig
import io.github.salomax.neotool.security.vault.VaultClient
import io.micronaut.context.annotation.Requires
import jakarta.inject.Singleton
import mu.KotlinLogging
import java.security.PrivateKey
import java.security.PublicKey
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Key manager implementation that retrieves keys from HashiCorp Vault.
 * Includes in-memory caching to reduce Vault API calls.
 * Only created when Vault is enabled.
 */
@Singleton
@Requires(property = "vault.enabled", value = "true")
class VaultKeyManager(
    private val vaultClient: VaultClient,
    private val vaultConfig: VaultConfig,
) : WritableKeyManager {
    private val logger = KotlinLogging.logger {}

    private val cache = ConcurrentHashMap<String, CachedKey>()
    private val cacheLock = ReentrantLock()
    private val cacheTtlMillis = 5 * 60 * 1000L // 5 minutes

    /**
     * Cached key with expiration time.
     */
    private data class CachedKey(
        val key: Any, // PrivateKey, PublicKey, or String (for secret)
        val expiresAt: Long,
    ) {
        fun isExpired(): Boolean = System.currentTimeMillis() > expiresAt
    }

    override fun getPrivateKey(keyId: String): PrivateKey? {
        val cacheKey = "private:$keyId"
        return getCachedOrFetch(cacheKey) {
            val secretPath = "${vaultConfig.secretPath}/$keyId"
            val keyContent = vaultClient.getSecretAsString(secretPath, "private")
                ?: return null

            JwtKeyManager.loadPrivateKey(null, keyContent)
        } as? PrivateKey
    }

    override fun getPublicKey(keyId: String): PublicKey? {
        val cacheKey = "public:$keyId"
        return getCachedOrFetch(cacheKey) {
            val secretPath = "${vaultConfig.secretPath}/$keyId"
            val keyContent = vaultClient.getSecretAsString(secretPath, "public")
                ?: return null

            JwtKeyManager.loadPublicKey(null, keyContent)
        } as? PublicKey
    }

    override fun getSecret(keyId: String): String? {
        val cacheKey = "secret:$keyId"
        return getCachedOrFetch(cacheKey) {
            val secretPath = "${vaultConfig.secretPath}/$keyId"
            vaultClient.getSecretAsString(secretPath, "secret")
        } as? String
    }

    override fun isAvailable(): Boolean {
        return vaultClient.isAvailable()
    }

    /**
     * Get value from cache or fetch from Vault.
     * Thread-safe with double-checked locking pattern.
     */
    private fun <T> getCachedOrFetch(cacheKey: String, fetch: () -> T?): T? {
        // Check cache first
        val cached = cache[cacheKey]
        if (cached != null && !cached.isExpired()) {
            logger.debug { "Using cached key: $cacheKey" }
            @Suppress("UNCHECKED_CAST")
            return cached.key as? T
        }

        // Fetch from Vault with lock
        return cacheLock.withLock {
            // Double-check after acquiring lock
            val cachedAfterLock = cache[cacheKey]
            if (cachedAfterLock != null && !cachedAfterLock.isExpired()) {
                @Suppress("UNCHECKED_CAST")
                return cachedAfterLock.key as? T
            }

            try {
                val value = fetch()
                if (value != null) {
                    val expiresAt = System.currentTimeMillis() + cacheTtlMillis
                    cache[cacheKey] = CachedKey(value, expiresAt)
                    logger.debug { "Fetched and cached key from Vault: $cacheKey" }
                } else {
                    logger.warn { "Key not found in Vault: $cacheKey" }
                }
                value
            } catch (e: Exception) {
                logger.error(e) { "Failed to fetch key from Vault: $cacheKey" }
                // Return cached value even if expired as fallback
                cached?.key?.let {
                    logger.warn { "Using expired cached key as fallback: $cacheKey" }
                    @Suppress("UNCHECKED_CAST")
                    it as? T
                }
            }
        }
    }

    /**
     * Store private key in Vault.
     *
     * @param keyId Key identifier
     * @param privateKey Private key to store
     * @return true on success, false on failure
     */
    fun storePrivateKey(keyId: String, privateKey: PrivateKey): Boolean {
        return try {
            require(privateKey is java.security.interfaces.RSAPrivateKey) { 
                "Private key must be RSA key, got ${privateKey.javaClass.name}" 
            }
            val secretPath = "${vaultConfig.secretPath}/$keyId"
            val privateKeyPem = JwtKeyManager.privateKeyToPem(privateKey)
            
            // Read existing secret to merge with new private key
            val existing = vaultClient.getSecret(secretPath) ?: emptyMap()
            val data = existing.toMutableMap()
            data["private"] = privateKeyPem
            
            val success = vaultClient.writeSecret(secretPath, data.mapValues { it.value.toString() })
            if (success) {
                // Clear cache to force refresh
                cache.remove("private:$keyId")
                logger.info { "Private key stored in Vault: $secretPath" }
            }
            success
        } catch (e: Exception) {
            logger.error(e) { "Failed to store private key in Vault: $keyId" }
            false
        }
    }

    /**
     * Store public key in Vault.
     *
     * @param keyId Key identifier
     * @param publicKey Public key to store
     * @return true on success, false on failure
     */
    fun storePublicKey(keyId: String, publicKey: PublicKey): Boolean {
        return try {
            require(publicKey is java.security.interfaces.RSAPublicKey) { 
                "Public key must be RSA key, got ${publicKey.javaClass.name}" 
            }
            val secretPath = "${vaultConfig.secretPath}/$keyId"
            val publicKeyPem = JwtKeyManager.publicKeyToPem(publicKey)
            
            // Read existing secret to merge with new public key
            val existing = vaultClient.getSecret(secretPath) ?: emptyMap()
            val data = existing.toMutableMap()
            data["public"] = publicKeyPem
            
            val success = vaultClient.writeSecret(secretPath, data.mapValues { it.value.toString() })
            if (success) {
                // Clear cache to force refresh
                cache.remove("public:$keyId")
                logger.info { "Public key stored in Vault: $secretPath" }
            }
            success
        } catch (e: Exception) {
            logger.error(e) { "Failed to store public key in Vault: $keyId" }
            false
        }
    }

    /**
     * Store both private and public keys in Vault atomically.
     *
     * @param keyId Key identifier
     * @param privateKey Private key to store
     * @param publicKey Public key to store
     * @return true on success, false on failure
     */
    override     override fun storeKeyPair(keyId: String, privateKey: PrivateKey, publicKey: PublicKey): Boolean {
        return try {
            // Validate key types before casting
            require(privateKey is java.security.interfaces.RSAPrivateKey) { 
                "Private key must be RSA key, got ${privateKey.javaClass.name}" 
            }
            require(publicKey is java.security.interfaces.RSAPublicKey) { 
                "Public key must be RSA key, got ${publicKey.javaClass.name}" 
            }
            
            val secretPath = "${vaultConfig.secretPath}/$keyId"
            val privateKeyPem = JwtKeyManager.privateKeyToPem(privateKey)
            val publicKeyPem = JwtKeyManager.publicKeyToPem(publicKey)
            
            val data = mapOf(
                "private" to privateKeyPem,
                "public" to publicKeyPem,
            )
            
            val success = vaultClient.writeSecret(secretPath, data)
            if (success) {
                // Clear cache to force refresh
                cache.remove("private:$keyId")
                cache.remove("public:$keyId")
                logger.info { "Key pair stored in Vault: $secretPath" }
            }
            success
        } catch (e: Exception) {
            logger.error(e) { "Failed to store key pair in Vault: $keyId" }
            false
        }
    }

    /**
     * Clear cache (useful for testing or key rotation).
     */
    fun clearCache() {
        cache.clear()
        logger.debug { "Vault key cache cleared" }
    }
}

