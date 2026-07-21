package io.github.salomax.neotool.security.http

import io.github.salomax.neotool.common.security.config.JwtConfig
import io.github.salomax.neotool.security.config.OAuthConfig
import io.micronaut.http.HttpResponse
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Produces
import io.micronaut.serde.annotation.Serdeable
import jakarta.inject.Singleton
import java.net.URI

@Singleton
@Controller("/.well-known")
class AuthorizationServerMetadataController(
    private val jwtConfig: JwtConfig,
    private val oauthConfig: OAuthConfig,
) {
    @Serdeable
    data class AuthorizationServerMetadata(
        val issuer: String,
        val authorization_endpoint: String,
        val token_endpoint: String,
        val jwks_uri: String,
        val scopes_supported: List<String>,
        val response_types_supported: List<String>,
        val grant_types_supported: List<String>,
        val token_endpoint_auth_methods_supported: List<String>,
        val code_challenge_methods_supported: List<String>,
        val resource_indicators_supported: Boolean,
        val registration_endpoint: String,
    )

    @Get("/oauth-authorization-server")
    @Produces(MediaType.APPLICATION_JSON)
    fun getAuthorizationServerMetadata(): HttpResponse<AuthorizationServerMetadata> {
        val metadataConfig = oauthConfig.authorizationServer.metadata
        val response =
            AuthorizationServerMetadata(
                issuer = jwtConfig.issuer,
                authorization_endpoint = resolveEndpoint(metadataConfig.authorizationEndpoint),
                token_endpoint = resolveEndpoint(metadataConfig.tokenEndpoint),
                jwks_uri = resolveEndpoint(metadataConfig.jwksUri),
                scopes_supported = metadataConfig.supportedScopes,
                response_types_supported = metadataConfig.responseTypes,
                grant_types_supported = metadataConfig.grantTypes,
                token_endpoint_auth_methods_supported = metadataConfig.tokenEndpointAuthMethods,
                code_challenge_methods_supported = metadataConfig.codeChallengeMethods,
                resource_indicators_supported = true,
                registration_endpoint = resolveEndpoint("/oauth2/register"),
            )

        return HttpResponse
            .ok(response)
            .header("Cache-Control", "public, max-age=${metadataConfig.cacheMaxAgeSeconds}")
    }

    private fun resolveEndpoint(endpoint: String): String {
        if (URI(endpoint).isAbsolute) {
            return endpoint
        }
        return jwtConfig.issuer.trimEnd('/') + "/" + endpoint.trimStart('/')
    }
}
