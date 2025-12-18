package io.github.salomax.neotool.security.config

import io.micronaut.context.annotation.ConfigurationProperties
import jakarta.validation.constraints.NotBlank

/**
 * Configuration properties for OAuth2 providers.
 *
 * Properties can be set via environment variables or application.yml:
 * - oauth.google.client-id: Google OAuth client ID
 * - oauth.google.issuer: Google JWT issuer (default: accounts.google.com)
 *
 * The structure is extensible for future providers:
 * - oauth.microsoft.client-id
 * - oauth.github.client-id
 * etc.
 *
 * Uses nested inner classes following Micronaut best practices for nested ConfigurationProperties.
 */
@ConfigurationProperties("oauth")
data class OAuthConfig(
    /**
     * Google OAuth configuration
     */
    val google: GoogleOAuthConfig = GoogleOAuthConfig(),
) {
    /**
     * Check if any OAuth provider is configured
     */
    fun isAnyProviderConfigured(): Boolean {
        return google.resolveClientId().isNotBlank()
    }

    /**
     * Google OAuth provider configuration.
     *
     * Nested inner class following Micronaut pattern for nested ConfigurationProperties.
     * Micronaut automatically binds properties from oauth.google.* in application.yml.
     */
    @ConfigurationProperties("google")
    data class GoogleOAuthConfig(
        /**
         * Google OAuth client ID.
         * Set via GOOGLE_CLIENT_ID environment variable or oauth.google.client-id in application.yml
         * Micronaut automatically resolves ${GOOGLE_CLIENT_ID:} from application.yml
         */
        @get:NotBlank
        val clientId: String = "",
        /**
         * Google JWT issuer.
         * Default: https://accounts.google.com (Google uses the full URL with protocol)
         * Set via GOOGLE_ISSUER environment variable or oauth.google.issuer in application.yml
         * Micronaut automatically resolves ${GOOGLE_ISSUER:https://accounts.google.com} from application.yml
         */
        val issuer: String = "https://accounts.google.com",
    ) {
        /**
         * Check if Google OAuth is configured
         */
        fun isConfigured(): Boolean {
            return clientId.isNotBlank()
        }

        /**
         * Resolve client ID with environment variable override (similar to EmailConfig pattern)
         * This ensures environment variables are always checked at runtime
         */
        fun resolveClientId(): String {
            return System.getenv("GOOGLE_CLIENT_ID") ?: clientId
        }

        /**
         * Resolve issuer with environment variable override
         */
        fun resolveIssuer(): String {
            return System.getenv("GOOGLE_ISSUER") ?: issuer
        }
    }
}
