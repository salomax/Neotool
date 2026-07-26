package io.github.salomax.neotool.security.test.http

import io.github.salomax.neotool.common.security.principal.PrincipalType
import io.github.salomax.neotool.common.test.http.exchangeAsString
import io.github.salomax.neotool.common.test.integration.BaseIntegrationTest
import io.github.salomax.neotool.common.test.integration.PostgresIntegrationTest
import io.github.salomax.neotool.common.test.transaction.runTransaction
import io.github.salomax.neotool.security.http.dto.OAuth2ClientRegistrationResponse
import io.github.salomax.neotool.security.model.PrincipalEntity
import io.github.salomax.neotool.security.model.UserEntity
import io.github.salomax.neotool.security.model.account.AccountEntity
import io.github.salomax.neotool.security.model.account.AccountMembershipEntity
import io.github.salomax.neotool.security.model.account.AccountRole
import io.github.salomax.neotool.security.model.account.AccountType
import io.github.salomax.neotool.security.model.account.MembershipStatus
import io.github.salomax.neotool.security.repo.AccountMembershipRepository
import io.github.salomax.neotool.security.repo.AccountRepository
import io.github.salomax.neotool.security.repo.OAuth2AuthorizationCodeRepository
import io.github.salomax.neotool.security.repo.OAuth2AuthorizationRequestRepository
import io.github.salomax.neotool.security.repo.OAuth2ClientRepository
import io.github.salomax.neotool.security.repo.OAuth2ConsentRepository
import io.github.salomax.neotool.security.repo.PrincipalRepository
import io.github.salomax.neotool.security.repo.RefreshTokenRepository
import io.github.salomax.neotool.security.repo.UserRepository
import io.github.salomax.neotool.security.service.jwt.JwtTokenIssuer
import io.github.salomax.neotool.security.service.management.ServicePrincipalService
import io.github.salomax.neotool.security.service.oauth2.OAuth2AuthorizationService
import io.github.salomax.neotool.security.service.oauth2.OAuth2AuthorizeInput
import io.github.salomax.neotool.security.service.oauth2.OAuth2TokenExchangeService
import io.github.salomax.neotool.security.service.oauth2.OAuth2TokenHashing
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import io.micronaut.test.support.TestPropertyProvider
import jakarta.inject.Inject
import jakarta.persistence.EntityManager
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.fail
import java.util.UUID

