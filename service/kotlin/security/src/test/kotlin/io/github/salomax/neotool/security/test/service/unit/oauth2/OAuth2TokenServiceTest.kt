package io.github.salomax.neotool.security.test.service.unit.oauth2

import io.github.salomax.neotool.common.security.config.JwtConfig
import io.github.salomax.neotool.common.security.jwt.JwtTokenValidator
import io.github.salomax.neotool.common.security.key.KeyManager
import io.github.salomax.neotool.common.security.key.KeyManagerFactory
import io.github.salomax.neotool.common.security.principal.AuthContext
import io.github.salomax.neotool.common.security.principal.PrincipalType
import io.github.salomax.neotool.security.http.dto.OAuth2TokenRequest
import io.github.salomax.neotool.security.key.SecurityKeyManagerFactory
import io.github.salomax.neotool.security.model.OAuth2AuthorizationCodeEntity
import io.github.salomax.neotool.security.model.OAuth2AuthorizationRequestEntity
import io.github.salomax.neotool.security.model.OAuth2AuthorizationRequestStatus
import io.github.salomax.neotool.security.model.PrincipalEntity
import io.github.salomax.neotool.security.model.RefreshTokenEntity
import io.github.salomax.neotool.security.repo.OAuth2AuthorizationCodeRepository
import io.github.salomax.neotool.security.repo.OAuth2AuthorizationRequestRepository
import io.github.salomax.neotool.security.repo.PrincipalRepository
import io.github.salomax.neotool.security.repo.RefreshTokenRepository
import io.github.salomax.neotool.security.repo.UserRepository
import io.github.salomax.neotool.security.service.authentication.AuthContextFactory
import io.github.salomax.neotool.security.service.jwt.JwtTokenIssuer
import io.github.salomax.neotool.security.service.jwt.RefreshTokenService
import io.github.salomax.neotool.security.service.oauth2.OAuth2ClientRegistry
import io.github.salomax.neotool.security.service.oauth2.OAuth2InvalidGrantException
import io.github.salomax.neotool.security.service.oauth2.OAuth2TokenExchangeService
import io.github.salomax.neotool.security.service.oauth2.OAuth2TokenHashing
import io.github.salomax.neotool.security.service.oauth2.OAuth2TokenRateLimitDecision
import io.github.salomax.neotool.security.service.oauth2.OAuth2TokenRateLimitService
import io.github.salomax.neotool.security.service.oauth2.OAuth2TokenService
import io.github.salomax.neotool.security.test.SecurityTestDataBuilders
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.time.Instant
import java.util.Optional
import java.util.UUID

class OAuth2TokenServiceTest {
    private val jwtConfig =
        JwtConfig(
            issuer = "https://security.test",
            keyId = "kid-1",
            accessTokenExpirationSeconds = 900,
            refreshTokenExpirationSeconds = 604800,
        )
    private val keyPair: KeyPair =
        KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
    private val authorizationCodeRepository: OAuth2AuthorizationCodeRepository = mock()
    private val authorizationRequestRepository: OAuth2AuthorizationRequestRepository = mock()
    private val userRepository: UserRepository = mock()
    private val refreshTokenRepository: RefreshTokenRepository = mock()
    private val principalRepository: PrincipalRepository = mock()
    private val authContextFactory: AuthContextFactory = mock()
    private val clientRegistry: OAuth2ClientRegistry = mock()
    private val tokenExchangeService: OAuth2TokenExchangeService = mock()
    private val tokenRateLimitService: OAuth2TokenRateLimitService = mock()
    private val tokenHashing = OAuth2TokenHashing()

    private lateinit var jwtTokenIssuer: JwtTokenIssuer
    private lateinit var jwtTokenValidator: JwtTokenValidator
    private lateinit var refreshTokenService: RefreshTokenService
    private lateinit var tokenService: OAuth2TokenService

