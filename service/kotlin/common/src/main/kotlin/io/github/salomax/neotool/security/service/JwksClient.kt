package io.github.salomax.neotool.security.service

import io.micronaut.http.HttpRequest
import io.micronaut.http.client.HttpClient
import io.micronaut.http.uri.UriBuilder
import jakarta.inject.Singleton
import mu.KotlinLogging
import java.math.BigInteger
import java.net.URL
import java.security.KeyFactory
import java.security.PublicKey
import java.security.interfaces.RSAPublicKey
import java.security.spec.RSAPublicKeySpec
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Client for fetching and caching public keys from JWKS endpoint.
 * Thread-safe with in-memory caching and TTL support.
 */
@Singleton
class JwksClient(
    private val httpClient: HttpClient,
) {
    private val logger = KotlinLogging.logger {}

    private val keyFactory = KeyFactory.getInstance("RSA")
    private val keyCache = ConcurrentHashMap<String, CachedKey>()
    private val cacheLock = ReentrantLock()

    /**
     * Cached key with expiration time.
     */
    private data class CachedKey(
        val publicKey: PublicKey,
        val expiresAt: Long,
    ) {
        fun isExpired(): Boolean = System.currentTimeMillis() > expiresAt
    }

    /**
     * JWKS response DTO.
     */
    private data class JwksResponse(
        val keys: List<Jwk>
    )

    /**
     * JSON Web Key (JWK) DTO.
     */
    private data class Jwk(
        val kty: String,
        val kid: String?,
        val use: String?,
        val alg: String?,
        val n: String,
        val e: String,
    )

    /**
     * Get public key by key ID from JWKS endpoint.
     *
     * @param jwksUrl URL to JWKS endpoint
     * @param keyId Key ID to fetch (optional, uses first key if not provided)
     * @param cacheTtlSeconds Cache TTL in seconds (default: 3600 = 1 hour)
     * @return PublicKey if found, null otherwise
     */
    fun getPublicKey(
        jwksUrl: String,
        keyId: String? = null,
        cacheTtlSeconds: Long = 3600,
    ): PublicKey? {
        val cacheKey = "$jwksUrl:$keyId"

        // Check cache first
        val cached = keyCache[cacheKey]
        if (cached != null && !cached.isExpired()) {
            logger.debug { "Using cached public key for kid: $keyId" }
            return cached.publicKey
        }

        // Fetch from JWKS endpoint
        return cacheLock.withLock {
            // Double-check after acquiring lock
            val cachedAfterLock = keyCache[cacheKey]
            if (cachedAfterLock != null && !cachedAfterLock.isExpired()) {
                return cachedAfterLock.publicKey
            }

            try {
                val jwks = fetchJwks(jwksUrl)
                val jwk = if (keyId != null) {
                    jwks.keys.firstOrNull { it.kid == keyId }
                } else {
                    jwks.keys.firstOrNull()
                }

                if (jwk == null) {
                    logger.warn { "Key not found in JWKS: kid=$keyId, url=$jwksUrl" }
                    return null
                }

                val publicKey = jwkToPublicKey(jwk)
                val expiresAt = System.currentTimeMillis() + (cacheTtlSeconds * 1000)
                keyCache[cacheKey] = CachedKey(publicKey, expiresAt)

                logger.debug { "Fetched and cached public key for kid: ${jwk.kid}" }
                return publicKey
            } catch (e: Exception) {
                logger.error(e) { "Failed to fetch public key from JWKS: $jwksUrl" }
                // Return cached key even if expired as fallback
                return cached?.publicKey
            }
        }
    }

    /**
     * Fetch JWKS from endpoint.
     */
    private fun fetchJwks(jwksUrl: String): JwksResponse {
        val request = HttpRequest.GET<Any>(jwksUrl)
        val response = httpClient.toBlocking().retrieve(request, JwksResponse::class.java)
        return response
    }

    /**
     * Convert JWK to PublicKey.
     */
    private fun jwkToPublicKey(jwk: Jwk): RSAPublicKey {
        require(jwk.kty == "RSA") { "Only RSA keys are supported, got: ${jwk.kty}" }

        // Decode base64url to BigInteger
        val modulus = BigInteger(1, base64UrlDecode(jwk.n))
        val exponent = BigInteger(1, base64UrlDecode(jwk.e))

        val keySpec = RSAPublicKeySpec(modulus, exponent)
        return keyFactory.generatePublic(keySpec) as RSAPublicKey
    }

    /**
     * Decode base64url string to bytes.
     */
    private fun base64UrlDecode(encoded: String): ByteArray {
        return Base64.getUrlDecoder().decode(encoded)
    }

    /**
     * Clear cache (useful for testing or key rotation).
     */
    fun clearCache() {
        keyCache.clear()
        logger.debug { "JWKS cache cleared" }
    }

    /**
     * Get cache size (for monitoring).
     */
    fun getCacheSize(): Int = keyCache.size
}

