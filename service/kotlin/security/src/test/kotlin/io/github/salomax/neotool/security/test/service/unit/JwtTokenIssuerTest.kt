package io.github.salomax.neotool.security.test.service.unit

import io.github.salomax.neotool.common.security.config.JwtConfig
import io.github.salomax.neotool.common.security.key.KeyManager
import io.github.salomax.neotool.common.security.principal.AuthContext
import io.github.salomax.neotool.security.key.SecurityKeyManagerFactory
import io.github.salomax.neotool.security.service.jwt.JwtTokenIssuer
import io.jsonwebtoken.Jwts
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPublicKey
import java.util.UUID

class JwtTokenIssuerTest {
    private val configuredIssuer = "https://security.invistus.com.br"
    private val keyPair: KeyPair =
        KeyPairGenerator.getInstance("RSA").apply {
            initialize(2048)
        }.generateKeyPair()
    private lateinit var issuer: JwtTokenIssuer

    @BeforeEach
    fun setUp() {
        val keyManager = mock<KeyManager>()
        val keyManagerFactory = mock<SecurityKeyManagerFactory>()

        whenever(keyManager.getPrivateKey(any())).thenReturn(keyPair.private)
        whenever(keyManagerFactory.getKeyManager()).thenReturn(keyManager)

        issuer =
            JwtTokenIssuer(
                jwtConfig = JwtConfig(issuer = configuredIssuer, keyId = "kid-1"),
                keyManagerFactory = keyManagerFactory,
            )
    }

    @Test
    fun `should use configured issuer for access token generated from user data`() {
        val token =
            issuer.generateAccessToken(
                userId = UUID.randomUUID(),
                email = "test@example.com",
                permissions = listOf("security:user:view"),
            )

        assertThat(parseIssuer(token)).isEqualTo(configuredIssuer)
    }

    @Test
    fun `should use configured issuer for access token generated from auth context`() {
        val token =
            issuer.generateAccessToken(
                AuthContext(
                    userId = UUID.randomUUID(),
                    email = "test@example.com",
                    displayName = "Test User",
                    roles = emptyList(),
                    permissions = listOf("security:user:view"),
                ),
            )

        assertThat(parseIssuer(token)).isEqualTo(configuredIssuer)
    }

    @Test
    fun `should use configured issuer for refresh token`() {
        val token = issuer.generateRefreshToken(UUID.randomUUID())

        assertThat(parseIssuer(token)).isEqualTo(configuredIssuer)
    }

    @Test
    fun `should use configured issuer for service token`() {
        val token =
            issuer.generateServiceToken(
                serviceId = UUID.randomUUID(),
                targetAudience = "banking",
                permissions = listOf("banking:transaction:read"),
            )

        assertThat(parseIssuer(token)).isEqualTo(configuredIssuer)
    }

    @Test
    fun `should use configured issuer for service token with user context`() {
        val token =
            issuer.generateServiceTokenWithUserContext(
                serviceId = UUID.randomUUID(),
                targetAudience = "banking",
                permissions = listOf("banking:transaction:read"),
                userId = UUID.randomUUID(),
                userPermissions = listOf("banking:transaction:read"),
            )

        assertThat(parseIssuer(token)).isEqualTo(configuredIssuer)
    }

    private fun parseIssuer(token: String): String =
        Jwts
            .parser()
            .verifyWith(keyPair.public as RSAPublicKey)
            .requireIssuer(configuredIssuer)
            .build()
            .parseSignedClaims(token)
            .payload
            .issuer
}
