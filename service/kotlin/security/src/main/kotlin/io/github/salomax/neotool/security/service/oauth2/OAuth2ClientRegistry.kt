package io.github.salomax.neotool.security.service.oauth2

import io.github.salomax.neotool.security.config.OAuth2ClientConfig
import jakarta.inject.Singleton

data class OAuth2RegisteredClient(
    val clientId: String,
    val clientName: String,
    val redirectUris: Set<String>,
    val scopes: Set<String>,
    val resources: Set<String>,
)

@Singleton
class OAuth2ClientRegistry(
    private val clients: List<OAuth2ClientConfig>,
) {
    fun find(clientId: String): OAuth2RegisteredClient? =
        clients
            .firstOrNull { it.clientId == clientId }
            ?.let { client ->
                OAuth2RegisteredClient(
                    clientId = client.clientId,
                    clientName = client.clientName.ifBlank { client.clientId },
                    redirectUris = client.redirectUris.toSet(),
                    scopes = client.scopes.toSet(),
                    resources = client.resources.toSet(),
                )
            }
}
