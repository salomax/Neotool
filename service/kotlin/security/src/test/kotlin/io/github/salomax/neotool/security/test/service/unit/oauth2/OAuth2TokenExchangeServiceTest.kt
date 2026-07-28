package io.github.salomax.neotool.security.test.service.unit.oauth2

import io.github.salomax.neotool.common.security.config.JwtConfig
import io.github.salomax.neotool.common.security.jwt.JwtTokenValidator
import io.github.salomax.neotool.common.security.key.KeyManager
import io.github.salomax.neotool.common.security.key.KeyManagerFactory
import io.github.salomax.neotool.common.security.principal.PrincipalType
import io.github.salomax.neotool.security.domain.rbac.Permission
import io.github.salomax.neotool.security.http.dto.OAuth2TokenRequest
import io.github.salomax.neotool.security.key.SecurityKeyManagerFactory
import io.github.salomax.neotool.security.model.PrincipalEntity
import io.github.salomax.neotool.security.service.authorization.AuthorizationService
import io.github.salomax.neotool.security.service.jwt.JwtTokenIssuer
import io.github.salomax.neotool.security.service.management.ServicePrincipalService
import io.github.salomax.neotool.security.service.oauth2.DefaultOAuth2ScopePermissionMapper
import io.github.salomax.neotool.security.service.oauth2.OAuth2AccessTokenRevocationChecker
import io.github.salomax.neotool.security.service.oauth2.OAuth2InvalidGrantException
import io.github.salomax.neotool.security.service.oauth2.OAuth2TokenExchangeRateLimiter
import io.github.salomax.neotool.security.service.oauth2.OAuth2TokenExchangeResourceResolver
import io.github.salomax.neotool.security.service.oauth2.OAuth2TokenExchangeService
import io.jsonwebtoken.Jwts
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.time.Instant
import java.util.Date
import java.util.UUID

class OAuth2TokenExchangeServiceTest {
    private val authenticatedClientId = "mcp-service"
    private val jwtConfig =
        JwtConfig(
            issuer = "https://security.test",
            keyId = "kid-1",
            accessTokenExpirationSeconds = 900,
        )
    private val keyPair: KeyPair =
        KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
    private lateinit var issuer: JwtTokenIssuer
    private lateinit var validator: JwtTokenValidator
    private val servicePrincipalService: ServicePrincipalService = mock()
    private val authorizationService: AuthorizationService = mock()
    private val mapper = DefaultOAuth2ScopePermissionMapper()
    private val revocationChecker: OAuth2AccessTokenRevocationChecker = mock()
    private val rateLimiter: OAuth2TokenExchangeRateLimiter = mock()
    private val resourceResolver: OAuth2TokenExchangeResourceResolver = mock()
    private lateinit var exchangeService: OAuth2TokenExchangeService

    @BeforeEach
    fun setUp() {
        val keyManager = mock<KeyManager>()
        val securityKeyManagerFactory = mock<SecurityKeyManagerFactory>()
        val commonKeyManagerFactory = mock<KeyManagerFactory>()
        whenever(keyManager.getPrivateKey(any())).thenReturn(keyPair.private)
        whenever(keyManager.getPublicKey(any())).thenReturn(keyPair.public)
        whenever(securityKeyManagerFactory.getKeyManager()).thenReturn(keyManager)
        whenever(commonKeyManagerFactory.getKeyManager()).thenReturn(keyManager)

        issuer = JwtTokenIssuer(jwtConfig, securityKeyManagerFactory)
        validator = JwtTokenValidator(jwtConfig, commonKeyManagerFactory)
        exchangeService =
            OAuth2TokenExchangeService(
                servicePrincipalService = servicePrincipalService,
                jwtTokenValidator = validator,
                jwtTokenIssuer = issuer,
                authorizationService = authorizationService,
                scopePermissionMapper = mapper,
                revocationChecker = revocationChecker,
                rateLimiter = rateLimiter,
                resourceResolver = resourceResolver,
                jwtConfig = jwtConfig,
            )
        whenever(resourceResolver.resolveServiceResourceIdentifier(authenticatedClientId))
            .thenReturn("https://mcp.invistus.test")
    }

