package io.github.salomax.neotool.security.test.service

import io.github.salomax.neotool.security.config.OAuthConfig
import io.github.salomax.neotool.security.service.GoogleOAuthProvider
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@DisplayName("GoogleOAuthProvider Unit Tests")
class GoogleOAuthProviderTest {
    private lateinit var oauthConfig: OAuthConfig
    private lateinit var googleOAuthProvider: GoogleOAuthProvider

    @BeforeEach
    fun setUp() {
        oauthConfig = mock()
        googleOAuthProvider = GoogleOAuthProvider(oauthConfig)
    }

    @Nested
    @DisplayName("Provider Name")
    inner class ProviderNameTests {
        @Test
        fun `should return google as provider name`() {
            assertThat(googleOAuthProvider.getProviderName()).isEqualTo("google")
        }
    }

    @Nested
    @DisplayName("Configuration Check")
    inner class ConfigurationCheckTests {
        @Test
        fun `should return null when Google OAuth is not configured`() {
            val googleConfig = OAuthConfig.GoogleOAuthConfig(clientId = "", issuer = "accounts.google.com")
            whenever(oauthConfig.google).thenReturn(googleConfig)

            val result = googleOAuthProvider.validateAndExtractClaims("invalid-token")

            assertThat(result).isNull()
        }

        @Test
        fun `should return null when client ID is blank`() {
            val googleConfig = OAuthConfig.GoogleOAuthConfig(clientId = "   ", issuer = "accounts.google.com")
            whenever(oauthConfig.google).thenReturn(googleConfig)

            val result = googleOAuthProvider.validateAndExtractClaims("invalid-token")

            assertThat(result).isNull()
        }
    }

    @Nested
    @DisplayName("Token Validation")
    inner class TokenValidationTests {
        @Test
        fun `should return null for invalid token format`() {
            val googleConfig =
                OAuthConfig.GoogleOAuthConfig(
                    clientId = "test-client-id",
                    issuer = "accounts.google.com",
                )
            whenever(oauthConfig.google).thenReturn(googleConfig)

            val result = googleOAuthProvider.validateAndExtractClaims("invalid-token-format")

            assertThat(result).isNull()
        }

        @Test
        fun `should return null for empty token`() {
            val googleConfig =
                OAuthConfig.GoogleOAuthConfig(
                    clientId = "test-client-id",
                    issuer = "accounts.google.com",
                )
            whenever(oauthConfig.google).thenReturn(googleConfig)

            val result = googleOAuthProvider.validateAndExtractClaims("")

            assertThat(result).isNull()
        }
    }

    // Note: Testing with real Google ID tokens would require actual Google OAuth setup
    // In a real scenario, you would use test fixtures with valid Google ID tokens
    // or mock the IdToken.parse() method. For now, we test the configuration and error handling.
}
