package io.github.salomax.neotool.security.test.service.unit.oauth2

import io.github.salomax.neotool.security.config.OAuth2ClientConfig
import io.github.salomax.neotool.security.config.OAuthConfig
import io.github.salomax.neotool.security.http.dto.OAuth2ClientRegistrationRequest
import io.github.salomax.neotool.security.repo.OAuth2ClientRepository
import io.github.salomax.neotool.security.service.oauth2.OAuth2ClientMetadataCodec
import io.github.salomax.neotool.security.service.oauth2.OAuth2ClientRegistrationService
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

@DisplayName("OAuth2ClientRegistrationService Unit Tests")
class OAuth2ClientRegistrationServiceTest {
    private lateinit var repository: OAuth2ClientRepository
    private lateinit var service: OAuth2ClientRegistrationService
    private val codec = OAuth2ClientMetadataCodec()

    @BeforeEach
    fun setUp() {
        repository = mock()
        whenever(repository.save(any())).thenAnswer { invocation -> invocation.arguments[0] }
        service =
            OAuth2ClientRegistrationService(
                repository,
                codec,
                OAuth2TokenHashing(),
                OAuthConfig(
                    authorizationServer =
                        OAuthConfig.AuthorizationServerConfig(
                            metadata =
                                OAuthConfig.AuthorizationServerConfig.MetadataConfig(
                                    supportedResources = listOf("https://api.invistus.test"),
                                ),
                        ),
                ),
            )
    }

    @Test
    fun `register creates public client and echoes registered metadata`() {
        val response = service.register(validRequest(), "127.0.0.1")

        assertThat(response.client_id).startsWith("dcr_")
        assertThat(response.client_id_issued_at).isPositive()
        assertThat(response.client_name).isEqualTo("Runtime Agent")
        assertThat(response.redirect_uris).containsExactly("https://client.example/callback")
        assertThat(response.scope).isEqualTo("finance:cashflow:read finance:transactions:read")
        assertThat(response.resources).containsExactly("https://api.invistus.test")
        assertThat(response.grant_types).containsExactly("authorization_code", "refresh_token")
        assertThat(response.response_types).containsExactly("code")
        assertThat(response.token_endpoint_auth_method).isEqualTo("none")
    }

    @Test
    fun `register rejects non-https redirect outside localhost`() {
        assertThrows<OAuth2InvalidRequestException> {
            service.register(validRequest(redirectUris = listOf("http://client.example/callback")), "127.0.0.1")
        }
    }

    @Test
    fun `register allows localhost http redirect`() {
        val response =
            service.register(
                validRequest(redirectUris = listOf("http://localhost:4321/callback")),
                "127.0.0.1",
            )

        assertThat(response.redirect_uris).containsExactly("http://localhost:4321/callback")
    }

    @Test
    fun `register rejects malformed redirect unknown scope unknown resource and missing required fields`() {
        assertThrows<OAuth2InvalidRequestException> {
            service.register(validRequest(redirectUris = listOf("not a uri")), "127.0.0.1")
        }
        assertThrows<OAuth2InvalidRequestException> {
            service.register(validRequest(scope = "finance:unknown:read"), "127.0.0.1")
        }
        assertThrows<OAuth2InvalidRequestException> {
            service.register(validRequest(resources = listOf("https://unknown.invistus.test")), "127.0.0.1")
        }
        assertThrows<OAuth2InvalidRequestException> {
            service.register(validRequest(redirectUris = emptyList()), "127.0.0.1")
        }
    }

    @Test
    fun `register rejects confidential clients in v1`() {
        assertThrows<OAuth2InvalidRequestException> {
            service.register(validRequest(tokenEndpointAuthMethod = "client_secret_basic"), "127.0.0.1")
        }
    }

    @Test
    fun `register rejects unsafe client names`() {
        assertThrows<OAuth2InvalidRequestException> {
            service.register(validRequest(clientName = "Invistus Official\nAllow access"), "127.0.0.1")
        }
        assertThrows<OAuth2InvalidRequestException> {
            service.register(validRequest(clientName = "A".repeat(81)), "127.0.0.1")
        }
    }

    private fun validRequest(
        redirectUris: List<String> = listOf("https://client.example/callback"),
        scope: String = "finance:cashflow:read finance:transactions:read",
        resources: List<String> = listOf("https://api.invistus.test"),
        tokenEndpointAuthMethod: String? = null,
        clientName: String = "Runtime Agent",
    ): OAuth2ClientRegistrationRequest =
        OAuth2ClientRegistrationRequest(
            client_name = clientName,
            redirect_uris = redirectUris,
            scope = scope,
            resources = resources,
            token_endpoint_auth_method = tokenEndpointAuthMethod,
        )

    private fun configClient(): OAuth2ClientConfig =
        OAuth2ClientConfig().apply {
            clientId = "test-agent"
            clientName = "Test Agent"
            redirectUris = listOf("http://client.example/callback")
            scopes = listOf("finance:cashflow:read", "finance:transactions:read")
            resources = listOf("https://api.invistus.test")
        }
}