    @Test
    fun `user lacking permission cannot obtain it through token exchange`() {
        val servicePrincipalId = UUID.randomUUID()
        val userId = UUID.randomUUID()
        val accountId = UUID.randomUUID()
        whenever(servicePrincipalService.validateServiceCredentials(authenticatedClientId, "secret"))
            .thenReturn(servicePrincipal(servicePrincipalId))
        whenever(servicePrincipalService.getServicePermissions(servicePrincipalId))
            .thenReturn(listOf("service:call"))
        whenever(authorizationService.getUserPermissions(any(), any()))
            .thenReturn(listOf(Permission(name = "banking:transaction:view")))
        whenever(revocationChecker.isRevoked(any())).thenReturn(false)

        val subjectToken =
            issuer.generateOAuthAccessToken(
                userId = userId,
                email = "user@example.com",
                clientId = "agent",
                scopes = listOf("finance:transactions:write"),
                resource = "https://mcp.invistus.test",
                currentAccountId = accountId,
            )

        val response = exchangeService.exchange(validRequest(subjectToken))
        val delegatedToken = response.access_token

        assertThat(validator.getUserPermissionsFromServiceToken(delegatedToken)).isEmpty()
        assertThat(validator.getCurrentAccountIdFromToken(delegatedToken)).isEqualTo(accountId)
        assertThat(validator.getAudienceFromToken(delegatedToken)).isEqualTo("banking")
        assertThat(validator.getDelegatedByFromServiceToken(delegatedToken)).isEqualTo(authenticatedClientId)
        assertThat(response.scope).isEmpty()
    }

    @Test
    fun `delegated by is derived from authenticated caller and ignores request value`() {
        val servicePrincipalId = UUID.randomUUID()
        val userId = UUID.randomUUID()
        whenever(servicePrincipalService.validateServiceCredentials(authenticatedClientId, "secret"))
            .thenReturn(servicePrincipal(servicePrincipalId))
        whenever(servicePrincipalService.getServicePermissions(servicePrincipalId))
            .thenReturn(listOf("service:call"))
        whenever(authorizationService.getUserPermissions(userId))
            .thenReturn(listOf(Permission(name = "banking:transaction:view")))
        whenever(revocationChecker.isRevoked(any())).thenReturn(false)
        val subjectToken =
            oauthSubjectToken(
                userId = userId,
                scopes = listOf("finance:cashflow:read"),
            )

        val response =
            exchangeService.exchange(
                validRequest(subjectToken).copy(
                    delegated_by = "forged-delegator",
                ),
            )

        assertThat(validator.getDelegatedByFromServiceToken(response.access_token)).isEqualTo(authenticatedClientId)
    }

    @Test
    fun `rejects subject token with wrong audience`() {
        whenever(servicePrincipalService.validateServiceCredentials(authenticatedClientId, "secret"))
            .thenReturn(servicePrincipal(UUID.randomUUID()))
        val subjectToken =
            issuer.generateOAuthAccessToken(
                userId = UUID.randomUUID(),
                email = "user@example.com",
                clientId = "agent",
                scopes = listOf("finance:cashflow:read"),
                resource = "https://other-resource.invistus.test",
                currentAccountId = UUID.randomUUID(),
            )

        assertThatThrownBy { exchangeService.exchange(validRequest(subjectToken)) }
            .isInstanceOf(OAuth2InvalidGrantException::class.java)
            .hasMessageContaining("audience")
    }