    @BeforeEach
    fun setUp() {
        val keyManager = mock<KeyManager>()
        val securityKeyManagerFactory = mock<SecurityKeyManagerFactory>()
        val commonKeyManagerFactory = mock<KeyManagerFactory>()
        whenever(keyManager.getPrivateKey(any())).thenReturn(keyPair.private)
        whenever(keyManager.getPublicKey(any())).thenReturn(keyPair.public)
        whenever(securityKeyManagerFactory.getKeyManager()).thenReturn(keyManager)
        whenever(commonKeyManagerFactory.getKeyManager()).thenReturn(keyManager)

        jwtTokenIssuer = JwtTokenIssuer(jwtConfig, securityKeyManagerFactory)
        jwtTokenValidator = JwtTokenValidator(jwtConfig, commonKeyManagerFactory)
        refreshTokenService =
            RefreshTokenService(
                refreshTokenRepository = refreshTokenRepository,
                userRepository = userRepository,
                principalRepository = principalRepository,
                jwtTokenIssuer = jwtTokenIssuer,
                authContextFactory = authContextFactory,
                jwtConfig = jwtConfig,
            )
        tokenService =
            OAuth2TokenService(
                authorizationCodeRepository = authorizationCodeRepository,
                authorizationRequestRepository = authorizationRequestRepository,
                userRepository = userRepository,
                jwtTokenIssuer = jwtTokenIssuer,
                jwtTokenValidator = jwtTokenValidator,
                refreshTokenService = refreshTokenService,
                tokenHashing = tokenHashing,
                clientRegistry = clientRegistry,
                jwtConfig = jwtConfig,
                tokenExchangeService = tokenExchangeService,
                authContextFactory = authContextFactory,
                tokenRateLimitService = tokenRateLimitService,
            )
        whenever(tokenRateLimitService.check(any())).thenReturn(
            OAuth2TokenRateLimitDecision(
                allowed = true,
                remaining = 9,
                resetAt = Instant.now().plusSeconds(60),
                retryAfterSeconds = 0,
            ),
        )
    }

    @Test
    fun `authorization code flow fails closed when no account can be resolved`() {
        val userId = UUID.randomUUID()
        val user = SecurityTestDataBuilders.user(id = userId, email = "user@example.com")
        val requestId = UUID.randomUUID()
        val verifier = "verifier-123"
        val code = "authorization-code"
        val codeHash = tokenHashing.sha256Hex(code)
        val request =
            approvedAuthorizationRequest(
                requestId = requestId,
                userId = userId,
                verifier = verifier,
                accountId = null,
            )

        whenever(authorizationCodeRepository.findByCodeHash(codeHash))
            .thenReturn(
                OAuth2AuthorizationCodeEntity(
                    codeHash = codeHash,
                    authorizationRequestId = requestId,
                    expiresAt = Instant.now().plusSeconds(60),
                ),
            )
        whenever(authorizationCodeRepository.consumeIfActive(any(), any())).thenReturn(1L)
        whenever(authorizationRequestRepository.findById(requestId)).thenReturn(Optional.of(request))
        whenever(userRepository.findById(userId)).thenReturn(Optional.of(user))
        whenever(authContextFactory.build(user))
            .thenReturn(
                AuthContext(
                    userId = userId,
                    email = user.email,
                    displayName = user.displayName,
                    roles = emptyList(),
                    permissions = emptyList(),
                    currentAccountId = null,
                ),
            )

        val exception =
            assertThrows<OAuth2InvalidGrantException> {
                tokenService.issue(
                    OAuth2TokenRequest(
                        grant_type = "authorization_code",
                        code = code,
                        client_id = "test-agent",
                        redirect_uri = "https://client.example/callback",
                        code_verifier = verifier,
                    ),
                )
            }

        assertThat(exception.message).contains("no account")
    }

