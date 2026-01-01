package io.github.salomax.neotool.security.vault

import io.github.salomax.neotool.security.config.VaultConfig
import io.micronaut.context.annotation.Requires
import jakarta.inject.Singleton
import mu.KotlinLogging

/**
 * Client wrapper for HashiCorp Vault.
 * Provides simplified interface for secret retrieval with error handling.
 * Only created when Vault is enabled.
 */
@Singleton
@Requires(property = "vault.enabled", value = "true")
class VaultClient(
    private val vaultConfig: VaultConfig,
) {
    private val logger = KotlinLogging.logger {}

    private val vaultDriver: com.bettercloud.vault.Vault by lazy {
        val config = com.bettercloud.vault.VaultConfig()
            .address(vaultConfig.address)
            .engineVersion(vaultConfig.engineVersion)
            .connectionTimeout(vaultConfig.connectionTimeout)
            .readTimeout(vaultConfig.readTimeout)

        if (vaultConfig.token != null) {
            config.token(vaultConfig.token)
        }

        try {
            com.bettercloud.vault.Vault(config)
        } catch (e: Exception) {
            logger.error(e) { "Failed to initialize Vault client: ${e.message}" }
            throw IllegalStateException("Failed to initialize Vault client", e)
        }
    }

    /**
     * Get secret from Vault at the specified path.
     * Handles KV v2 API format by unwrapping the 'data' key.
     *
     * @param path Secret path (e.g., "secret/jwt/keys/kid-1")
     * @return Map of key-value pairs from the secret, or null if not found
     */
    fun getSecret(path: String): Map<String, Any>? {
        return try {
            val response = vaultDriver.logical().read(path)
            val rawData = response?.data ?: return null
            
            // For KV v2, unwrap the 'data' key
            if (vaultConfig.engineVersion == 2) {
                @Suppress("UNCHECKED_CAST")
                (rawData["data"] as? Map<String, Any>) ?: rawData
            } else {
                rawData
            }
        } catch (e: com.bettercloud.vault.VaultException) {
            when (e.httpStatusCode) {
                404 -> {
                    logger.debug { "Secret not found in Vault: $path" }
                    null
                }
                403 -> {
                    logger.warn { "Access denied to Vault secret: $path" }
                    null
                }
                else -> {
                    logger.error(e) { "Failed to read secret from Vault: $path (HTTP ${e.httpStatusCode})" }
                    null
                }
            }
        } catch (e: Exception) {
            logger.error(e) { "Unexpected error reading secret from Vault: $path" }
            null
        }
    }

    /**
     * Get a specific value from a secret by key.
     *
     * @param path Secret path (e.g., "secret/jwt/keys/kid-1")
     * @param key Key within the secret (e.g., "private", "public")
     * @return Secret value as string, or null if not found
     */
    fun getSecretAsString(path: String, key: String): String? {
        val secret = getSecret(path) ?: return null
        return secret[key]?.toString()
    }

    /**
     * Check if Vault is available and accessible.
     * Performs a health check by attempting to read a test path.
     *
     * @return true if Vault is available, false otherwise
     */
    fun isAvailable(): Boolean {
        return try {
            // Try to read Vault sys/health endpoint (doesn't require auth)
            val response = vaultDriver.logical().read("sys/health")
            response != null
        } catch (e: Exception) {
            logger.debug(e) { "Vault health check failed: ${e.message}" }
            false
        }
    }

    /**
     * Write secret to Vault at the specified path.
     * Handles Vault KV v2 API format (automatically uses `data` prefix).
     *
     * @param path Secret path (e.g., "secret/jwt/keys/kid-1")
     * @param data Map of key-value pairs to store
     * @return true on success, false on failure
     */
    fun writeSecret(path: String, data: Map<String, String>): Boolean {
        return try {
            // For KV v2, the driver handles the 'data' prefix automatically based on engineVersion
            // We just use the path as-is, and the driver will handle KV v2 format
            val kvPath = path

            // For KV v2, wrap data in 'data' key if engine version is 2
            val writeData = if (vaultConfig.engineVersion == 2) {
                mapOf("data" to data)
            } else {
                data
            }

            vaultDriver.logical().write(kvPath, writeData)
            logger.debug { "Successfully wrote secret to Vault: $path" }
            true
        } catch (e: com.bettercloud.vault.VaultException) {
            when (e.httpStatusCode) {
                403 -> {
                    logger.warn { "Access denied writing to Vault secret: $path" }
                    false
                }
                404 -> {
                    logger.warn { "Secret path not found in Vault: $path" }
                    false
                }
                409 -> {
                    logger.debug { "Secret already exists in Vault (CAS conflict): $path" }
                    false
                }
                else -> {
                    logger.error(e) { "Failed to write secret to Vault: $path (HTTP ${e.httpStatusCode})" }
                    false
                }
            }
        } catch (e: Exception) {
            logger.error(e) { "Unexpected error writing secret to Vault: $path" }
            false
        }
    }

    /**
     * Vault lock information.
     */
    data class VaultLock(
        val lockPath: String,
        val lockValue: String,
        val ttlSeconds: Int,
    )

    /**
     * Acquire a distributed lock in Vault.
     * Uses Vault KV with check-and-set (CAS) versioning for atomic lock acquisition.
     * 
     * Note: This implementation uses a check-then-write pattern which has a race condition.
     * For production, consider using Vault's native locking API or implementing proper CAS.
     *
     * @param lockPath Lock path (e.g., "secret/locks/jwt-key-init/kid-1")
     * @param ttlSeconds Lock TTL in seconds (default: 60)
     * @return VaultLock if acquired, null if lock already held
     */
    fun acquireLock(lockPath: String, ttlSeconds: Int = 60): VaultLock? {
        return try {
            // Generate unique lock value (pod identifier)
            val lockValue = "${System.currentTimeMillis()}-${java.util.UUID.randomUUID()}"

            val lockData = mapOf(
                "lock_value" to lockValue,
                "acquired_at" to System.currentTimeMillis().toString(),
                "ttl_seconds" to ttlSeconds.toString(),
            )

            // Try to read first to check if lock exists
            // Note: This is not atomic - race condition exists between read and write
            // For production, use Vault's CAS API or implement proper atomic operation
            val existing = vaultDriver.logical().read(lockPath)
            if (existing != null && existing.data != null) {
                logger.debug { "Lock already held: $lockPath" }
                return null
            }

            // Write lock (KV v2 requires wrapping in 'data' for write)
            val writeData = if (vaultConfig.engineVersion == 2) {
                mapOf("data" to (lockData as Map<String, Any>))
            } else {
                lockData as Map<String, Any>
            }

            vaultDriver.logical().write(lockPath, writeData)
            logger.debug { "Lock acquired: $lockPath (value: $lockValue)" }
            VaultLock(lockPath, lockValue, ttlSeconds)
        } catch (e: com.bettercloud.vault.VaultException) {
            when (e.httpStatusCode) {
                403 -> {
                    logger.warn { "Access denied acquiring Vault lock: $lockPath" }
                    null
                }
                409 -> {
                    logger.debug { "Lock already held (CAS conflict): $lockPath" }
                    null
                }
                else -> {
                    logger.error(e) { "Failed to acquire lock in Vault: $lockPath (HTTP ${e.httpStatusCode})" }
                    null
                }
            }
        } catch (e: Exception) {
            logger.error(e) { "Unexpected error acquiring lock in Vault: $lockPath" }
            null
        }
    }

    /**
     * Release a distributed lock in Vault.
     *
     * @param lock The lock to release
     * @return true on success, false on failure
     */
    fun releaseLock(lock: VaultLock): Boolean {
        return try {
            // Verify we own the lock before releasing
            val existing = vaultDriver.logical().read(lock.lockPath)
            if (existing?.data == null) {
                logger.debug { "Lock already released: ${lock.lockPath}" }
                return true
            }

            // Unwrap KV v2 data if needed
            val lockData = if (vaultConfig.engineVersion == 2) {
                @Suppress("UNCHECKED_CAST")
                (existing.data["data"] as? Map<String, Any>) ?: existing.data
            } else {
                existing.data
            }

            val existingValue = lockData["lock_value"]?.toString()
            if (existingValue != lock.lockValue) {
                logger.warn { "Lock value mismatch - cannot release lock owned by another process: ${lock.lockPath}" }
                return false
            }

            // Delete the lock
            vaultDriver.logical().delete(lock.lockPath)
            logger.debug { "Lock released: ${lock.lockPath}" }
            true
        } catch (e: com.bettercloud.vault.VaultException) {
            when (e.httpStatusCode) {
                404 -> {
                    logger.debug { "Lock already released (not found): ${lock.lockPath}" }
                    true
                }
                else -> {
                    logger.error(e) { "Failed to release lock in Vault: ${lock.lockPath} (HTTP ${e.httpStatusCode})" }
                    false
                }
            }
        } catch (e: Exception) {
            logger.error(e) { "Unexpected error releasing lock in Vault: ${lock.lockPath}" }
            false
        }
    }
}