    @Test
    fun `rejects subject token without current account`() {
        whenever(servicePrincipalService.validateServiceCredentials(authenticatedClientId, "secret"))
            .thenReturn(servicePrincipal(UUID.randomUUID()))
        val subjectToken = oauthSubjectTokenWithoutCurrentAccount()

        assertThatThrownBy { exchangeService.exchange(validRequest(subjectToken)) }
            .isInstanceOf(OAuth2InvalidGrantException::class.java)
            .hasMessageContaining("current_account")
    }

    @Test
    fun `rejects exchange when caller has no configured resource identifier`() {
        whenever(resourceResolver.resolveServiceResourceIdentifier(authenticatedClientId)).thenReturn(null)
        whenever(servicePrincipalService.validateServiceCredentials(authenticatedClientId, "secret"))
            .thenReturn(servicePrincipal(UUID.randomUUID()))
        val subjectToken =
            issuer.generateOAuthAccessToken(
                userId = UUID.randomUUID(),
                email = "user@example.com",
                clientId = "agent",
                scopes = listOf("finance:cashflow:read"),
                resource = "https://mcp.invistus.test",
                currentAccountId = UUID.randomUUID(),
            )

        assertThatThrownBy { exchangeService.exchange(validRequest(subjectToken)) }
            .isInstanceOf(OAuth2InvalidGrantException::class.java)
            .hasMessageContaining("resource identifier")
    }

    @Test
    fun `rejects unsupported requested token type`() {
        val subjectToken =
            issuer.generateOAuthAccessToken(
                userId = UUID.randomUUID(),
                email = "user@example.com",
                clientId = "agent",
                scopes = listOf("finance:cashflow:read"),
                resource = "https://mcp.invistus.test",
                currentAccountId = UUID.randomUUID(),
            )

        assertThatThrownBy {
            exchangeService.exchange(
                validRequest(subjectToken).copy(
                    requested_token_type = "urn:ietf:params:oauth:token-type:refresh_token",
                ),
            )
        }.isInstanceOf(RuntimeException::class.java)
            .hasMessageContaining("requested_token_type")
    }

    @Test
    fun `response scope uses OAuth scopes that survived permission intersection`() {
        val servicePrincipalId = UUID.randomUUID()
        val userId = UUID.randomUUID()
        whenever(servicePrincipalService.validateServiceCredentials(authenticatedClientId, "secret"))
            .thenReturn(servicePrincipal(servicePrincipalId))
        whenever(servicePrincipalService.getServicePermissions(servicePrincipalId))
            .thenReturn(listOf("service:call"))
        whenever(authorizationService.getUserPermissions(any(), any()))
            .thenReturn(listOf(Permission(name = "banking:transaction:view")))
        whenever(revocationChecker.isRevoked(any())).thenReturn(false)
        val subjectToken =
            oauthSubjectToken(
                userId = userId,
                scopes = listOf("finance:cashflow:read", "finance:transactions:write"),
            )

        val response = exchangeService.exchange(validRequest(subjectToken))

        assertThat(response.scope).isEqualTo("finance:cashflow:read")
    }

    @Test
    fun `rejects revoked subject token`() {
        whenever(servicePrincipalService.validateServiceCredentials(authenticatedClientId, "secret"))
            .thenReturn(servicePrincipal(UUID.randomUUID()))
        whenever(revocationChecker.isRevoked(any())).thenReturn(true)
        val subjectToken = oauthSubjectToken()

        assertThatThrownBy { exchangeService.exchange(validRequest(subjectToken)) }
            .isInstanceOf(OAuth2InvalidGrantException::class.java)
            .hasMessageContaining("revoked")
    }

    @Test
    fun `rejects unregistered caller`() {
        whenever(servicePrincipalService.validateServiceCredentials(authenticatedClientId, "secret")).thenReturn(null)
        val subjectToken = oauthSubjectToken()

        assertThatThrownBy { exchangeService.exchange(validRequest(subjectToken)) }
            .isInstanceOf(OAuth2InvalidGrantException::class.java)
            .hasMessageContaining("Invalid client credentials")
    }

