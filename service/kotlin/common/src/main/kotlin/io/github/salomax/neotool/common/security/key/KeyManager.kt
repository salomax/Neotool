package io.github.salomax.neotool.common.security.key

import java.security.PrivateKey
import java.security.PublicKey

/**
 * Interface for managing cryptographic keys used for JWT signing and validation.
 * Supports multiple backends (Vault, file system, environment variables).
 *
 * Implementations should handle:
 * - Key retrieval from various sources
 * - Caching for performance
 * - Error handling and fallback mechanisms
 */
interface KeyManager {
    /**
     * Get RSA private key by key ID.
     * Used for signing JWT tokens.
     *
     * @param keyId Key identifier (e.g., "kid-1")
     * @return PrivateKey if found, null otherwise
     */
    fun getPrivateKey(keyId: String): PrivateKey?

    /**
     * Get RSA public key by key ID.
     * Used for validating JWT tokens.
     *
     * @param keyId Key identifier (e.g., "kid-1")
     * @return PublicKey if found, null otherwise
     */
    fun getPublicKey(keyId: String): PublicKey?

    /**
     * Get symmetric secret by key ID.
     * Used for HS256 algorithm.
     *
     * @param keyId Key identifier (e.g., "kid-1")
     * @return Secret string if found, null otherwise
     */
    fun getSecret(keyId: String): String?

    /**
     * Check if this key manager is available and ready to use.
     * Used for health checks and fallback selection.
     *
     * @return true if available, false otherwise
     */
    fun isAvailable(): Boolean
}
