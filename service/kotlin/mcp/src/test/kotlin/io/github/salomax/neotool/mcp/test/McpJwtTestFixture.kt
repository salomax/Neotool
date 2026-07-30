package io.github.salomax.neotool.mcp.test

import io.jsonwebtoken.Jwts
import io.micronaut.serde.annotation.Serdeable
import java.math.BigInteger
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

object McpJwtTestFixture {
    const val ISSUER = "https://issuer.example.test"
    const val AUDIENCE = "https://mcp.example.test"
    const val KEY_ID = "kid-1"

    private val keyPair: KeyPair by lazy {
        KeyPairGenerator.getInstance("RSA").apply {
            initialize(2048)
        }.generateKeyPair()
    }

    val publicKey: PublicKey
        get() = keyPair.public

    val publicKeyPem: String
        get() {
            val encoded = Base64.getMimeEncoder(64, "\n".toByteArray()).encodeToString(publicKey.encoded)
            return "-----BEGIN PUBLIC KEY-----\n$encoded\n-----END PUBLIC KEY-----"
        }

    private val privateKey: PrivateKey
        get() = keyPair.private

    fun accessToken(
        userId: UUID = UUID.randomUUID(),
        clientId: String = "test-agent",
        accountId: UUID? = UUID.randomUUID(),
        audience: String? = AUDIENCE,
        additionalAudiences: List<String> = emptyList(),
        scope: String? = "mcp:tools:call",
        expirationSeconds: Long = 900,
        issuedAt: Instant = Instant.now(),
    ): String {
        val builder =
            Jwts
                .builder()
                .subject(userId.toString())
                .issuer(ISSUER)
                .claim("type", "access")
                .claim("email", "oauth-$userId@example.test")
                .claim("client_id", clientId)
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(issuedAt.plusSeconds(expirationSeconds)))
                .id(UUID.randomUUID().toString())

        accountId?.let { builder.claim("current_account", it.toString()) }
        audience?.let { builder.audience().add(it).and() }
        additionalAudiences.forEach { builder.audience().add(it).and() }
        scope?.let { builder.claim("scope", it) }
        scope?.let { builder.claim("permissions", it.split(' ').filter(String::isNotBlank)) }

        return builder
            .header()
            .keyId(KEY_ID)
            .and()
            .signWith(privateKey as RSAPrivateKey)
            .compact()
    }

    fun expiredAccessToken(): String = accessToken(expirationSeconds = -60)

    fun serviceToken(
        serviceId: UUID = UUID.randomUUID(),
        userId: UUID? = null,
        userPermissions: List<String>? = null,
        currentAccountId: UUID? = null,
        audience: String = "mcp-service",
        permissions: List<String> = emptyList(),
        expirationSeconds: Long = 900,
        issuedAt: Instant = Instant.now(),
    ): String {
        val builder =
            Jwts
                .builder()
                .subject(serviceId.toString())
                .issuer(ISSUER)
                .claim("type", "service")
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(issuedAt.plusSeconds(expirationSeconds)))
                .id(UUID.randomUUID().toString())

        if (permissions.isNotEmpty()) {
            builder.claim("permissions", permissions)
        }
        userId?.let { builder.claim("user_id", it.toString()) }
        userPermissions?.let { builder.claim("user_permissions", it) }
        currentAccountId?.let { builder.claim("current_account", it.toString()) }
        builder.audience().add(audience).and()

        return builder
            .header()
            .keyId(KEY_ID)
            .and()
            .signWith(privateKey as RSAPrivateKey)
            .compact()
    }

    fun jwks(): JwksResponse {
        val rsaPublicKey = publicKey as RSAPublicKey
        return JwksResponse(
            keys =
                listOf(
                    Jwk(
                        kty = "RSA",
                        kid = KEY_ID,
                        use = "sig",
                        alg = "RS256",
                        n = base64UrlEncode(unsignedBytes(rsaPublicKey.modulus)),
                        e = base64UrlEncode(unsignedBytes(rsaPublicKey.publicExponent)),
                    ),
                ),
        )
    }

    private fun unsignedBytes(value: BigInteger): ByteArray {
        val bytes = value.toByteArray()
        return if (bytes.isNotEmpty() && bytes[0].toInt() == 0) {
            bytes.sliceArray(1 until bytes.size)
        } else {
            bytes
        }
    }

    private fun base64UrlEncode(bytes: ByteArray): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

    @Serdeable
    data class JwksResponse(
        val keys: List<Jwk>,
    )

    @Serdeable
    data class Jwk(
        val kty: String,
        val kid: String,
        val use: String,
        val alg: String,
        val n: String,
        val e: String,
    )
}
