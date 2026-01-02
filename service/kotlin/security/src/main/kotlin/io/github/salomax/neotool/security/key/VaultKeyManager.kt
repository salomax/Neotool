package io.github.salomax.neotool.security.key

import io.github.salomax.neotool.common.security.key.JwtKeyManager
import io.github.salomax.neotool.common.security.key.KeyManager
import io.github.salomax.neotool.security.config.VaultConfig
import io.github.salomax.neotool.security.vault.VaultClient
import io.micronaut.context.annotation.Requires
import jakarta.inject.Singleton
import mu.KotlinLogging
import java.security.PrivateKey
import java.security.PublicKey
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
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
) : KeyManager {
    private val logger = KotlinLogging.logger {}

    private val cache = ConcurrentHashMap<String, CachedKey>()
    private val cacheLock = ReentrantLock()
    private val cacheTtlMillis = 5 * 60 * 1000L // 5 minutes

    /**
     * Cached key with expiration time.
     */
    private data class CachedKey(
        // PrivateKey, PublicKey, or String (for secret)
        val key: Any,
        val expiresAt: Long,
    ) {
        fun isExpired(): Boolean = System.currentTimeMillis() > expiresAt
    }

    override fun getPrivateKey(keyId: String): PrivateKey? {
        val cacheKey = "private:$keyId"
        return getCachedOrFetch(cacheKey) fetch@{
            val secretPath = "${vaultConfig.secretPath}/$keyId"
            val keyContent =
                vaultClient.getSecretAsString(secretPath, "private")
                    ?: return@fetch null

            JwtKeyManager.loadPrivateKey(null, keyContent)
        } as? PrivateKey
    }

    override fun getPublicKey(keyId: String): PublicKey? {
        val cacheKey = "public:$keyId"
        return getCachedOrFetch(cacheKey) fetch@{
            val secretPath = "${vaultConfig.secretPath}/$keyId"
            val keyContent =
                vaultClient.getSecretAsString(secretPath, "public")
                    ?: return@fetch null

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

    override fun isAvailable(): Boolean = vaultClient.isAvailable()

    /**
     * Get value from cache or fetch from Vault.
     * Thread-safe with double-checked locking pattern.
     */
    private fun <T> getCachedOrFetch(
        cacheKey: String,
        fetch: () -> T?,
    ): T? {
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
                    return@withLock it as? T
                }
                // If no cached value available, return null
                null
            }
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

