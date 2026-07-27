package io.github.salomax.neotool.security.test.http

import io.github.salomax.neotool.common.security.jwt.JwtTokenValidator
import io.github.salomax.neotool.common.security.principal.PrincipalType
import io.github.salomax.neotool.common.test.http.exchangeAsString
import io.github.salomax.neotool.common.test.integration.BaseIntegrationTest
import io.github.salomax.neotool.common.test.integration.PostgresIntegrationTest
import io.github.salomax.neotool.common.test.transaction.runTransaction
import io.github.salomax.neotool.security.http.AuthorizationServerMetadataController.AuthorizationServerMetadata
import io.github.salomax.neotool.security.http.dto.OAuth2ClientRegistrationResponse
import io.github.salomax.neotool.security.http.dto.OAuth2TokenResponse
import io.github.salomax.neotool.security.model.OAuth2AuthorizationRequestStatus
import io.github.salomax.neotool.security.model.OAuth2ClientStatus
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
import io.github.salomax.neotool.security.service.jwt.RefreshTokenService
import io.github.salomax.neotool.security.service.oauth2.OAuth2AuthorizationService
import io.github.salomax.neotool.security.service.oauth2.OAuth2AuthorizeInput
import io.github.salomax.neotool.security.service.oauth2.OAuth2SessionService
import io.github.salomax.neotool.security.service.oauth2.OAuth2TokenHashing
import io.micronaut.http.HttpHeaders
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.client.DefaultHttpClientConfiguration
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import jakarta.persistence.EntityManager
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.assertThrows
import java.time.Instant
import java.util.UUID

