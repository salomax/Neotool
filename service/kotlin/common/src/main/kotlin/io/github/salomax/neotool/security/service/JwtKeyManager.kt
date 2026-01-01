package io.github.salomax.neotool.security.service

import mu.KotlinLogging
import java.io.File
import java.nio.charset.StandardCharsets
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.PublicKey
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

/**
 * Utility class for managing RSA keys for JWT signing.
 * Handles loading keys from files or inline strings, generating key pairs, and converting to PEM format.
 */
object JwtKeyManager {
    private val logger = KotlinLogging.logger {}
    private val keyFactory = KeyFactory.getInstance("RSA")

    /**
     * Load RSA private key from file path or inline string.
     *
     * @param path Path to private key file (PEM format), or null if using inline key
     * @param inlineKey Inline private key (PEM or base64-encoded), or null if using file path
     * @return PrivateKey if successfully loaded, null otherwise
     */
    fun loadPrivateKey(path: String?, inlineKey: String?): PrivateKey? {
        return try {
            val keyContent = when {
                inlineKey != null -> inlineKey
                path != null -> {
                    val file = File(path)
                    if (!file.exists()) {
                        logger.warn { "Private key file not found: $path" }
                        return null
                    }
                    file.readText(StandardCharsets.UTF_8)
                }
                else -> return null
            }

            parsePrivateKey(keyContent)
        } catch (e: Exception) {
            logger.error(e) { "Failed to load private key: ${e.message}" }
            null
        }
    }

    /**
     * Load RSA public key from file path or inline string.
     *
     * @param path Path to public key file (PEM format), or null if using inline key
     * @param inlineKey Inline public key (PEM or base64-encoded), or null if using file path
     * @return PublicKey if successfully loaded, null otherwise
     */
    fun loadPublicKey(path: String?, inlineKey: String?): PublicKey? {
        return try {
            val keyContent = when {
                inlineKey != null -> inlineKey
                path != null -> {
                    val file = File(path)
                    if (!file.exists()) {
                        logger.warn { "Public key file not found: $path" }
                        return null
                    }
                    file.readText(StandardCharsets.UTF_8)
                }
                else -> return null
            }

            parsePublicKey(keyContent)
        } catch (e: Exception) {
            logger.error(e) { "Failed to load public key: ${e.message}" }
            null
        }
    }

    /**
     * Generate a new RSA key pair.
     *
     * @param keySize Key size in bits (default: 4096, minimum: 2048)
     * @return Generated KeyPair
     */
    fun generateKeyPair(keySize: Int = 4096): KeyPair {
        require(keySize >= 2048) { "Key size must be at least 2048 bits" }
        val keyPairGenerator = KeyPairGenerator.getInstance("RSA")
        keyPairGenerator.initialize(keySize)
        return keyPairGenerator.generateKeyPair()
    }

    /**
     * Convert RSA key pair to PEM format strings.
     *
     * @param keyPair The key pair to convert
     * @return Pair of (privateKeyPem, publicKeyPem)
     */
    fun keyPairToPem(keyPair: KeyPair): Pair<String, String> {
        val privateKeyPem = privateKeyToPem(keyPair.private as RSAPrivateKey)
        val publicKeyPem = publicKeyToPem(keyPair.public as RSAPublicKey)
        return Pair(privateKeyPem, publicKeyPem)
    }

    /**
     * Convert RSA private key to PEM format.
     */
    fun privateKeyToPem(privateKey: RSAPrivateKey): String {
        val keySpec = PKCS8EncodedKeySpec(privateKey.encoded)
        val encoded = Base64.getEncoder().encodeToString(keySpec.encoded)
        return buildString {
            appendLine("-----BEGIN PRIVATE KEY-----")
            encoded.chunked(64).forEach { chunk ->
                appendLine(chunk)
            }
            appendLine("-----END PRIVATE KEY-----")
        }
    }

    /**
     * Convert RSA public key to PEM format.
     */
    fun publicKeyToPem(publicKey: RSAPublicKey): String {
        val keySpec = X509EncodedKeySpec(publicKey.encoded)
        val encoded = Base64.getEncoder().encodeToString(keySpec.encoded)
        return buildString {
            appendLine("-----BEGIN PUBLIC KEY-----")
            encoded.chunked(64).forEach { chunk ->
                appendLine(chunk)
            }
            appendLine("-----END PUBLIC KEY-----")
        }
    }

    /**
     * Parse private key from PEM or base64-encoded string.
     */
    private fun parsePrivateKey(keyContent: String): PrivateKey {
        val cleanedKey = cleanKeyContent(keyContent, "PRIVATE KEY")
        val keyBytes = Base64.getDecoder().decode(cleanedKey)
        val keySpec = PKCS8EncodedKeySpec(keyBytes)
        return keyFactory.generatePrivate(keySpec)
    }

    /**
     * Parse public key from PEM or base64-encoded string.
     */
    private fun parsePublicKey(keyContent: String): PublicKey {
        val cleanedKey = cleanKeyContent(keyContent, "PUBLIC KEY")
        val keyBytes = Base64.getDecoder().decode(cleanedKey)
        val keySpec = X509EncodedKeySpec(keyBytes)
        return keyFactory.generatePublic(keySpec)
    }

    /**
     * Clean key content by removing PEM headers/footers and whitespace.
     */
    private fun cleanKeyContent(keyContent: String, keyType: String): String {
        return keyContent
            .replace("-----BEGIN $keyType-----", "")
            .replace("-----END $keyType-----", "")
            .replace("-----BEGIN RSA $keyType-----", "")
            .replace("-----END RSA $keyType-----", "")
            .replace(Regex("\\s"), "")
    }
}

