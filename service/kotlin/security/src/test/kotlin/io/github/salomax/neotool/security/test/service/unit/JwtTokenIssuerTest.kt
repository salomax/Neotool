package io.github.salomax.neotool.security.test.service.unit

import io.github.salomax.neotool.common.security.config.JwtConfig
import io.github.salomax.neotool.common.security.jwt.JwtTokenValidator
import io.github.salomax.neotool.common.security.key.KeyManager
import io.github.salomax.neotool.common.security.key.KeyManagerFactory
import io.github.salomax.neotool.common.security.principal.AuthContext
import io.github.salomax.neotool.common.security.principal.JwtPrincipalDecoder
import io.github.salomax.neotool.security.key.SecurityKeyManagerFactory
import io.github.salomax.neotool.security.service.jwt.JwtTokenIssuer
import io.jsonwebtoken.Claims
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
    private lateinit var validator: JwtTokenValidator
    private lateinit var decoder: JwtPrincipalDecoder

    @BeforeEach
    fun setUp() {
        val keyManager = mock<KeyManager>()
        val keyManagerFactory = mock<SecurityKeyManagerFactory>()
        val commonKeyManagerFactory = mock<KeyManagerFactory>()

        whenever(keyManager.getPrivateKey(any())).thenReturn(keyPair.private)
        whenever(keyManager.getPublicKey(any())).thenReturn(keyPair.public)
        whenever(keyManagerFactory.getKeyManager()).thenReturn(keyManager)
        whenever(commonKeyManagerFactory.getKeyManager()).thenReturn(keyManager)

        val jwtConfig = JwtConfig(issuer = configuredIssuer, keyId = "kid-1")
        issuer =
            JwtTokenIssuer(
                jwtConfig = jwtConfig,
                keyManagerFactory = keyManagerFactory,
            )
        validator = JwtTokenValidator(jwtConfig, commonKeyManagerFactory)
        decoder = JwtPrincipalDecoder(validator)
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

    @Test
    fun `service token with user context carries real audience and current account`() {
        val accountId = UUID.randomUUID()
        val token =
            issuer.generateServiceTokenWithUserContext(
                serviceId = UUID.randomUUID(),
                targetAudience = "banking",
                permissions = listOf("banking:transaction:view"),
                userId = UUID.randomUUID(),
                userPermissions = listOf("banking:transaction:view"),
                currentAccountId = accountId,
            )

        val claims = parseClaims(token)
        val principal = decoder.fromToken(token)

        assertThat(claims.audience).containsExactly("banking")
        assertThat(validator.getAudienceFromToken(token)).isEqualTo("banking")
        assertThat(principal.currentAccountId).isEqualTo(accountId)
    }

    @Test
    fun `should issue OAuth access token with requested resource as audience`() {
        val resource = "https://api.invistus.test"
        val token =
            issuer.generateOAuthAccessToken(
                userId = UUID.randomUUID(),
                email = "test@example.com",
                clientId = "test-agent",
                scopes = listOf("finance:cashflow:read"),
                resource = resource,
            )

        val claims = parseClaims(token)

        assertThat(claims.audience).containsExactly(resource)
        assertThat(claims["resource"]).isNull()
    }

    @Test
    fun `should issue unique jwt id for every token type`() {
        val userId = UUID.randomUUID()
        val serviceId = UUID.randomUUID()
        val issuedTokenIds =
            listOf(
                issuer.generateAccessToken(
                    userId = userId,
                    email = "test@example.com",
                    permissions = listOf("security:user:view"),
                ),
                issuer.generateAccessToken(
                    AuthContext(
                        userId = userId,
                        email = "test@example.com",
                        displayName = "Test User",
                        roles = emptyList(),
                        permissions = listOf("security:user:view"),
                    ),
                ),
                issuer.generateRefreshToken(userId),
                issuer.generateServiceToken(
                    serviceId = serviceId,
                    targetAudience = "banking",
                    permissions = listOf("banking:transaction:read"),
                ),
                issuer.generateServiceTokenWithUserContext(
                    serviceId = serviceId,
                    targetAudience = "banking",
                    permissions = listOf("banking:transaction:read"),
                    userId = userId,
                    userPermissions = listOf("banking:transaction:read"),
                ),
            ).map(::parseJwtId)

        assertThat(issuedTokenIds).doesNotContainNull()
        assertThat(issuedTokenIds.toSet()).hasSize(issuedTokenIds.size)
    }

    @Test
    fun `should issue unique jwt ids across repeated access and refresh tokens`() {
        val userId = UUID.randomUUID()
        val accessTokenIds =
            (1..5).map {
                parseJwtId(
                    issuer.generateAccessToken(
                        userId = userId,
                        email = "test@example.com",
                        permissions = listOf("security:user:view"),
                    ),
                )
            }
        val refreshTokenIds =
            (1..5).map {
                parseJwtId(issuer.generateRefreshToken(userId))
            }

        assertThat(accessTokenIds.toSet()).hasSize(accessTokenIds.size)
        assertThat(refreshTokenIds.toSet()).hasSize(refreshTokenIds.size)
        assertThat(accessTokenIds).doesNotContainNull()
        assertThat(refreshTokenIds).doesNotContainNull()
    }

    @Test
    fun `should round trip access token jwt id through validator and decoder`() {
        val userId = UUID.randomUUID()
        val token =
            issuer.generateAccessToken(
                userId = userId,
                email = "test@example.com",
                permissions = listOf("security:user:view"),
            )

        val tokenId = validator.getJwtIdFromToken(token)
        val principal = decoder.fromToken(token)

        assertThat(tokenId).isNotBlank()
        assertThat(principal.tokenId).isEqualTo(tokenId)
        assertThat(principal.userId).isEqualTo(userId)
    }

    @Test
    fun `should expose refresh token jwt id for rotation reuse detection`() {
        val token = issuer.generateRefreshToken(UUID.randomUUID())

        val tokenId = validator.getJwtIdFromToken(token)

        assertThat(tokenId).isNotBlank()
        assertThat(tokenId).isEqualTo(parseJwtId(token))
    }

    private fun parseIssuer(token: String): String = parseClaims(token).issuer

    private fun parseJwtId(token: String): String? = parseClaims(token).id

    private fun parseClaims(token: String): Claims =
        Jwts
            .parser()
            .verifyWith(keyPair.public as RSAPublicKey)
            .requireIssuer(configuredIssuer)
            .build()
            .parseSignedClaims(token)
            .payload
}
