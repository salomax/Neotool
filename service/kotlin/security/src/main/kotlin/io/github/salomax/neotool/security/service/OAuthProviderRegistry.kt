package io.github.salomax.neotool.security.service.oauth

import jakarta.inject.Singleton

/**
 * Registry for OAuth providers.
 *
 * Manages all available OAuth providers and allows lookup by provider name.
 * This makes it easy to add new providers in the future.
 */
@Singleton
class OAuthProviderRegistry(
    private val providers: List<OAuthProvider>,
) {
    private val providerMap: Map<String, OAuthProvider> = providers.associateBy { it.getProviderName() }

    /**
     * Get an OAuth provider by name.
     *
     * @param providerName The provider name (e.g., "google", "microsoft")
     * @return OAuthProvider if found, null otherwise
     */
    fun getProvider(providerName: String): OAuthProvider? {
        return providerMap[providerName.lowercase()]
    }

    /**
     * Check if a provider is supported.
     */
    fun isProviderSupported(providerName: String): Boolean {
        return providerMap.containsKey(providerName.lowercase())
    }

    /**
     * Get all supported provider names.
     */
    fun getSupportedProviders(): List<String> {
        return providerMap.keys.toList()
    }
}
