package io.github.salomax.neotool.security.service.oauth2

import io.github.salomax.neotool.security.config.OAuthConfig
import io.github.salomax.neotool.security.http.dto.OAuth2ClientRegistrationRequest
import io.github.salomax.neotool.security.http.dto.OAuth2ClientRegistrationResponse
import io.github.salomax.neotool.security.model.OAuth2ClientEntity
import io.github.salomax.neotool.security.model.OAuth2ClientStatus
import io.github.salomax.neotool.security.repo.OAuth2ClientRepository
import jakarta.inject.Singleton
import jakarta.transaction.Transactional
import mu.KotlinLogging
import java.net.URI
import java.time.Instant

@Singleton
open class OAuth2ClientRegistrationService(
    private val clientRepository: OAuth2ClientRepository,
    private val clientMetadataCodec: OAuth2ClientMetadataCodec,
    private val tokenHashing: OAuth2TokenHashing,
    private val oauthConfig: OAuthConfig,
) {
    private val logger = KotlinLogging.logger {}
    private val clientNameRegex = Regex("^[A-Za-z0-9][A-Za-z0-9 .,'&()_:/-]{0,79}$")

    @Transactional
    open fun register(
        request: OAuth2ClientRegistrationRequest,
        sourceIp: String?,
    ): OAuth2ClientRegistrationResponse {
        val redirectUris = validateRedirectUris(request.redirect_uris)
        val clientName = validateClientName(request.client_name)
        val scopes = validateScopes(request.scope)
        val resources = validateResources(request.resources)
        val grantTypes = validateGrantTypes(request.grant_types)
        val responseTypes = validateResponseTypes(request.response_types)
        val tokenEndpointAuthMethod = validateTokenEndpointAuthMethod(request.token_endpoint_auth_method)
        // Stored only for metadata round-trip. Do not render or fetch these URIs without a separate SSRF/spoofing review.
        request.logo_uri?.let { validateOptionalUri("logo_uri", it) }
        request.client_uri?.let { validateOptionalUri("client_uri", it) }

        val now = Instant.now()
        val clientId = generateClientId()
        val saved =
            clientRepository.save(
                OAuth2ClientEntity(
                    id = clientId,
                    clientName = clientName,
                    redirectUris = clientMetadataCodec.encodeList(redirectUris),
                    scopes = clientMetadataCodec.encodeList(scopes),
                    resources = clientMetadataCodec.encodeList(resources),
                    grantTypes = clientMetadataCodec.encodeList(grantTypes),
                    responseTypes = clientMetadataCodec.encodeList(responseTypes),
                    tokenEndpointAuthMethod = tokenEndpointAuthMethod,
                    logoUri = request.logo_uri,
                    clientUri = request.client_uri,
                    clientIdIssuedAt = now,
                    createdFromIp = sourceIp,
                    status = OAuth2ClientStatus.ACTIVE,
                    createdAt = now,
                    updatedAt = now,
                ),
            )

        logger.info { "OAuth2 dynamic client registered from ip=$sourceIp client_id=${saved.id}" }
        return toResponse(saved)
    }

    fun toResponse(client: OAuth2ClientEntity): OAuth2ClientRegistrationResponse =
        OAuth2ClientRegistrationResponse(
            client_id = client.id,
            client_id_issued_at = client.clientIdIssuedAt.epochSecond,
            client_name = client.clientName,
            redirect_uris = clientMetadataCodec.decodeList(client.redirectUris),
            scope = clientMetadataCodec.decodeList(client.scopes).joinToString(" "),
            resources = clientMetadataCodec.decodeList(client.resources),
            grant_types = clientMetadataCodec.decodeList(client.grantTypes),
            response_types = clientMetadataCodec.decodeList(client.responseTypes),
            token_endpoint_auth_method = client.tokenEndpointAuthMethod,
            logo_uri = client.logoUri,
            client_uri = client.clientUri,
        )

    private fun validateClientName(clientName: String?): String? {
        val value = clientName?.trim()?.takeIf { it.isNotBlank() } ?: return null
        if (!clientNameRegex.matches(value)) {
            throw OAuth2InvalidRequestException(
                "client_name must be 1-80 characters and use letters, numbers, spaces, or common punctuation",
            )
        }
        return value
    }

    private fun validateRedirectUris(redirectUris: List<String>?): List<String> {
        val values = requiredList("redirect_uris", redirectUris)
        values.forEach { value ->
            val uri = parseAbsoluteUri("redirect_uris", value)
            val isLocalHttp =
                uri.scheme == "http" && (uri.host == "localhost" || uri.host == "127.0.0.1")
            if (uri.scheme != "https" && !isLocalHttp) {
                throw OAuth2InvalidRequestException(
                    "redirect_uris must use https except http://localhost and http://127.0.0.1",
                )
            }
        }
        return values
    }

    private fun validateScopes(scope: String?): List<String> {
        val scopes =
            scope
                ?.split(" ")
                ?.map { it.trim() }
                ?.filter { it.isNotBlank() }
                ?.distinct()
                ?: emptyList()
        val supportedScopes = oauthConfig.authorizationServer.metadata.supportedScopes.toSet()
        if (scopes.isEmpty()) {
            return supportedScopes.toList()
        }
        val unknownScopes = scopes.filterNot { supportedScopes.contains(it) }
        if (unknownScopes.isNotEmpty()) {
            throw OAuth2InvalidRequestException("Unknown scope requested")
        }
        return scopes
    }

    private fun validateResources(resources: List<String>?): List<String> {
        val supportedResources =
            oauthConfig.authorizationServer.metadata.supportedResources
                .filter { it.isNotBlank() }
        if (supportedResources.isEmpty()) {
            throw OAuth2InvalidRequestException("No OAuth2 resources are configured")
        }
        val values = resources?.map { it.trim() }?.filter { it.isNotBlank() }?.distinct().orEmpty()
        if (values.isEmpty()) {
            return supportedResources
        }
        values.forEach { parseAbsoluteUri("resources", it) }
        val supportedResourcesSet = supportedResources.toSet()
        val unknownResources = values.filterNot { supportedResourcesSet.contains(it) }
        if (unknownResources.isNotEmpty()) {
            throw OAuth2InvalidRequestException("Unknown resource requested")
        }
        return values
    }

    private fun validateGrantTypes(grantTypes: List<String>?): List<String> {
        val values = grantTypes?.takeIf { it.isNotEmpty() } ?: listOf("authorization_code", "refresh_token")
        val unsupported = values.filterNot { it == "authorization_code" || it == "refresh_token" }
        if (unsupported.isNotEmpty()) {
            throw OAuth2InvalidRequestException("Unsupported grant_type requested")
        }
        return values.distinct()
    }

    private fun validateResponseTypes(responseTypes: List<String>?): List<String> {
        val values = responseTypes?.takeIf { it.isNotEmpty() } ?: listOf("code")
        if (values.any { it != "code" }) {
            throw OAuth2InvalidRequestException("Unsupported response_type requested")
        }
        return values.distinct()
    }

    private fun validateTokenEndpointAuthMethod(value: String?): String {
        val method = value?.takeIf { it.isNotBlank() } ?: "none"
        if (method != "none") {
            throw OAuth2InvalidRequestException(
                "Only public clients with token_endpoint_auth_method none are supported",
            )
        }
        return method
    }

    private fun validateOptionalUri(
        field: String,
        value: String,
    ) {
        parseAbsoluteUri(field, value)
    }

    private fun requiredList(
        field: String,
        values: List<String>?,
    ): List<String> {
        val normalized = values?.map { it.trim() }?.filter { it.isNotBlank() } ?: emptyList()
        if (normalized.isEmpty()) {
            throw OAuth2InvalidRequestException("$field is required")
        }
        return normalized.distinct()
    }

    private fun parseAbsoluteUri(
        field: String,
        value: String,
    ): URI =
        runCatching { URI(value) }
            .getOrElse { throw OAuth2InvalidRequestException("$field contains a malformed URI") }
            .takeIf { it.isAbsolute && !it.host.isNullOrBlank() }
            ?: throw OAuth2InvalidRequestException("$field must contain absolute URIs")

    private fun generateClientId(): String = "dcr_${tokenHashing.newOpaqueToken(24)}"
}
