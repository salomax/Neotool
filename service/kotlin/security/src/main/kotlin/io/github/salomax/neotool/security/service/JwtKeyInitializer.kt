package io.github.salomax.neotool.security.service

import io.github.salomax.neotool.security.config.JwtConfig
import io.github.salomax.neotool.security.config.VaultConfig
import io.github.salomax.neotool.security.vault.VaultClient
import io.micronaut.context.event.StartupEvent
import io.micronaut.runtime.event.annotation.EventListener
import jakarta.inject.Singleton
import mu.KotlinLogging
import java.security.KeyPair
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Initializes JWT keys on first startup if they don't exist.
 * Only runs in security module (issuer).
 * Uses distributed locking to prevent race conditions when multiple pods start simultaneously.
 */
@Singleton
class JwtKeyInitializer(
    private val jwtConfig: JwtConfig,
    private val keyManagerFactory: KeyManagerFactory,
    private val vaultConfig: VaultConfig,
    private val vaultClient: VaultClient? = null, // Optional - only available when Vault enabled
) {
    private val logger = KotlinLogging.logger {}
    private val fileLock = ReentrantLock() // For file-based locking when Vault disabled

    @EventListener
    fun onStartup(event: StartupEvent) {
        if (!jwtConfig.autoGenerateKeys) {
            logger.debug { "Auto-generation of keys is disabled" }
            return
        }

        val keyId = jwtConfig.keyId ?: "kid-1"
        logger.info { "Checking if JWT keys exist for keyId: $keyId" }

        // Cache KeyManager instance to avoid multiple factory calls
        val keyManager = keyManagerFactory.getKeyManager()
        val existingPrivateKey = keyManager.getPrivateKey(keyId)

        if (existingPrivateKey != null) {
            logger.info { "JWT keys already exist for keyId: $keyId, skipping generation" }
            return
        }

        logger.info { "JWT keys not found for keyId: $keyId, initializing keys..." }

        // Initialize keys with distributed locking
        try {
            if (vaultConfig.enabled && vaultClient != null) {
                initializeKeysWithVaultLock(keyId, keyManager)
            } else {
                initializeKeysWithFileLock(keyId, keyManager)
            }
        } catch (e: Exception) {
            logger.error(e) { "Failed to initialize JWT keys: ${e.message}" }
            // Don't block startup - keys may be provisioned via Vault or file system
        }
    }

    /**
     * Initialize keys using Vault distributed locking.
     */
    private fun initializeKeysWithVaultLock(keyId: String, keyManager: KeyManager) {
        val lockPath = "secret/locks/jwt-key-init/$keyId"
        var lock: VaultClient.VaultLock? = null

        try {
            // Try to acquire lock with retries
            lock = acquireLockWithRetry(lockPath)
            if (lock == null) {
                logger.warn { "Could not acquire lock after retries, checking if keys were created by another pod" }
                // Re-check if keys exist (another pod may have created them)
                if (keyManager.getPrivateKey(keyId) != null) {
                    logger.info { "Keys were created by another pod, skipping generation" }
                    return
                }
                logger.error { "Could not acquire lock and keys don't exist - keys may need manual provisioning" }
                return
            }

            // Double-check keys don't exist after acquiring lock
            if (keyManager.getPrivateKey(keyId) != null) {
                logger.info { "Keys exist after acquiring lock (created by another pod), skipping generation" }
                return
            }

            // Generate and store keys
            generateAndStoreKeys(keyId, keyManager)

        } finally {
            // Always release lock
            lock?.let {
                val released = vaultClient?.releaseLock(it) ?: false
                if (!released) {
                    logger.warn { "Failed to release lock: $lockPath (will expire after TTL)" }
                }
            }
        }
    }

    /**
     * Initialize keys using file-based locking (when Vault disabled).
     */
    private fun initializeKeysWithFileLock(keyId: String, keyManager: KeyManager) {
        fileLock.withLock {
            // Double-check keys don't exist after acquiring lock
            if (keyManager.getPrivateKey(keyId) != null) {
                logger.info { "Keys exist after acquiring file lock, skipping generation" }
                return
            }

            // Generate and store keys
            generateAndStoreKeys(keyId, keyManager)
        }
    }

    /**
     * Acquire lock with retry logic.
     */
    private fun acquireLockWithRetry(lockPath: String): VaultClient.VaultLock? {
        var attempt = 0
        while (attempt < jwtConfig.lockRetryAttempts) {
            val lock = vaultClient?.acquireLock(lockPath, jwtConfig.lockTtlSeconds)
            if (lock != null) {
                return lock
            }

            attempt++
            if (attempt < jwtConfig.lockRetryAttempts) {
                logger.debug { "Lock acquisition failed, retrying in ${jwtConfig.lockRetryDelaySeconds}s (attempt $attempt/${jwtConfig.lockRetryAttempts})" }
                Thread.sleep(jwtConfig.lockRetryDelaySeconds * 1000L)
            }
        }
        return null
    }

    /**
     * Generate and store keys.
     * Uses WritableKeyManager interface for abstraction (follows Open/Closed Principle).
     */
    private fun generateAndStoreKeys(keyId: String, keyManager: KeyManager) {
        logger.info { "Generating RSA key pair (${jwtConfig.keySize} bits) for keyId: $keyId" }
        val keyPair = JwtKeyGenerator.generateRsaKeyPair(jwtConfig.keySize)

        val success = if (keyManager is WritableKeyManager) {
            keyManager.storeKeyPair(keyId, keyPair.private, keyPair.public)
        } else {
            logger.error { "KeyManager does not support writing keys: ${keyManager.javaClass.name}" }
            false
        }

        if (success) {
            logger.info { "JWT keys successfully initialized for keyId: $keyId" }
        } else {
            logger.error { "Failed to store JWT keys for keyId: $keyId" }
        }
    }
}

