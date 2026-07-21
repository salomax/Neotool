package io.github.salomax.neotool.security.test.config

import io.github.salomax.neotool.security.config.OAuthConfig
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class OAuthConfigTest {
    @Test
    fun `metadata config accepts absolute paths and absolute URIs`() {
        OAuthConfig.AuthorizationServerConfig.MetadataConfig(
            authorizationEndpoint = "/oauth2/authorize",
            tokenEndpoint = "https://auth.invistus.com.br/oauth2/token",
            jwksUri = "/.well-known/jwks.json",
        ).validate()
    }

    @Test
    fun `metadata config rejects malformed endpoint`() {
        assertThrows<IllegalArgumentException> {
            OAuthConfig.AuthorizationServerConfig.MetadataConfig(
                tokenEndpoint = "http://[invalid",
            ).validate()
        }
    }

    @Test
    fun `metadata config rejects relative endpoint without leading slash`() {
        assertThrows<IllegalArgumentException> {
            OAuthConfig.AuthorizationServerConfig.MetadataConfig(
                tokenEndpoint = "oauth2/token",
            ).validate()
        }
    }
}
