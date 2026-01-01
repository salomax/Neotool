package io.github.salomax.neotool.security.config

import io.micronaut.context.annotation.ConfigurationProperties
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank

/**
 * Configuration properties for HashiCorp Vault integration.
 *
 * Properties can be set via environment variables or application.yml:
 * - vault.enabled: Enable Vault integration (default: false)
 * - vault.address: Vault server URL (default: http://localhost:8200)
 * - vault.token: Vault authentication token
 * - vault.secret-path: Path prefix for secrets (default: secret/jwt/keys)
 * - vault.engine-version: KV secrets engine version (default: 2)
 * - vault.connection-timeout: Connection timeout in milliseconds (default: 5000)
 * - vault.read-timeout: Read timeout in milliseconds (default: 5000)
 */
@ConfigurationProperties("vault")
data class VaultConfig(
    /**
     * Enable Vault integration.
     * When false, falls back to file-based key management.
     * Default: false (for development)
     * Can be set via VAULT_ENABLED environment variable.
     */
    val enabled: Boolean = System.getenv("VAULT_ENABLED")?.toBoolean() ?: false,

    /**
     * Vault server address.
     * Default: http://localhost:8200
     * Can be set via VAULT_ADDRESS environment variable.
     */
    @get:NotBlank
    val address: String = System.getenv("VAULT_ADDRESS") ?: "http://localhost:8200",

    /**
     * Vault authentication token.
     * In production, should be provided via environment variable or service account.
     * Can be set via VAULT_TOKEN environment variable.
     */
    val token: String? = System.getenv("VAULT_TOKEN"),

    /**
     * Secret path prefix in Vault.
     * Keys will be stored at: {secret-path}/{keyId}/private, {secret-path}/{keyId}/public
     * Default: secret/jwt/keys
     * Can be set via VAULT_SECRET_PATH environment variable.
     */
    @get:NotBlank
    val secretPath: String = System.getenv("VAULT_SECRET_PATH") ?: "secret/jwt/keys",

    /**
     * KV secrets engine version.
     * Version 2 is recommended for better performance and features.
     * Default: 2
     * Can be set via VAULT_ENGINE_VERSION environment variable.
     */
    @get:Min(1)
    val engineVersion: Int = System.getenv("VAULT_ENGINE_VERSION")?.toIntOrNull() ?: 2,

    /**
     * Connection timeout in milliseconds.
     * Default: 5000ms (5 seconds)
     * Can be set via VAULT_CONNECTION_TIMEOUT environment variable.
     */
    @get:Min(1000)
    val connectionTimeout: Int = System.getenv("VAULT_CONNECTION_TIMEOUT")?.toIntOrNull() ?: 5000,

    /**
     * Read timeout in milliseconds.
     * Default: 5000ms (5 seconds)
     * Can be set via VAULT_READ_TIMEOUT environment variable.
     */
    @get:Min(1000)
    val readTimeout: Int = System.getenv("VAULT_READ_TIMEOUT")?.toIntOrNull() ?: 5000,
) {
    /**
     * Check if Vault is configured and ready to use.
     */
    fun isConfigured(): Boolean {
        return enabled && token != null && token.isNotBlank()
    }
}