@MicronautTest(startApplication = true)
@DisplayName("OAuth2 Authorization Server Integration Tests")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Tag("integration")
@Tag("security")
@Tag("http")
open class OAuth2AuthorizationServerIntegrationTest :
    BaseIntegrationTest(),
    PostgresIntegrationTest {
    @Inject
    lateinit var authorizationService: OAuth2AuthorizationService

    @Inject
    lateinit var tokenHashing: OAuth2TokenHashing

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
    lateinit var refreshTokenService: RefreshTokenService

    @Inject
    lateinit var sessionService: OAuth2SessionService

    @Inject
    lateinit var jwtTokenValidator: JwtTokenValidator

    @Inject
    lateinit var embeddedServer: EmbeddedServer

    @Inject
    lateinit var entityManager: EntityManager

    private val createdUserIds = mutableSetOf<UUID>()
    private val createdAccountIds = mutableSetOf<UUID>()

    @AfterEach
    fun cleanupTestData() {
        entityManager.runTransaction {
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
    fun `authorization code and PKCE flow issues valid access token`() {
        val verifier = "correct-horse-battery-staple-0123456789"
        val (userId, code) = createApprovedAuthorizationCode(verifier)
        val response = exchangeAuthorizationCode(code, verifier)

        assertThat(response.status()).isEqualTo(HttpStatus.OK)
        val tokenResponse = json.readValue(response.body(), OAuth2TokenResponse::class.java)
        assertThat(tokenResponse.refresh_token).isNotBlank()
        assertThat(tokenResponse.scope).isEqualTo("finance:cashflow:read")
        assertThat(jwtTokenValidator.getUserIdFromToken(tokenResponse.access_token)).isEqualTo(userId)
        assertThat(jwtTokenValidator.getJwtIdFromToken(tokenResponse.access_token)).isNotBlank()
        assertThat(jwtTokenValidator.getIssuerFromToken(tokenResponse.access_token)).isEqualTo("http://localhost:8080")
        assertThat(jwtTokenValidator.getAudienceFromToken(tokenResponse.access_token))
            .isEqualTo("https://api.invistus.test")
    }

    @Test
    fun `authorization server metadata is public and advertises configured endpoints`() {
        val response =
            httpClient.exchangeAsString(
                HttpRequest.GET<Any>("/.well-known/oauth-authorization-server"),
            )
        val metadata = json.readValue(response.body(), AuthorizationServerMetadata::class.java)

        assertThat(response.status()).isEqualTo(HttpStatus.OK)
        assertThat(response.header("Cache-Control")).isEqualTo("public, max-age=3600")
        assertThat(metadata.issuer).isEqualTo("http://localhost:8080")
        assertThat(metadata.authorization_endpoint).isEqualTo("http://localhost:8080/oauth2/authorize")
        assertThat(metadata.token_endpoint).isEqualTo("http://localhost:8080/oauth2/token")
        assertThat(metadata.jwks_uri).isEqualTo("http://localhost:8080/.well-known/jwks.json")
        assertThat(metadata.scopes_supported)
            .containsExactly(
                "finance:transactions:read",
                "finance:transactions:write",
                "finance:cashflow:read",
                "finance:accounts:read",
                "finance:bills:read",
                "finance:categories:read",
                "finance:categories:write",
                "finance:rules:read",
                "finance:rules:write",
            )
        assertThat(metadata.response_types_supported).containsExactly("code")
        assertThat(metadata.grant_types_supported).containsExactly("authorization_code", "refresh_token")
        assertThat(metadata.token_endpoint_auth_methods_supported).containsExactly("none")
        assertThat(metadata.code_challenge_methods_supported).containsExactly("S256")
        assertThat(metadata.resource_indicators_supported).isTrue()
        assertThat(metadata.registration_endpoint).isEqualTo("http://localhost:8080/oauth2/register")
    }

    @Test
    fun `dynamic client registration returns public client metadata unchanged`() {
        val registration = registerRuntimeClient()

        assertThat(registration.client_id).startsWith("dcr_")
        assertThat(registration.client_id_issued_at).isPositive()
        assertThat(registration.client_name).isEqualTo("Runtime Agent")
        assertThat(registration.redirect_uris).containsExactly("https://runtime.example/callback")
        assertThat(registration.scope).isEqualTo("finance:cashflow:read")
        assertThat(registration.resources).containsExactly("https://api.invistus.test")
        assertThat(registration.grant_types).containsExactly("authorization_code", "refresh_token")
        assertThat(registration.response_types).containsExactly("code")
        assertThat(registration.token_endpoint_auth_method).isEqualTo("none")
    }

    @Test
    fun `dynamic client registration defaults scope and resources when omitted`() {
        val registration =
            registerRuntimeClient(
                mapOf(
                    "client_name" to "ChatGPT Runtime Agent",
                    "redirect_uris" to listOf("https://chatgpt.com/connector/oauth/callback"),
                    "grant_types" to listOf("authorization_code", "refresh_token"),
                    "response_types" to listOf("code"),
                    "token_endpoint_auth_method" to "none",
                ),
            )

        assertThat(registration.client_id).startsWith("dcr_")
        assertThat(registration.client_name).isEqualTo("ChatGPT Runtime Agent")
        assertThat(registration.redirect_uris)
            .containsExactly("https://chatgpt.com/connector/oauth/callback")
        assertThat(registration.scope)
            .isEqualTo(
                "finance:transactions:read finance:transactions:write finance:cashflow:read " +
                    "finance:accounts:read finance:bills:read finance:categories:read " +
                    "finance:categories:write finance:rules:read finance:rules:write",
            )
        assertThat(registration.resources).containsExactly("https://api.invistus.test")
        assertThat(registration.grant_types).containsExactly("authorization_code", "refresh_token")
        assertThat(registration.response_types).containsExactly("code")
        assertThat(registration.token_endpoint_auth_method).isEqualTo("none")
    }

    @Test
    fun `client registered without explicit scope or resources is usable at authorize endpoint`() {
        val registration =
            registerRuntimeClient(
                mapOf(
                    "client_name" to "ChatGPT Runtime Agent",
                    "redirect_uris" to listOf("https://chatgpt.com/connector/oauth/callback"),
                    "grant_types" to listOf("authorization_code", "refresh_token"),
                    "response_types" to listOf("code"),
                    "token_endpoint_auth_method" to "none",
                ),
            )

        val response =
            noRedirectClient().use { client ->
                client.toBlocking().exchange(
                    HttpRequest.GET<Any>(
                        "/oauth2/authorize" +
                            "?response_type=code" +
                            "&client_id=${registration.client_id}" +
                            "&redirect_uri=https://chatgpt.com/connector/oauth/callback" +
                            "&scope=finance:cashflow:read" +
                            "&code_challenge=valid-s256-challenge" +
                            "&code_challenge_method=S256" +
                            "&resource=https://api.invistus.test",
                    ),
                    String::class.java,
                )
            }

        assertThat(response.status()).isEqualTo(HttpStatus.SEE_OTHER)
        assertThat(response.headers[HttpHeaders.LOCATION]).startsWith("/oauth2/login?request_id=")
    }

    @Test
    fun `dynamically registered client is immediately usable at authorize endpoint`() {
        val registration = registerRuntimeClient()

        val response =
            noRedirectClient().use { client ->
                client.toBlocking().exchange(
                    HttpRequest.GET<Any>(
                        "/oauth2/authorize" +
                            "?response_type=code" +
                            "&client_id=${registration.client_id}" +
                            "&redirect_uri=https://runtime.example/callback" +
                            "&scope=finance:cashflow:read" +
                            "&code_challenge=valid-s256-challenge" +
                            "&code_challenge_method=S256" +
                            "&resource=https://api.invistus.test",
                    ),
                    String::class.java,
                )
            }

        assertThat(response.status()).isEqualTo(HttpStatus.SEE_OTHER)
        assertThat(response.headers[HttpHeaders.LOCATION]).startsWith("/oauth2/login?request_id=")
    }

    @Test
    fun `garbage collected dynamic client is rejected at authorize endpoint`() {
        val registration = registerRuntimeClient()
        entityManager.runTransaction {
            val client = oauth2ClientRepository.findById(registration.client_id).orElseThrow()
            client.status = OAuth2ClientStatus.DELETED
            client.updatedAt = Instant.now()
            oauth2ClientRepository.update(client)
        }

        val exception =
            assertThrows<HttpClientResponseException> {
                httpClient.exchangeAsString(
                    HttpRequest.GET<Any>(
                        "/oauth2/authorize" +
                            "?response_type=code" +
                            "&client_id=${registration.client_id}" +
                            "&redirect_uri=https://runtime.example/callback" +
                            "&scope=finance:cashflow:read" +
                            "&code_challenge=valid-s256-challenge" +
                            "&code_challenge_method=S256" +
                            "&resource=https://api.invistus.test",
                    ),
                )
            }

        assertThat(exception.status).isEqualTo(HttpStatus.BAD_REQUEST)
    }

    @Test
    fun `metadata issuer matches issued token issuer`() {
        val verifier = "correct-horse-battery-staple-0123456789"
        val (_, code) = createApprovedAuthorizationCode(verifier)
        val tokenResponse =
            json.readValue(
                exchangeAuthorizationCode(code, verifier).body(),
                OAuth2TokenResponse::class.java,
            )
        val metadataResponse =
            httpClient.exchangeAsString(
                HttpRequest.GET<Any>("/.well-known/oauth-authorization-server"),
            )
        val metadata = json.readValue(metadataResponse.body(), AuthorizationServerMetadata::class.java)

        assertThat(metadata.issuer).isEqualTo(jwtTokenValidator.getIssuerFromToken(tokenResponse.access_token))
    }

    @Test
    fun `token audience is bound to authorization request resource`() {
        val verifier = "correct-horse-battery-staple-0123456789"
        val (_, code) = createApprovedAuthorizationCode(verifier, resource = "https://api.invistus.test")
        val response =
            postToken(
                authorizationCodeTokenRequest(code, verifier) +
                    ("resource" to "https://other-resource.example"),
            )
        val tokenResponse = json.readValue(response.body(), OAuth2TokenResponse::class.java)

        assertThat(jwtTokenValidator.getAudienceFromToken(tokenResponse.access_token))
            .isEqualTo("https://api.invistus.test")
    }

    @Test
    fun `authorization code replay revokes issued refresh token family`() {
        val verifier = "correct-horse-battery-staple-0123456789"
        val (_, code) = createApprovedAuthorizationCode(verifier)
        val tokenResponse =
            json.readValue(
                exchangeAuthorizationCode(code, verifier).body(),
                OAuth2TokenResponse::class.java,
            )
        val refreshToken = requireNotNull(tokenResponse.refresh_token)
        val tokenRecord =
            refreshTokenRepository.findByTokenHash(tokenHashing.sha256Hex(refreshToken))
        val familyId = requireNotNull(tokenRecord?.familyId)

        val exception =
            assertThrows<HttpClientResponseException> {
                exchangeAuthorizationCodeRaw(code, verifier)
            }

        assertThat(exception.status).isEqualTo(HttpStatus.BAD_REQUEST)
        entityManager.clear()
        assertThat(refreshTokenRepository.findByFamilyId(familyId))
            .allSatisfy { refreshToken -> assertThat(refreshToken.revokedAt).isNotNull() }
    }

    @Test
    fun `OAuth refresh token preserves granted scope`() {
        val verifier = "correct-horse-battery-staple-0123456789"
        val (_, code) = createApprovedAuthorizationCode(verifier)
        val firstTokenResponse =
            json.readValue(
                exchangeAuthorizationCode(code, verifier).body(),
                OAuth2TokenResponse::class.java,
            )
        val firstRefreshToken = requireNotNull(firstTokenResponse.refresh_token)
        val originalTokenRecord =
            refreshTokenRepository.findByTokenHash(tokenHashing.sha256Hex(firstRefreshToken))
        val familyId = requireNotNull(originalTokenRecord?.familyId)

        val refreshResponse =
            httpClient.exchangeAsString(
                HttpRequest
                    .POST(
                        "/oauth2/token",
                        mapOf(
                            "grant_type" to "refresh_token",
                            "refresh_token" to firstRefreshToken,
                        ),
                    ).contentType(MediaType.APPLICATION_JSON),
            )
        val refreshedTokenResponse = json.readValue(refreshResponse.body(), OAuth2TokenResponse::class.java)

        assertThat(refreshResponse.status()).isEqualTo(HttpStatus.OK)
        assertThat(refreshedTokenResponse.refresh_token).isNotEqualTo(firstRefreshToken)
        assertThat(refreshedTokenResponse.scope).isEqualTo("finance:cashflow:read")

        val reuseException =
            assertThrows<HttpClientResponseException> {
                httpClient.exchangeAsString(
                    HttpRequest
                        .POST(
                            "/oauth2/token",
                            mapOf(
                                "grant_type" to "refresh_token",
                                "refresh_token" to firstRefreshToken,
                            ),
                        ).contentType(MediaType.APPLICATION_JSON),
                )
            }

        assertThat(reuseException.status).isEqualTo(HttpStatus.BAD_REQUEST)
        entityManager.clear()
        assertThat(refreshTokenRepository.findByFamilyId(familyId))
            .allSatisfy { refreshToken -> assertThat(refreshToken.revokedAt).isNotNull() }
    }

    @Test
    fun `authorize rejects plain PKCE method with invalid_request`() {
        val exception =
            assertThrows<HttpClientResponseException> {
                httpClient.exchangeAsString(
                    HttpRequest.GET<Any>(
                        "/oauth2/authorize" +
                            "?response_type=code" +
                            "&client_id=test-agent" +
                            "&redirect_uri=http://client.example/callback" +
                            "&scope=finance:cashflow:read" +
                            "&code_challenge=plain-challenge" +
                            "&code_challenge_method=plain" +
                            "&resource=https://api.invistus.test",
                    ),
                )
            }

        assertThat(exception.status).isEqualTo(HttpStatus.BAD_REQUEST)
    }

    @Test
    fun `consent form preserves repeated scope values`() {
        val verifier = "correct-horse-battery-staple-0123456789"
        val (userId, requestId, csrfToken) =
            createPendingAuthorizationRequest(
                verifier = verifier,
                scope = "finance:cashflow:read finance:transactions:read",
            )
        val sessionCookie = "${sessionService.cookieName()}=${sessionService.createSessionCookie(userId).value}"
        val consentResponse =
            noRedirectClient().use { client ->
                client.toBlocking().exchange(
                    HttpRequest
                        .POST(
                            "/oauth2/consent",
                            "request_id=$requestId" +
                                "&csrf_token=$csrfToken" +
                                "&scope=finance%3Acashflow%3Aread" +
                                "&scope=finance%3Atransactions%3Aread",
                        ).contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .header(HttpHeaders.COOKIE, sessionCookie),
                    String::class.java,
                )
            }

        assertThat(consentResponse.status()).isEqualTo(HttpStatus.SEE_OTHER)
        val code =
            requireNotNull(
                Regex("code=([^&]+)").find(
                    requireNotNull(consentResponse.headers[HttpHeaders.LOCATION]),
                ),
            ).groupValues[1]
        val tokenResponse =
            json.readValue(
                exchangeAuthorizationCode(code, verifier).body(),
                OAuth2TokenResponse::class.java,
            )

        assertThat(tokenResponse.scope).isEqualTo("finance:cashflow:read finance:transactions:read")
        assertThat(jwtTokenValidator.validateToken(tokenResponse.access_token)["scope"])
            .isEqualTo("finance:cashflow:read finance:transactions:read")
        val consent = oauth2ConsentRepository.findAll().single()
        assertThat(consent.userId).isEqualTo(userId)
        assertThat(consent.clientId).isEqualTo("test-agent")
        assertThat(consent.grantedScopes).isEqualTo("finance:cashflow:read finance:transactions:read")
        assertThat(consent.revokedAt).isNull()
    }

    @Test
    fun `deselected consent scope is omitted from token and persisted grant`() {
        val verifier = "correct-horse-battery-staple-0123456789"
        val (userId, requestId, csrfToken) =
            createPendingAuthorizationRequest(
                verifier = verifier,
                scope = "finance:cashflow:read finance:transactions:read",
            )
        val sessionCookie = "${sessionService.cookieName()}=${sessionService.createSessionCookie(userId).value}"
        val consentResponse =
            noRedirectClient().use { client ->
                client.toBlocking().exchange(
                    HttpRequest
                        .POST(
                            "/oauth2/consent",
                            "request_id=$requestId" +
                                "&csrf_token=$csrfToken" +
                                "&scope=finance%3Acashflow%3Aread",
                        ).contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .header(HttpHeaders.COOKIE, sessionCookie),
                    String::class.java,
                )
            }

        assertThat(consentResponse.status()).isEqualTo(HttpStatus.SEE_OTHER)
        val code =
            requireNotNull(
                Regex("code=([^&]+)").find(
                    requireNotNull(consentResponse.headers[HttpHeaders.LOCATION]),
                ),
            ).groupValues[1]
        val tokenResponse =
            json.readValue(
                exchangeAuthorizationCode(code, verifier).body(),
                OAuth2TokenResponse::class.java,
            )

        assertThat(tokenResponse.scope).isEqualTo("finance:cashflow:read")
        assertThat(jwtTokenValidator.validateToken(tokenResponse.access_token)["scope"])
            .isEqualTo("finance:cashflow:read")
        val consent = oauth2ConsentRepository.findAll().single()
        assertThat(consent.userId).isEqualTo(userId)
        assertThat(consent.grantedScopes).isEqualTo("finance:cashflow:read")
    }

    @Test
    fun `explicit deny redirects access denied to client`() {
        val verifier = "correct-horse-battery-staple-0123456789"
        val (userId, requestId, csrfToken) =
            createPendingAuthorizationRequest(
                verifier = verifier,
                scope = "finance:cashflow:read",
            )
        val sessionCookie = "${sessionService.cookieName()}=${sessionService.createSessionCookie(userId).value}"
        val consentResponse =
            noRedirectClient().use { client ->
                client.toBlocking().exchange(
                    HttpRequest
                        .POST(
                            "/oauth2/consent",
                            "request_id=$requestId" +
                                "&csrf_token=$csrfToken" +
                                "&decision=deny",
                        ).contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .header(HttpHeaders.COOKIE, sessionCookie),
                    String::class.java,
                )
            }

        assertThat(consentResponse.status()).isEqualTo(HttpStatus.SEE_OTHER)
        assertThat(consentResponse.headers[HttpHeaders.LOCATION])
            .isEqualTo("http://client.example/callback?error=access_denied&state=state-123")
        assertThat(oauth2ConsentRepository.findAll()).isEmpty()
        entityManager.clear()
        assertThat(authorizationRequestRepository.findById(requestId).orElseThrow().status)
            .isEqualTo(OAuth2AuthorizationRequestStatus.DENIED)
    }

    @Test
    fun `declining newly requested delta proceeds with existing consent`() {
        val firstVerifier = "correct-horse-battery-staple-0123456789"
        val (userId, _) = createApprovedAuthorizationCode(firstVerifier)
        val secondVerifier = "another-correct-horse-battery-staple-0123456789"
        val (_, requestId, csrfToken) =
            createPendingAuthorizationRequest(
                verifier = secondVerifier,
                scope = "finance:cashflow:read finance:transactions:read",
                userId = userId,
            )
        val sessionCookie = "${sessionService.cookieName()}=${sessionService.createSessionCookie(userId).value}"
        val consentResponse =
            noRedirectClient().use { client ->
                client.toBlocking().exchange(
                    HttpRequest
                        .POST(
                            "/oauth2/consent",
                            "request_id=$requestId" +
                                "&csrf_token=$csrfToken" +
                                "&decision=allow",
                        ).contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .header(HttpHeaders.COOKIE, sessionCookie),
                    String::class.java,
                )
            }

        assertThat(consentResponse.status()).isEqualTo(HttpStatus.SEE_OTHER)
        val code =
            requireNotNull(
                Regex("code=([^&]+)").find(
                    requireNotNull(consentResponse.headers[HttpHeaders.LOCATION]),
                ),
            ).groupValues[1]
        val tokenResponse =
            json.readValue(
                exchangeAuthorizationCode(code, secondVerifier).body(),
                OAuth2TokenResponse::class.java,
            )

        assertThat(tokenResponse.scope).isEqualTo("finance:cashflow:read")
        assertThat(jwtTokenValidator.validateToken(tokenResponse.access_token)["scope"])
            .isEqualTo("finance:cashflow:read")
        assertThat(oauth2ConsentRepository.findAll())
            .singleElement()
            .extracting<String> { it.grantedScopes }
            .isEqualTo("finance:cashflow:read")
    }

    @Test
    fun `token endpoint rejects missing code verifier`() {
        val verifier = "correct-horse-battery-staple-0123456789"
        val (_, code) = createApprovedAuthorizationCode(verifier)

        val exception =
            assertThrows<HttpClientResponseException> {
                postToken(
                    mapOf(
                        "grant_type" to "authorization_code",
                        "client_id" to "test-agent",
                        "redirect_uri" to "http://client.example/callback",
                        "code" to code,
                    ),
                )
            }

        assertThat(exception.status).isEqualTo(HttpStatus.BAD_REQUEST)
    }

    @Test
    fun `token endpoint rejects wrong code verifier`() {
        val verifier = "correct-horse-battery-staple-0123456789"
        val (_, code) = createApprovedAuthorizationCode(verifier)

        val exception =
            assertThrows<HttpClientResponseException> {
                exchangeAuthorizationCodeRaw(code, "wrong-horse-battery-staple-0123456789")
            }

        assertThat(exception.status).isEqualTo(HttpStatus.BAD_REQUEST)
    }

    @Test
    fun `token endpoint rejects client id mismatch`() {
        val verifier = "correct-horse-battery-staple-0123456789"
        val (_, code) = createApprovedAuthorizationCode(verifier)

        val exception =
            assertThrows<HttpClientResponseException> {
                postToken(
                    authorizationCodeTokenRequest(code, verifier) +
                        ("client_id" to "other-agent"),
                )
            }

        assertThat(exception.status).isEqualTo(HttpStatus.BAD_REQUEST)
    }

    @Test
    fun `token endpoint rejects redirect uri mismatch`() {
        val verifier = "correct-horse-battery-staple-0123456789"
        val (_, code) = createApprovedAuthorizationCode(verifier)

        val exception =
            assertThrows<HttpClientResponseException> {
                postToken(
                    authorizationCodeTokenRequest(code, verifier) +
                        ("redirect_uri" to "http://client.example/other"),
                )
            }

        assertThat(exception.status).isEqualTo(HttpStatus.BAD_REQUEST)
    }

    @Test
    fun `token endpoint rejects expired authorization code`() {
        val verifier = "correct-horse-battery-staple-0123456789"
        val (_, code) = createApprovedAuthorizationCode(verifier)
        entityManager.runTransaction {
            val codeRecord = authorizationCodeRepository.findByCodeHash(tokenHashing.sha256Hex(code))!!
            codeRecord.expiresAt = Instant.now().minusSeconds(1)
            codeRecord.updatedAt = Instant.now()
            authorizationCodeRepository.update(codeRecord)
        }

        val exception =
            assertThrows<HttpClientResponseException> {
                exchangeAuthorizationCodeRaw(code, verifier)
            }

        assertThat(exception.status).isEqualTo(HttpStatus.BAD_REQUEST)
    }

    @Test
    fun `token endpoint rejects first party refresh token`() {
        val userId = createUser()
        val firstPartyRefreshToken =
            entityManager.runTransaction {
                refreshTokenService.createRefreshToken(userId)
            }

        val exception =
            assertThrows<HttpClientResponseException> {
                postToken(
                    mapOf(
                        "grant_type" to "refresh_token",
                        "refresh_token" to firstPartyRefreshToken,
                    ),
                )
            }

        assertThat(exception.status).isEqualTo(HttpStatus.BAD_REQUEST)
    }

    private fun createApprovedAuthorizationCode(
        verifier: String,
        resource: String = "https://api.invistus.test",
    ): Pair<UUID, String> =
        entityManager.runTransaction {
            val savedUserId = createUser()
            val authorizationRequest =
                authorizationService.createAuthorizationRequest(
                    OAuth2AuthorizeInput(
                        responseType = "code",
                        clientId = "test-agent",
                        redirectUri = "http://client.example/callback",
                        scope = "finance:cashflow:read",
                        state = "state-123",
                        codeChallenge = tokenHashing.pkceS256(verifier),
                        codeChallengeMethod = "S256",
                        resource = resource,
                    ),
                )
            val requestId = requireNotNull(authorizationRequest.id)
            val (_, csrf) = authorizationService.bindUserAndCsrf(requestId, savedUserId)
            val redirect =
                authorizationService.approve(
                    requestId = requestId,
                    userId = savedUserId,
                    csrfToken = csrf,
                    grantedScopes = listOf("finance:cashflow:read"),
                )
            savedUserId to Regex("code=([^&]+)").find(redirect)!!.groupValues[1]
        }

    private fun createPendingAuthorizationRequest(
        verifier: String,
        scope: String,
        userId: UUID? = null,
    ): Triple<UUID, UUID, String> =
        entityManager.runTransaction {
            val savedUserId = userId ?: createUser()
            val authorizationRequest =
                authorizationService.createAuthorizationRequest(
                    OAuth2AuthorizeInput(
                        responseType = "code",
                        clientId = "test-agent",
                        redirectUri = "http://client.example/callback",
                        scope = scope,
                        state = "state-123",
                        codeChallenge = tokenHashing.pkceS256(verifier),
                        codeChallengeMethod = "S256",
                        resource = "https://api.invistus.test",
                    ),
                )
            val requestId = requireNotNull(authorizationRequest.id)
            val (_, csrfToken) = authorizationService.bindUserAndCsrf(requestId, savedUserId)
            Triple(savedUserId, requestId, csrfToken)
        }

    private fun exchangeAuthorizationCode(
        code: String,
        verifier: String,
    ) = try {
        exchangeAuthorizationCodeRaw(code, verifier)
    } catch (e: HttpClientResponseException) {
        val body = e.response.getBody(String::class.java).orElse("")
        throw AssertionError("Token endpoint failed with ${e.status}: $body", e)
    }

    private fun exchangeAuthorizationCodeRaw(
        code: String,
        verifier: String,
    ) = postToken(authorizationCodeTokenRequest(code, verifier))

    private fun authorizationCodeTokenRequest(
        code: String,
        verifier: String,
    ): Map<String, String> =
        mapOf(
            "grant_type" to "authorization_code",
            "client_id" to "test-agent",
            "redirect_uri" to "http://client.example/callback",
            "code" to code,
            "code_verifier" to verifier,
        )

    private fun postToken(body: Map<String, String>) =
        httpClient.exchangeAsString(
            HttpRequest
                .POST(
                    "/oauth2/token",
                    body,
                ).contentType(MediaType.APPLICATION_JSON),
        )

    private fun registerRuntimeClient(
        body: Map<String, Any> =
            mapOf(
                "client_name" to "Runtime Agent",
                "redirect_uris" to listOf("https://runtime.example/callback"),
                "scope" to "finance:cashflow:read",
                "resources" to listOf("https://api.invistus.test"),
            ),
    ): OAuth2ClientRegistrationResponse {
        val response =
            httpClient.exchangeAsString(
                HttpRequest
                    .POST(
                        "/oauth2/register",
                        body,
                    ).contentType(MediaType.APPLICATION_JSON),
            )
        assertThat(response.status()).isEqualTo(HttpStatus.CREATED)
        return json.readValue(response.body(), OAuth2ClientRegistrationResponse::class.java)
    }

    private fun createUser(): UUID {
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
        return savedUserId
    }

    private fun noRedirectClient(): HttpClient {
        val config =
            DefaultHttpClientConfiguration().apply {
                setFollowRedirects(false)
            }
        @Suppress("DEPRECATION")
        return HttpClient.create(embeddedServer.url, config)
    }
}
