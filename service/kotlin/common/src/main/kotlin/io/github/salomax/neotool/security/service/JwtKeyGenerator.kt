package io.github.salomax.neotool.security.service

import mu.KotlinLogging
import java.security.KeyPair

/**
 * Service for generating RSA key pairs for JWT signing.
 * Wraps JwtKeyManager functionality with service-level abstraction.
 */
object JwtKeyGenerator {
    private val logger = KotlinLogging.logger {}

    /**
     * Generate a new RSA key pair.
     *
     * @param keySize Key size in bits (default: 4096, minimum: 2048)
     * @return Generated KeyPair
     */
    fun generateRsaKeyPair(keySize: Int = 4096): KeyPair {
        require(keySize >= 2048) { "Key size must be at least 2048 bits" }
        
        logger.info { "Generating RSA key pair (${keySize} bits)..." }
        val keyPair = JwtKeyManager.generateKeyPair(keySize)
        logger.info { "RSA key pair generated successfully" }
        
        return keyPair
    }

    /**
     * Convert RSA key pair to PEM format strings.
     *
     * @param keyPair The key pair to convert
     * @return Pair of (privateKeyPem, publicKeyPem)
     */
    fun keyPairToPem(keyPair: KeyPair): Pair<String, String> {
        return JwtKeyManager.keyPairToPem(keyPair)
    }
}

