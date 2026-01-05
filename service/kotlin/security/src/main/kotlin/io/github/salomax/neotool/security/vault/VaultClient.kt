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

    /**
     * Normalize Vault address to ensure it has a proper protocol and format.
     * Handles cases where only port number or host:port is provided.
     */
    private fun normalizeVaultAddress(address: String): String {
        val trimmed = address.trim()

        // If already a full URL, return as-is
        if (
            trimmed.startsWith("http://", ignoreCase = true) ||
            trimmed.startsWith("https://", ignoreCase = true)
        ) {
            return trimmed
        }

        // If it's just a port number, assume localhost
        if (trimmed.matches(Regex("^\\d+$"))) {
            logger.warn { "Vault address is just a port number ($trimmed), assuming http://localhost:$trimmed" }
            return "http://localhost:$trimmed"
        }

        // If it's host:port format, add http://
        if (trimmed.matches(Regex("^[^:]+:\\d+$"))) {
            logger.warn { "Vault address missing protocol ($trimmed), assuming http://$trimmed" }
            return "http://$trimmed"
        }

        // If it's just a hostname, add http:// and default port
        if (!trimmed.contains("://") && !trimmed.contains(":")) {
            logger.warn { "Vault address missing protocol and port ($trimmed), assuming http://$trimmed:8200" }
            return "http://$trimmed:8200"
        }

        // Return as-is if we can't determine format (let library handle error)
        return trimmed
    }

    private val vaultDriver: com.bettercloud.vault.Vault by lazy {
        val openTimeoutSeconds = vaultConfig.connectionTimeout / 1000
        val readTimeoutSeconds = vaultConfig.readTimeout / 1000

        // Normalize and validate Vault address
        val normalizedAddress = normalizeVaultAddress(vaultConfig.address)

        val config =
            com.bettercloud.vault
                .VaultConfig()
                .address(normalizedAddress)
                .engineVersion(vaultConfig.engineVersion)
                .openTimeout(openTimeoutSeconds)
                .readTimeout(readTimeoutSeconds)

        // Configure SSL - required even for HTTP connections
        // For HTTP (non-HTTPS) addresses, disable SSL verification
        val isHttps = normalizedAddress.startsWith("https://", ignoreCase = true)
        if (isHttps) {
            // For HTTPS, use default SSL config (verify certificates)
            config.sslConfig(
                com.bettercloud.vault.SslConfig()
                    .verify(true),
            )
        } else {
            // For HTTP, disable SSL verification (not applicable but required by library)
            config.sslConfig(
                com.bettercloud.vault.SslConfig()
                    .verify(false),
            )
        }

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
    fun getSecretAsString(
        path: String,
        key: String,
    ): String? {
        val secret = getSecret(path) ?: return null
        return secret[key]?.toString()
    }

    /**
     * Check if Vault is available and accessible.
     * Performs a health check by attempting to read a test path.
     *
     * @return true if Vault is available, false otherwise
     */
    fun isAvailable(): Boolean =
        try {
            // Try to read Vault sys/health endpoint (doesn't require authentication)
            val response = vaultDriver.logical().read("sys/health")
            response != null
        } catch (e: Exception) {
            logger.debug(e) { "Vault health check failed: ${e.message}" }
            false
        }

    /**
     * Write secret to Vault at the specified path.
     * Handles Vault KV v2 API format (automatically uses `data` prefix).
     *
     * @param path Secret path (e.g., "secret/jwt/keys/kid-1")
     * @param data Map of key-value pairs to store
     * @return true on success, false on failure
     */
    fun writeSecret(
        path: String,
        data: Map<String, String>,
    ): Boolean =
        try {
            // For KV v2, the driver handles the 'data' prefix automatically based on engineVersion
            // We just use the path as-is, and the driver will handle KV v2 format
            val kvPath = path

            // For KV v2, wrap data in 'data' key if engine version is 2
            val writeData =
                if (vaultConfig.engineVersion == 2) {
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