    @Test
    fun `rejects expired subject token`() {
        whenever(servicePrincipalService.validateServiceCredentials(authenticatedClientId, "secret"))
            .thenReturn(servicePrincipal(UUID.randomUUID()))
        val subjectToken = oauthSubjectToken(expiresAt = Instant.now().minusSeconds(5))

        assertThatThrownBy { exchangeService.exchange(validRequest(subjectToken)) }
            .isInstanceOf(OAuth2InvalidGrantException::class.java)
            .hasMessageContaining("expired")
    }

    @Test
    fun `delegated token ttl does not exceed subject token remaining lifetime`() {
        val servicePrincipalId = UUID.randomUUID()
        val userId = UUID.randomUUID()
        val subjectExpiresAt = Instant.now().plusSeconds(120)
        whenever(servicePrincipalService.validateServiceCredentials(authenticatedClientId, "secret"))
            .thenReturn(servicePrincipal(servicePrincipalId))
        whenever(servicePrincipalService.getServicePermissions(servicePrincipalId))
            .thenReturn(listOf("service:call"))
        whenever(authorizationService.getUserPermissions(userId))
            .thenReturn(listOf(Permission(name = "banking:transaction:view")))
        whenever(revocationChecker.isRevoked(any())).thenReturn(false)
        val subjectToken =
            oauthSubjectToken(
                userId = userId,
                scopes = listOf("finance:cashflow:read"),
                expiresAt = subjectExpiresAt,
            )

        val response = exchangeService.exchange(validRequest(subjectToken))
        val delegatedExpiresAt = validator.getTokenExpiration(response.access_token)

        assertThat(response.expires_in).isBetween(1L, 120L)
        assertThat(delegatedExpiresAt).isNotNull()
        assertThat(delegatedExpiresAt).isBeforeOrEqualTo(subjectExpiresAt)
    }

    private fun validRequest(subjectToken: String) =
        OAuth2TokenRequest(
            grant_type = OAuth2TokenExchangeService.TOKEN_EXCHANGE_GRANT_TYPE,
            client_id = authenticatedClientId,
            client_secret = "secret",
            audience = "banking",
            delegated_by = authenticatedClientId,
            subject_token = subjectToken,
            subject_token_type = OAuth2TokenExchangeService.ACCESS_TOKEN_TYPE,
        )

    private fun oauthSubjectToken(
        userId: UUID = UUID.randomUUID(),
        scopes: List<String> = listOf("finance:cashflow:read"),
        expiresAt: Instant? = null,
    ): String =
        issuer.generateOAuthAccessToken(
            userId = userId,
            email = "user@example.com",
            clientId = "agent",
            scopes = scopes,
            resource = "https://mcp.invistus.test",
            currentAccountId = UUID.randomUUID(),
            expiresAt = expiresAt,
        )

    private fun oauthSubjectTokenWithoutCurrentAccount(
        userId: UUID = UUID.randomUUID(),
        scopes: List<String> = listOf("finance:cashflow:read"),
        expiresAt: Instant? = null,
    ): String {
        val now = Instant.now()
        val expiration = expiresAt ?: now.plusSeconds(jwtConfig.accessTokenExpirationSeconds)
        return Jwts
            .builder()
            .subject(userId.toString())
            .claim("email", "user@example.com")
            .claim("type", "access")
            .claim("client_id", "agent")
            .claim("scope", scopes.joinToString(" "))
            .claim("permissions", scopes)
            .audience()
            .add("https://mcp.invistus.test")
            .and()
            .issuedAt(Date.from(now))
            .expiration(Date.from(expiration))
            .issuer(jwtConfig.issuer)
            .id(UUID.randomUUID().toString())
            .signWith(keyPair.private)
            .compact()
    }

    private fun servicePrincipal(id: UUID) =
        PrincipalEntity(
            id = id,
            principalType = PrincipalType.SERVICE,
            externalId = authenticatedClientId,
            enabled = true,
        )
}
