package io.github.salomax.neotool.security.key

import io.github.salomax.neotool.common.security.config.JwtConfig
import io.github.salomax.neotool.common.security.key.FileKeyManager
import io.github.salomax.neotool.common.security.key.JwtKeyManager
import io.github.salomax.neotool.security.config.VaultConfig
import io.github.salomax.neotool.security.vault.VaultClient
import io.micronaut.context.annotation.Context
import io.micronaut.context.annotation.Requires
import io.micronaut.context.event.StartupEvent
import io.micronaut.runtime.event.annotation.EventListener
import mu.KotlinLogging

/**
 * Propagates keys from Vault to FileKeyManager at startup.
 * This bridges the gap between Vault storage and the common JwtTokenValidator.
 */
@Context
@Requires(property = "vault.enabled", value = "true")
class VaultKeyPropagator(
    private val vaultClient: VaultClient,
    private val vaultConfig: VaultConfig,
    private val fileKeyManager: FileKeyManager,
    private val jwtConfig: JwtConfig,
) {
    private val logger = KotlinLogging.logger {}

    @EventListener
    fun onStartup(event: StartupEvent) {
        logger.info { "Checking for Vault keys to propagate..." }
        if (!vaultClient.isAvailable()) {
            logger.warn { "Vault is not available, skipping key propagation" }
            return
        }

        val keyId = jwtConfig.keyId ?: "kid-1"
        val secretPath = "${vaultConfig.secretPath}/$keyId"

        try {
            val keyContent = vaultClient.getSecretAsString(secretPath, "public")
            if (keyContent != null) {
                val publicKey = JwtKeyManager.loadPublicKey(null, keyContent)
                if (publicKey != null) {
                    fileKeyManager.setPublicKey(publicKey)
                    logger.info { "Successfully propagated public key for $keyId from Vault to FileKeyManager" }
                } else {
                    logger.error { "Failed to parse public key from Vault for $keyId" }
                }
            } else {
                logger.warn { "Public key not found in Vault at $secretPath" }
            }
        } catch (e: Exception) {
            logger.error(e) { "Error propagating key from Vault" }
        }
    }
}
