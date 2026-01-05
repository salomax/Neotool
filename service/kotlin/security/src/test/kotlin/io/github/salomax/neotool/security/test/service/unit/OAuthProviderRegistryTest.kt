package io.github.salomax.neotool.security.test.service.unit

import io.github.salomax.neotool.security.service.oauth.OAuthProvider
import io.github.salomax.neotool.security.service.oauth.OAuthProviderRegistry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@DisplayName("OAuthProviderRegistry Unit Tests")
class OAuthProviderRegistryTest {
    private lateinit var googleProvider: OAuthProvider
    private lateinit var registry: OAuthProviderRegistry

    @BeforeEach
    fun setUp() {
        googleProvider = mock()
        whenever(googleProvider.getProviderName()).thenReturn("google")
        registry = OAuthProviderRegistry(listOf(googleProvider))
    }

    @Nested
    @DisplayName("Provider Lookup")
    inner class ProviderLookupTests {
        @Test
        fun `should get provider by name`() {
            val provider = registry.getProvider("google")

            assertThat(provider).isNotNull()
            assertThat(provider).isEqualTo(googleProvider)
        }

        @Test
        fun `should get provider by name case insensitive`() {
            val provider = registry.getProvider("GOOGLE")

            assertThat(provider).isNotNull()
            assertThat(provider).isEqualTo(googleProvider)
        }

        @Test
        fun `should return null for unsupported provider`() {
            val provider = registry.getProvider("microsoft")

            assertThat(provider).isNull()
        }

        @Test
        fun `should return null for empty provider name`() {
            val provider = registry.getProvider("")

            assertThat(provider).isNull()
        }
    }

    @Nested
    @DisplayName("Provider Support Check")
    inner class ProviderSupportTests {
        @Test
        fun `should return true for supported provider`() {
            assertThat(registry.isProviderSupported("google")).isTrue()
        }

        @Test
        fun `should return true for supported provider case insensitive`() {
            assertThat(registry.isProviderSupported("GOOGLE")).isTrue()
        }

        @Test
        fun `should return false for unsupported provider`() {
            assertThat(registry.isProviderSupported("microsoft")).isFalse()
        }
    }

    @Nested
    @DisplayName("Supported Providers List")
    inner class SupportedProvidersTests {
        @Test
        fun `should return list of supported providers`() {
            val providers = registry.getSupportedProviders()

            assertThat(providers).containsExactly("google")
        }

        @Test
        fun `should return empty list when no providers registered`() {
            val emptyRegistry = OAuthProviderRegistry(emptyList())

            val providers = emptyRegistry.getSupportedProviders()

            assertThat(providers).isEmpty()
        }
    }
}
