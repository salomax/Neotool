package io.github.salomax.neotool.security.config

import io.micronaut.context.annotation.ConfigurationProperties
import jakarta.validation.constraints.NotBlank
import java.net.URI
import java.net.URISyntaxException

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
    val authorizationServer: AuthorizationServerConfig = AuthorizationServerConfig(),
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

    @ConfigurationProperties("authorization-server")
    data class AuthorizationServerConfig(
        val session: SessionConfig = SessionConfig(),
        val metadata: MetadataConfig = MetadataConfig(),
        val registration: RegistrationConfig = RegistrationConfig(),
        val tokenExchange: TokenExchangeConfig = TokenExchangeConfig(),
        val authorizationRequestTtlSeconds: Long = 600,
        val authorizationCodeTtlSeconds: Long = 60,
    ) {
        @ConfigurationProperties("metadata")
        data class MetadataConfig(
            val authorizationEndpoint: String = "/oauth2/authorize",
            val tokenEndpoint: String = "/oauth2/token",
            val jwksUri: String = "/.well-known/jwks.json",
            val supportedScopes: List<String> =
                listOf(
                    "finance:transactions:read",
                    "finance:transactions:write",
                    "finance:cashflow:read",
                    "finance:accounts:read",
                    "finance:bills:read",
                    "finance:categories:read",
                    "finance:categories:write",
                    "finance:rules:read",
                    "finance:rules:write",
                ),
            val responseTypes: List<String> = listOf("code"),
            val grantTypes: List<String> = listOf("authorization_code", "refresh_token"),
            val tokenEndpointAuthMethods: List<String> = listOf("none"),
            val codeChallengeMethods: List<String> = listOf("S256"),
            val supportedResources: List<String> = emptyList(),
            val cacheMaxAgeSeconds: Long = 3600,
        ) {
            fun validate() {
                validateEndpoint("authorization-endpoint", authorizationEndpoint)
                validateEndpoint("token-endpoint", tokenEndpoint)
                validateEndpoint("jwks-uri", jwksUri)
                require(supportedScopes.all { it.isNotBlank() }) {
                    "oauth.authorization-server.metadata.supported-scopes must not contain blank values"
                }
                require(responseTypes.all { it.isNotBlank() }) {
                    "oauth.authorization-server.metadata.response-types must not contain blank values"
                }
                require(grantTypes.all { it.isNotBlank() }) {
                    "oauth.authorization-server.metadata.grant-types must not contain blank values"
                }
                require(tokenEndpointAuthMethods.all { it.isNotBlank() }) {
                    "oauth.authorization-server.metadata.token-endpoint-auth-methods must not contain blank values"
                }
                require(codeChallengeMethods.all { it.isNotBlank() }) {
                    "oauth.authorization-server.metadata.code-challenge-methods must not contain blank values"
                }
                require(supportedResources.all { it.isNotBlank() }) {
                    "oauth.authorization-server.metadata.supported-resources must not contain blank values"
                }
                require(cacheMaxAgeSeconds > 0) {
                    "oauth.authorization-server.metadata.cache-max-age-seconds must be positive"
                }
            }

            private fun validateEndpoint(
                property: String,
                endpoint: String,
            ) {
                require(endpoint.isNotBlank()) {
                    "oauth.authorization-server.metadata.$property must not be blank"
                }
                val uri =
                    try {
                        URI(endpoint)
                    } catch (e: URISyntaxException) {
                        throw IllegalArgumentException(
                            absoluteEndpointMessage(property, endpoint),
                            e,
                        )
                    }
                require(uri.isAbsolute || endpoint.startsWith("/")) {
                    absoluteEndpointMessage(property, endpoint)
                }
            }

            private fun absoluteEndpointMessage(
                property: String,
                endpoint: String,
            ): String =
                "oauth.authorization-server.metadata.$property " +
                    "must be an absolute URI or absolute path: $endpoint"
        }

        @ConfigurationProperties("session")
        data class SessionConfig(
            val signingSecret: String = "",
            val ttlSeconds: Long = 900,
            val cookieName: String = "INVISTUS_AUTH_SESSION",
            val secure: Boolean = true,
        )

        @ConfigurationProperties("token-exchange")
        data class TokenExchangeConfig(
            val serviceResourceIdentifiers: Map<String, String> = emptyMap(),
        )

        @ConfigurationProperties("registration")
        data class RegistrationConfig(
            val garbageCollection: GarbageCollectionConfig = GarbageCollectionConfig(),
        ) {
            @ConfigurationProperties("garbage-collection")
            data class GarbageCollectionConfig(
                val enabled: Boolean = true,
                val fixedDelay: String = "PT24H",
                val unusedTtlSeconds: Long = 30L * 24 * 60 * 60,
            )
        }
    }
}