    @Test
    fun `authorization code and refresh grants emit matching claim sets`() {
        val userId = UUID.randomUUID()
        val accountId = UUID.randomUUID()
        val user = SecurityTestDataBuilders.user(id = userId, email = "user@example.com")
        val requestId = UUID.randomUUID()
        val verifier = "verifier-123"
        val code = "authorization-code"
        val codeHash = tokenHashing.sha256Hex(code)
        val persistedTokens = mutableMapOf<String, RefreshTokenEntity>()
        val request =
            approvedAuthorizationRequest(
                requestId = requestId,
                userId = userId,
                verifier = verifier,
                accountId = accountId,
            )

        whenever(authorizationCodeRepository.findByCodeHash(codeHash))
            .thenReturn(
                OAuth2AuthorizationCodeEntity(
                    codeHash = codeHash,
                    authorizationRequestId = requestId,
                    expiresAt = Instant.now().plusSeconds(60),
                ),
            )
        whenever(authorizationCodeRepository.consumeIfActive(any(), any())).thenReturn(1L)
        whenever(authorizationRequestRepository.findById(requestId)).thenReturn(Optional.of(request))
        whenever(userRepository.findById(userId)).thenReturn(Optional.of(user))
        whenever(refreshTokenRepository.save(any())).thenAnswer {
            val saved = it.arguments[0] as RefreshTokenEntity
            persistedTokens[saved.tokenHash] = saved
            saved
        }
        whenever(refreshTokenRepository.findByTokenHash(any())).thenAnswer {
            persistedTokens[it.arguments[0] as String]
        }
        whenever(
            principalRepository.findByPrincipalTypeAndExternalId(
                PrincipalType.USER,
                userId.toString(),
            ),
        ).thenReturn(
            Optional.of(
                PrincipalEntity(
                    id = UUID.randomUUID(),
                    principalType = PrincipalType.USER,
                    externalId = userId.toString(),
                    enabled = true,
                ),
            ),
        )

        val authorizationCodeResponse =
            tokenService.issue(
                OAuth2TokenRequest(
                    grant_type = "authorization_code",
                    code = code,
                    client_id = "test-agent",
                    redirect_uri = "https://client.example/callback",
                    code_verifier = verifier,
                ),
            )
        val refreshedResponse =
            tokenService.issue(
                OAuth2TokenRequest(
                    grant_type = "refresh_token",
                    refresh_token = authorizationCodeResponse.refresh_token,
                ),
            )

        assertThat(normalizedClaims(authorizationCodeResponse.access_token))
            .isEqualTo(normalizedClaims(refreshedResponse.access_token))
        assertThat(jwtTokenValidator.getCurrentAccountIdFromToken(refreshedResponse.access_token)).isEqualTo(accountId)
        assertThat(jwtTokenValidator.getAudienceFromToken(refreshedResponse.access_token))
            .isEqualTo("https://api.invistus.test")
        assertThat(refreshedResponse.scope).isEqualTo("finance:cashflow:read")
    }

    private fun approvedAuthorizationRequest(
        requestId: UUID,
        userId: UUID,
        verifier: String,
        accountId: UUID?,
    ) = OAuth2AuthorizationRequestEntity(
        id = requestId,
        clientId = "test-agent",
        redirectUri = "https://client.example/callback",
        requestedScopes = "finance:cashflow:read",
        grantedScopes = "finance:cashflow:read",
        codeChallenge = tokenHashing.pkceS256(verifier),
        resource = "https://api.invistus.test",
        userId = userId,
        accountId = accountId,
        status = OAuth2AuthorizationRequestStatus.APPROVED,
        expiresAt = Instant.now().plusSeconds(60),
    )

    private fun normalizedClaims(token: String): Map<String, Any?> {
        val claims = jwtTokenValidator.validateToken(token)
        val normalized =
            claims.entries
                .filterNot { (key, _) -> key in setOf("iat", "exp", "jti") }
                .associate { (key, value) -> key to value }
                .toMutableMap()
        normalized["sub"] = claims.subject
        normalized["iss"] = claims.issuer
        normalized["aud"] = claims.audience.toList()
        return normalized.toSortedMap()
    }
}
