package io.github.salomax.neotool.common.test.security

import io.jsonwebtoken.Jwts
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.PublicKey
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.time.Instant
import java.util.Base64
import java.util.Date
import java.util.UUID

/**
 * Test fixture for JWT token creation and RSA key management.
 * Provides shared RSA key pairs and token creation utilities for JWT-related tests.
 *
 * Usage:
 * ```kotlin
 * val token = JwtTestFixture.createToken(
 *     subject = userId.toString(),
 *     type = "access",
 *     permissions = listOf("user:read", "user:write")
 * )
 * ```
 */
object JwtTestFixture {
    /**
     * Lazily initialized 2048-bit RSA key pair for testing.
     * Shared across all tests for consistency and performance.
     */
    private val keyPair: KeyPair by lazy {
        KeyPairGenerator.getInstance("RSA").apply {
            initialize(2048)
        }.generateKeyPair()
    }

    /**
     * Get the test RSA public key.
     */
    val publicKey: PublicKey
        get() = keyPair.public

    /**
     * Get the test RSA private key.
     */
    val privateKey: PrivateKey
        get() = keyPair.private

    /**
     * Create a signed JWT token for testing.
     *
     * @param subject Token subject (typically user ID or service ID)
     * @param type Token type ("access", "refresh", "service")
     * @param permissions List of permission strings to include in token
     * @param userPermissions User permissions for service tokens with user context
     * @param userId User ID for service tokens with user context
     * @param email User email to include in token
     * @param audience Token audience
     * @param expirationSeconds Token expiration in seconds from now (default: 900 = 15 minutes)
     * @param issuedAt Token issued at time (default: now)
     * @param customClaims Additional custom claims to include
     * @return Signed JWT token as compact string
     */
    fun createToken(
        subject: String = UUID.randomUUID().toString(),
        type: String = "access",
        permissions: List<String>? = null,
        userPermissions: List<String>? = null,
        userId: UUID? = null,
        email: String? = null,
        audience: String? = null,
        expirationSeconds: Long = 900,
        issuedAt: Instant = Instant.now(),
        customClaims: Map<String, Any> = emptyMap(),
    ): String {
        val builder =
            Jwts
                .builder()
                .subject(subject)
                .claim("type", type)
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(issuedAt.plusSeconds(expirationSeconds)))

        // Add optional claims
        permissions?.let { builder.claim("permissions", it) }
        userPermissions?.let { builder.claim("user_permissions", it) }
        userId?.let { builder.claim("user_id", it.toString()) }
        email?.let { builder.claim("email", it) }
        audience?.let { builder.audience().add(it) }

        // Add custom claims
        customClaims.forEach { (key, value) ->
            builder.claim(key, value)
        }

        return builder.signWith(privateKey as RSAPrivateKey).compact()
    }

    /**
     * Create an expired JWT token for testing expiration handling.
     *
     * @param expirationSecondsAgo How many seconds ago the token expired
     * @return Expired JWT token
     */
    fun createExpiredToken(
        subject: String = UUID.randomUUID().toString(),
        type: String = "access",
        expirationSecondsAgo: Long = 3600,
    ): String {
        val now = Instant.now()
        val issuedAt = now.minusSeconds(expirationSecondsAgo + 900) // Issued before expiration
        val expiration = now.minusSeconds(expirationSecondsAgo) // Expired in the past

        return Jwts
            .builder()
            .subject(subject)
            .claim("type", type)
            .issuedAt(Date.from(issuedAt))
            .expiration(Date.from(expiration))
            .signWith(privateKey as RSAPrivateKey)
            .compact()
    }

    /**
     * Create a service token for testing service principal authentication.
     *
     * @param serviceId Service ID (subject)
     * @param permissions Service-level permissions
     * @param userId Optional user ID for service token with user context
     * @param userPermissions Optional user permissions for service token with user context
     * @return Signed service token
     */
    fun createServiceToken(
        serviceId: UUID = UUID.randomUUID(),
        permissions: List<String> = emptyList(),
        userId: UUID? = null,
        userPermissions: List<String>? = null,
    ): String =
        createToken(
            subject = serviceId.toString(),
            type = "service",
            permissions = permissions,
            userId = userId,
            userPermissions = userPermissions,
        )

    /**
     * Create a JWK (JSON Web Key) representation of the test RSA public key.
     * Used for testing JWKS endpoints.
     *
     * @param kid Key ID to include in JWK
     * @param publicKey RSA public key to convert (defaults to test public key)
     * @return JWK as a map
     */
    fun createJwk(
        kid: String = "kid-1",
        publicKey: RSAPublicKey = this.publicKey as RSAPublicKey,
    ): Map<String, String> {
        val modulus = publicKey.modulus.toByteArray()
        val exponent = publicKey.publicExponent.toByteArray()

        // Remove leading zero byte if present (BigInteger.toByteArray() adds for positive numbers)
        val n = removeLeadingZero(modulus)
        val e = removeLeadingZero(exponent)

        return mapOf(
            "kty" to "RSA",
            "kid" to kid,
            "use" to "sig",
            "alg" to "RS256",
            "n" to Base64.getUrlEncoder().withoutPadding().encodeToString(n),
            "e" to Base64.getUrlEncoder().withoutPadding().encodeToString(e),
        )
    }

    /**
     * Remove leading zero byte from byte array if present.
     */
    private fun removeLeadingZero(bytes: ByteArray): ByteArray =
        if (bytes.isNotEmpty() && bytes[0].toInt() == 0) {
            bytes.sliceArray(1 until bytes.size)
        } else {
            bytes
        }

    /**
     * Create a JWKS (JSON Web Key Set) response for testing.
     *
     * @param keys List of JWK maps
     * @return JWKS response map
     */
    fun createJwksResponse(keys: List<Map<String, String>> = listOf(createJwk())): Map<String, Any> =
        mapOf("keys" to keys)

    /**
     * Create a malformed token for testing error handling.
     *
     * @param type Type of malformed token ("missing-parts", "invalid-signature", "invalid-base64")
     * @return Malformed token string
     */
    fun createMalformedToken(type: String = "missing-parts"): String =
        when (type) {
            "missing-parts" -> "eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiJ0ZXN0In0" // Missing signature
            "invalid-signature" -> {
                val validToken = createToken()
                val parts = validToken.split(".")
                "${parts[0]}.${parts[1]}.INVALID_SIGNATURE"
            }
            "invalid-base64" -> "not-a-valid-jwt-token"
            else -> throw IllegalArgumentException("Unknown malformed token type: $type")
        }
}
