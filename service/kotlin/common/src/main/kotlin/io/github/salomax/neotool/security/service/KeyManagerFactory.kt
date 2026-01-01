package io.github.salomax.neotool.security.service

import io.github.salomax.neotool.security.config.VaultConfig
import io.github.salomax.neotool.security.vault.VaultClient
import jakarta.inject.Singleton
import mu.KotlinLogging

/**
 * Factory for creating and selecting the appropriate KeyManager implementation.
 * Selects VaultKeyManager if Vault is enabled and available, otherwise falls back to FileKeyManager.
 */
@Singleton
class KeyManagerFactory(
    private val vaultConfig: VaultConfig,
    private val fileKeyManager: FileKeyManager,
    private val vaultClient: VaultClient? = null, // Optional - only available when Vault enabled
    private val vaultKeyManager: VaultKeyManager? = null, // Optional - only available when Vault enabled
) {
    private val logger = KotlinLogging.logger {}

    /**
     * Get the appropriate key manager based on configuration and availability.
     *
     * @return KeyManager instance (VaultKeyManager or FileKeyManager)
     */
    fun getKeyManager(): KeyManager {
        // Check if Vault is enabled and configured
        if (!vaultConfig.isConfigured()) {
            logger.info { "Vault is not configured, using file-based key manager" }
            return fileKeyManager
        }

        // Check if Vault client is available
        if (vaultClient == null || vaultKeyManager == null) {
            logger.warn { "Vault is configured but client is not available, falling back to file-based key manager" }
            return fileKeyManager
        }

        // Check if Vault is accessible
        if (!vaultClient.isAvailable()) {
            logger.warn { "Vault is configured but not available, falling back to file-based key manager" }
            return fileKeyManager
        }

        logger.info { "Using Vault-based key manager" }
        return vaultKeyManager
    }
}