@MicronautTest(startApplication = true)
@DisplayName("OAuth2 token rate limit integration")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Tag("integration")
@Tag("security")
@Tag("http")
open class OAuth2TokenRateLimitIntegrationTest :
    BaseIntegrationTest(),
    PostgresIntegrationTest,
    TestPropertyProvider {
    @Inject
    lateinit var authorizationService: OAuth2AuthorizationService

    @Inject
    lateinit var tokenHashing: OAuth2TokenHashing

    @Inject
    lateinit var jwtTokenIssuer: JwtTokenIssuer

    @Inject
    lateinit var servicePrincipalService: ServicePrincipalService

    @Inject
    lateinit var userRepository: UserRepository

    @Inject
    lateinit var principalRepository: PrincipalRepository

    @Inject
    lateinit var refreshTokenRepository: RefreshTokenRepository

    @Inject
    lateinit var authorizationCodeRepository: OAuth2AuthorizationCodeRepository

    @Inject
    lateinit var authorizationRequestRepository: OAuth2AuthorizationRequestRepository

    @Inject
    lateinit var oauth2ClientRepository: OAuth2ClientRepository

    @Inject
    lateinit var oauth2ConsentRepository: OAuth2ConsentRepository

    @Inject
    lateinit var accountRepository: AccountRepository

    @Inject
    lateinit var accountMembershipRepository: AccountMembershipRepository

    @Inject
    lateinit var entityManager: EntityManager

    private val createdUserIds = mutableSetOf<UUID>()
    private val createdAccountIds = mutableSetOf<UUID>()
    private var tokenExchangeClientSecret: String? = null

    override fun getProperties(): MutableMap<String, String> {
        val props = super.getProperties()
        props["oauth.authorization-server.token.rate-limit.limit"] = "2"
        props["oauth.authorization-server.token.rate-limit.window-ms"] = "3600000"
        props["oauth.authorization-server.token-exchange.service-resource-identifiers.mcp-service"] =
            "https://api.invistus.test"
        return props
    }

    @AfterEach
    fun cleanupTestData() {
        entityManager.runTransaction {
            entityManager.createNativeQuery("TRUNCATE TABLE security.oauth2_token_rate_limit_counters").executeUpdate()
            authorizationCodeRepository.deleteAll()
            authorizationRequestRepository.deleteAll()
            oauth2ConsentRepository.deleteAll()
            oauth2ClientRepository.deleteAll()
            refreshTokenRepository.deleteAll()
            if (createdAccountIds.isNotEmpty()) {
                accountMembershipRepository.deleteAll(
                    createdAccountIds.flatMap { accountMembershipRepository.findByAccountId(it) },
                )
                accountRepository.deleteAll(accountRepository.findAll().filter { it.id in createdAccountIds })
                createdAccountIds.clear()
            }
            if (createdUserIds.isNotEmpty()) {
                principalRepository.deleteAll(
                    principalRepository.findByPrincipalTypeAndExternalIdIn(
                        PrincipalType.USER,
                        createdUserIds.map { it.toString() },
                    ),
                )
                userRepository.deleteAll(userRepository.findByIdIn(createdUserIds.toList()))
                createdUserIds.clear()
            }
        }
    }

    @Test
    fun `authorization code flow returns 429 with retry after once the client exceeds the limit`() {
        val verifier = "correct-horse-battery-staple-0123456789"
        val client = registerRuntimeClient()
        val code1 = createApprovedAuthorizationCode(verifier, client.client_id)
        val code2 = createApprovedAuthorizationCode(verifier, client.client_id)
        exchangeAuthorizationCode(code1, verifier, client.client_id)
        exchangeAuthorizationCode(code2, verifier, client.client_id)
        val code3 = createApprovedAuthorizationCode(verifier, client.client_id)

        val exception =
            assertThrows<HttpClientResponseException> {
                exchangeAuthorizationCode(code3, verifier, client.client_id)
            }

        assertThat(exception.status).isEqualTo(HttpStatus.TOO_MANY_REQUESTS)
        assertThat(exception.response.header("Retry-After")).isNotBlank()
    }

    @Test
    fun `one client hitting 429 does not block a different client`() {
        val verifier = "correct-horse-battery-staple-0123456789"
        val clientOne = registerRuntimeClient()
        exchangeAuthorizationCode(
            createApprovedAuthorizationCode(verifier, clientOne.client_id),
            verifier,
            clientOne.client_id,
        )
        exchangeAuthorizationCode(
            createApprovedAuthorizationCode(verifier, clientOne.client_id),
            verifier,
            clientOne.client_id,
        )
        assertThrows<HttpClientResponseException> {
            exchangeAuthorizationCode(
                createApprovedAuthorizationCode(verifier, clientOne.client_id),
                verifier,
                clientOne.client_id,
            )
        }

        val clientTwo = registerRuntimeClient()
        val response =
            exchangeAuthorizationCode(
                createApprovedAuthorizationCode(verifier, clientTwo.client_id),
                verifier,
                clientTwo.client_id,
            )

        assertThat(response.status()).isEqualTo(HttpStatus.OK)
    }

    @Test
    fun `token exchange flow returns 429 with retry after once the client exceeds the limit`() {
        val (userId, accountId) = createUserAndAccount()
        val clientSecret = tokenExchangeClientSecret()
        val firstSubjectToken = createOAuthSubjectToken(userId, accountId)
        val secondSubjectToken = createOAuthSubjectToken(userId, accountId)

        val firstResponse = exchangeTokenOrFail(firstSubjectToken, clientSecret, "first")
        val secondResponse = exchangeTokenOrFail(secondSubjectToken, clientSecret, "second")

        assertThat(firstResponse.status()).isEqualTo(HttpStatus.OK)
        assertThat(secondResponse.status()).isEqualTo(HttpStatus.OK)

        val exception =
            assertThrows<HttpClientResponseException> {
                exchangeToken(createOAuthSubjectToken(userId, accountId), clientSecret)
            }

        assertThat(exception.status).isEqualTo(HttpStatus.TOO_MANY_REQUESTS)
        assertThat(exception.response.header("Retry-After")).isNotBlank()
    }

    private fun createApprovedAuthorizationCode(
        verifier: String,
        clientId: String,
    ): String =
        entityManager.runTransaction {
            val savedUserId = createUser()
            val authorizationRequest =
                authorizationService.createAuthorizationRequest(
                    OAuth2AuthorizeInput(
                        responseType = "code",
                        clientId = clientId,
                        redirectUri = "https://runtime.example/callback",
                        scope = "finance:cashflow:read",
                        state = "state-123",
                        codeChallenge = tokenHashing.pkceS256(verifier),
                        codeChallengeMethod = "S256",
                        resource = "https://api.invistus.test",
                    ),
                )
            val requestId = requireNotNull(authorizationRequest.id)
            val (_, csrfToken) = authorizationService.bindUserAndCsrf(requestId, savedUserId)
            val redirect =
                authorizationService.approve(
                    requestId = requestId,
                    userId = savedUserId,
                    csrfToken = csrfToken,
                    grantedScopes = listOf("finance:cashflow:read"),
                )
            Regex("code=([^&]+)").find(redirect)!!.groupValues[1]
        }

    private fun exchangeAuthorizationCode(
        code: String,
        verifier: String,
        clientId: String,
    ) = httpClient.exchangeAsString(
        HttpRequest
            .POST(
                "/oauth2/token",
                mapOf(
                    "grant_type" to "authorization_code",
                    "client_id" to clientId,
                    "redirect_uri" to "https://runtime.example/callback",
                    "code" to code,
                    "code_verifier" to verifier,
                ),
            ).contentType(MediaType.APPLICATION_JSON),
    )

    private fun registerRuntimeClient(): OAuth2ClientRegistrationResponse {
        val response =
            httpClient.exchangeAsString(
                HttpRequest
                    .POST(
                        "/oauth2/register",
                        mapOf(
                            "client_name" to "Runtime Agent",
                            "redirect_uris" to listOf("https://runtime.example/callback"),
                            "scope" to "finance:cashflow:read",
                            "resources" to listOf("https://api.invistus.test"),
                        ),
                    ).contentType(MediaType.APPLICATION_JSON),
            )
        assertThat(response.status()).isEqualTo(HttpStatus.CREATED)
        return json.readValue(response.body(), OAuth2ClientRegistrationResponse::class.java)
    }

    private fun tokenExchangeClientSecret(): String {
        tokenExchangeClientSecret?.let { return it }
        val registration =
            entityManager.runTransaction {
                servicePrincipalService.registerService("mcp-service", listOf("banking:transaction:view"))
            }
        tokenExchangeClientSecret = registration.clientSecret
        return registration.clientSecret
    }

    private fun createOAuthSubjectToken(
        userId: UUID,
        accountId: UUID,
    ): String =
        jwtTokenIssuer.generateOAuthAccessToken(
            userId = userId,
            email = "oauth-$userId@example.com",
            clientId = "test-agent",
            scopes = listOf("finance:cashflow:read"),
            resource = "https://api.invistus.test",
            currentAccountId = accountId,
        )

    private fun exchangeToken(
        subjectToken: String,
        clientSecret: String,
    ) = httpClient.exchangeAsString(
        HttpRequest
            .POST(
                "/oauth2/token",
                mapOf(
                    "grant_type" to OAuth2TokenExchangeService.TOKEN_EXCHANGE_GRANT_TYPE,
                    "client_id" to "mcp-service",
                    "client_secret" to clientSecret,
                    "audience" to "banking",
                    "subject_token" to subjectToken,
                    "subject_token_type" to OAuth2TokenExchangeService.ACCESS_TOKEN_TYPE,
                ),
            ).contentType(MediaType.APPLICATION_JSON),
    )

    private fun exchangeTokenOrFail(
        subjectToken: String,
        clientSecret: String,
        attempt: String,
    ) = try {
        exchangeToken(subjectToken, clientSecret)
    } catch (exception: HttpClientResponseException) {
        val responseBody = exception.response.getBody(String::class.java).orElse("<empty>")
        fail("Token exchange $attempt attempt failed with ${exception.status}: $responseBody")
    }

    private fun createUser(): UUID = createUserAndAccount().first

    private fun createUserAndAccount(): Pair<UUID, UUID> {
        val user =
            userRepository.save(
                UserEntity(
                    email = "oauth-${UUID.randomUUID()}@example.com",
                    displayName = "OAuth User",
                    passwordHash = "unused",
                    emailVerified = true,
                ),
            )
        val savedUserId = requireNotNull(user.id)
        createdUserIds.add(savedUserId)
        principalRepository.save(
            PrincipalEntity(
                principalType = PrincipalType.USER,
                externalId = savedUserId.toString(),
                enabled = true,
            ),
        )
        val account =
            accountRepository.save(
                AccountEntity(
                    accountName = "OAuth Test Account",
                    accountType = AccountType.PERSONAL,
                    ownerUserId = savedUserId,
                ),
            )
        val accountId = requireNotNull(account.id)
        createdAccountIds.add(accountId)
        accountMembershipRepository.save(
            AccountMembershipEntity(
                accountId = accountId,
                userId = savedUserId,
                accountRole = AccountRole.OWNER,
                membershipStatus = MembershipStatus.ACTIVE,
                isDefault = true,
            ),
        )
        return savedUserId to accountId
    }
}
