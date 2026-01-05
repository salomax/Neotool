package io.github.salomax.neotool.common.security.key

import jakarta.inject.Singleton
import mu.KotlinLogging

/**
 * Factory for creating and selecting the appropriate KeyManager implementation.
 *
 * This common implementation only provides FileKeyManager.
 * For Vault support, use SecurityKeyManagerFactory in the security module.
 */
@Singleton
class KeyManagerFactory(
    private val fileKeyManager: FileKeyManager,
) {
    private val logger = KotlinLogging.logger {}

    /**
     * Get the appropriate key manager based on configuration and availability.
     *
     * @return KeyManager instance (FileKeyManager for common module)
     */
    fun getKeyManager(): KeyManager {
        logger.debug { "Using file-based key manager" }
        return fileKeyManager
    }
}
