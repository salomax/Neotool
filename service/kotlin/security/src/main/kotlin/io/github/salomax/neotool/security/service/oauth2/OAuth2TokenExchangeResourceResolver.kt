package io.github.salomax.neotool.security.service.oauth2

import io.github.salomax.neotool.security.config.OAuthConfig
import jakarta.inject.Singleton

interface OAuth2TokenExchangeResourceResolver {
    fun resolveServiceResourceIdentifier(serviceId: String): String?
}

@Singleton
class ConfigOAuth2TokenExchangeResourceResolver(
    private val oauthConfig: OAuthConfig,
) : OAuth2TokenExchangeResourceResolver {
    override fun resolveServiceResourceIdentifier(serviceId: String): String? =
        oauthConfig.authorizationServer.tokenExchange.serviceResourceIdentifiers[serviceId]
            ?.takeIf { it.isNotBlank() }
}
