package io.github.salomax.neotool.security.service.oauth2

import io.github.salomax.neotool.security.config.OAuth2ClientConfig
import io.github.salomax.neotool.security.model.OAuth2ClientEntity
import io.github.salomax.neotool.security.model.OAuth2ClientStatus
import io.github.salomax.neotool.security.repo.OAuth2ClientRepository
import jakarta.inject.Singleton
import jakarta.transaction.Transactional
import java.time.Instant

data class OAuth2RegisteredClient(
    val clientId: String,
    val clientName: String,
    val redirectUris: Set<String>,
    val scopes: Set<String>,
    val resources: Set<String>,
)

@Singleton
open class OAuth2ClientRegistry(
    private val clients: List<OAuth2ClientConfig>,
    private val clientRepository: OAuth2ClientRepository,
    private val clientMetadataCodec: OAuth2ClientMetadataCodec,
) {
    fun find(clientId: String): OAuth2RegisteredClient? =
        clientRepository.findByIdAndStatus(clientId, OAuth2ClientStatus.ACTIVE)?.let { fromEntity(it) }
            ?: clients
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

    fun configuredResources(): Set<String> =
        clients
            .flatMap { it.resources }
            .filter { it.isNotBlank() }
            .toSet()

    @Transactional
    open fun markUsed(clientId: String) {
        clientRepository.markUsed(clientId, Instant.now())
    }

    private fun fromEntity(client: OAuth2ClientEntity): OAuth2RegisteredClient =
        OAuth2RegisteredClient(
            clientId = client.id,
            clientName = client.clientName?.ifBlank { client.id } ?: client.id,
            redirectUris = clientMetadataCodec.decodeList(client.redirectUris).toSet(),
            scopes = clientMetadataCodec.decodeList(client.scopes).toSet(),
            resources = clientMetadataCodec.decodeList(client.resources).toSet(),
        )
}
