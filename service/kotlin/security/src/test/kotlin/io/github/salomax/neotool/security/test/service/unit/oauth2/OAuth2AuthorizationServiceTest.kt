package io.github.salomax.neotool.security.test.service.unit.oauth2

import io.github.salomax.neotool.security.config.OAuth2ClientConfig
import io.github.salomax.neotool.security.config.OAuthConfig
import io.github.salomax.neotool.security.model.OAuth2AuthorizationRequestEntity
import io.github.salomax.neotool.security.model.OAuth2AuthorizationRequestStatus
import io.github.salomax.neotool.security.repo.OAuth2AuthorizationCodeRepository
import io.github.salomax.neotool.security.repo.OAuth2AuthorizationRequestRepository
import io.github.salomax.neotool.security.repo.OAuth2ClientRepository
import io.github.salomax.neotool.security.service.oauth2.OAuth2AuthorizationService
import io.github.salomax.neotool.security.service.oauth2.OAuth2AuthorizeInput
import io.github.salomax.neotool.security.service.oauth2.OAuth2ClientMetadataCodec
import io.github.salomax.neotool.security.service.oauth2.OAuth2ClientRegistry
import io.github.salomax.neotool.security.service.oauth2.OAuth2InvalidRequestException
import io.github.salomax.neotool.security.service.oauth2.OAuth2TokenHashing
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.Instant
import java.util.UUID

@DisplayName("OAuth2AuthorizationService Unit Tests")
class OAuth2AuthorizationServiceTest {
    private lateinit var requestRepository: OAuth2AuthorizationRequestRepository
    private lateinit var codeRepository: OAuth2AuthorizationCodeRepository
    private lateinit var clientRepository: OAuth2ClientRepository
    private lateinit var service: OAuth2AuthorizationService
    private val tokenHashing = OAuth2TokenHashing()
    private val clientMetadataCodec = OAuth2ClientMetadataCodec()

    @BeforeEach
    fun setUp() {
        requestRepository = mock()
        codeRepository = mock()
        clientRepository = mock()
        val oauthConfig =
            OAuthConfig(
                authorizationServer =
                    OAuthConfig.AuthorizationServerConfig(),
            )
        val clientConfig =
            OAuth2ClientConfig().apply {
                clientId = "test-agent"
                clientName = "Test Agent"
                redirectUris = listOf("http://client.example/callback")
                scopes = listOf("finance:cashflow:read", "finance:transactions:read")
                resources = listOf("https://api.invistus.test")
            }
        whenever(requestRepository.save(any())).thenAnswer { invocation -> invocation.arguments[0] }
        whenever(requestRepository.update(any())).thenAnswer { invocation -> invocation.arguments[0] }
        whenever(codeRepository.save(any())).thenAnswer { invocation -> invocation.arguments[0] }
        service =
            OAuth2AuthorizationService(
                OAuth2ClientRegistry(listOf(clientConfig), clientRepository, clientMetadataCodec),
                requestRepository,
                codeRepository,
                tokenHashing,
                oauthConfig,
            )
    }

    @Test
    fun `should reject plain PKCE method at authorize step`() {
        val exception =
            assertThrows<OAuth2InvalidRequestException> {
                service.createAuthorizationRequest(validInput(codeChallengeMethod = "plain"))
            }

        assertThat(exception.error).isEqualTo("invalid_request")
    }

    @Test
    fun `should reject unknown client`() {
        assertThrows<OAuth2InvalidRequestException> {
            service.createAuthorizationRequest(validInput(clientId = "unknown-agent"))
        }
    }

    @Test
    fun `should reject missing challenge`() {
        assertThrows<OAuth2InvalidRequestException> {
            service.createAuthorizationRequest(validInput(codeChallenge = null))
        }
    }

    @Test
    fun `should reject redirect uri mismatch`() {
        assertThrows<OAuth2InvalidRequestException> {
            service.createAuthorizationRequest(validInput(redirectUri = "http://client.example/other"))
        }
    }

    @Test
    fun `should reject unknown scope`() {
        assertThrows<OAuth2InvalidRequestException> {
            service.createAuthorizationRequest(validInput(scope = "finance:cashflow:read finance:unknown:read"))
        }
    }

    @Test
    fun `should reject missing resource`() {
        assertThrows<OAuth2InvalidRequestException> {
            service.createAuthorizationRequest(validInput(resource = null))
        }
    }

    @Test
    fun `should reject consent scope widening and invalid csrf`() {
        val requestId = UUID.randomUUID()
        val userId = UUID.randomUUID()
        val csrf = "csrf-token"
        whenever(requestRepository.findByIdAndStatus(requestId, OAuth2AuthorizationRequestStatus.PENDING))
            .thenReturn(
                OAuth2AuthorizationRequestEntity(
                    id = requestId,
                    clientId = "test-agent",
                    redirectUri = "http://client.example/callback",
                    requestedScopes = "finance:cashflow:read",
                    codeChallenge = "challenge",
                    resource = "https://api.invistus.test",
                    userId = userId,
                    csrfTokenHash = tokenHashing.sha256Hex(csrf),
                    expiresAt = Instant.now().plusSeconds(60),
                ),
            )

        assertThrows<OAuth2InvalidRequestException> {
            service.approve(requestId, userId, "wrong", listOf("finance:cashflow:read"))
        }
        assertThrows<OAuth2InvalidRequestException> {
            service.approve(requestId, userId, csrf, listOf("finance:transactions:read"))
        }
    }

    @Test
    fun `should reject expired authorization request on consent approval`() {
        val requestId = UUID.randomUUID()
        val userId = UUID.randomUUID()
        whenever(requestRepository.findByIdAndStatus(requestId, OAuth2AuthorizationRequestStatus.PENDING))
            .thenReturn(
                OAuth2AuthorizationRequestEntity(
                    id = requestId,
                    clientId = "test-agent",
                    redirectUri = "http://client.example/callback",
                    requestedScopes = "finance:cashflow:read",
                    codeChallenge = "challenge",
                    resource = "https://api.invistus.test",
                    userId = userId,
                    csrfTokenHash = tokenHashing.sha256Hex("csrf-token"),
                    expiresAt = Instant.now().minusSeconds(1),
                ),
            )

        assertThrows<OAuth2InvalidRequestException> {
            service.approve(requestId, userId, "csrf-token", listOf("finance:cashflow:read"))
        }
    }

    private fun validInput(
        clientId: String? = "test-agent",
        redirectUri: String? = "http://client.example/callback",
        scope: String? = "finance:cashflow:read",
        codeChallenge: String? = "challenge",
        codeChallengeMethod: String? = "S256",
        resource: String? = "https://api.invistus.test",
    ): OAuth2AuthorizeInput =
        OAuth2AuthorizeInput(
            responseType = "code",
            clientId = clientId,
            redirectUri = redirectUri,
            scope = scope,
            state = "state",
            codeChallenge = codeChallenge,
            codeChallengeMethod = codeChallengeMethod,
            resource = resource,
        )
}
