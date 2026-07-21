package io.github.salomax.neotool.security.test.service.unit.oauth2

import io.github.salomax.neotool.security.config.OAuth2ClientConfig
import io.github.salomax.neotool.security.model.OAuth2ClientEntity
import io.github.salomax.neotool.security.model.OAuth2ClientStatus
import io.github.salomax.neotool.security.repo.OAuth2ClientRepository
import io.github.salomax.neotool.security.service.oauth2.OAuth2ClientMetadataCodec
import io.github.salomax.neotool.security.service.oauth2.OAuth2ClientRegistry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.Instant

@DisplayName("OAuth2ClientRegistry Unit Tests")
class OAuth2ClientRegistryTest {
    private val repository: OAuth2ClientRepository = mock()
    private val codec = OAuth2ClientMetadataCodec()

    @Test
    fun `find resolves configured clients`() {
        val registry = OAuth2ClientRegistry(listOf(configClient()), repository, codec)

        val client = registry.find("test-agent")

        assertThat(client?.clientName).isEqualTo("Test Agent")
        assertThat(client?.redirectUris).containsExactly("http://client.example/callback")
        assertThat(client?.scopes).containsExactly("finance:cashflow:read")
        assertThat(client?.resources).containsExactly("https://api.invistus.test")
    }

    @Test
    fun `database client shadows configured client with the same id`() {
        whenever(repository.findByIdAndStatus("test-agent", OAuth2ClientStatus.ACTIVE))
            .thenReturn(
                OAuth2ClientEntity(
                    id = "test-agent",
                    clientName = "Runtime Agent",
                    redirectUris = codec.encodeList(listOf("https://runtime.example/callback")),
                    scopes = codec.encodeList(listOf("finance:transactions:read")),
                    resources = codec.encodeList(listOf("https://runtime.invistus.test")),
                    grantTypes = codec.encodeList(listOf("authorization_code", "refresh_token")),
                    responseTypes = codec.encodeList(listOf("code")),
                    tokenEndpointAuthMethod = "none",
                    clientIdIssuedAt = Instant.now(),
                ),
            )
        val registry = OAuth2ClientRegistry(listOf(configClient()), repository, codec)

        val client = registry.find("test-agent")

        assertThat(client?.clientName).isEqualTo("Runtime Agent")
        assertThat(client?.redirectUris).containsExactly("https://runtime.example/callback")
        assertThat(client?.scopes).containsExactly("finance:transactions:read")
        assertThat(client?.resources).containsExactly("https://runtime.invistus.test")
    }

    private fun configClient(): OAuth2ClientConfig =
        OAuth2ClientConfig().apply {
            clientId = "test-agent"
            clientName = "Test Agent"
            redirectUris = listOf("http://client.example/callback")
            scopes = listOf("finance:cashflow:read")
            resources = listOf("https://api.invistus.test")
        }
}
