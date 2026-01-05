package io.github.salomax.neotool.common.security.key

import io.micronaut.http.client.HttpClient
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock

@DisplayName("JwksClient Unit Tests")
class JwksClientTest {
    private lateinit var httpClient: HttpClient
    private lateinit var jwksClient: JwksClient

    @BeforeEach
    fun setUp() {
        httpClient = mock()
        jwksClient = JwksClient(httpClient)
    }

    @Nested
    @DisplayName("Cache Management")
    inner class CacheManagementTests {
        @Test
        fun `should have zero cache size when initialized`() {
            // Act
            val cacheSize = jwksClient.getCacheSize()

            // Assert
            assertThat(cacheSize).isEqualTo(0)
        }

        @Test
        fun `should clear cache successfully`() {
            // Act
            jwksClient.clearCache()

            // Assert
            assertThat(jwksClient.getCacheSize()).isEqualTo(0)
        }

        @Test
        fun `should maintain zero cache size after clear on empty cache`() {
            // Arrange
            jwksClient.clearCache()

            // Act
            val cacheSize = jwksClient.getCacheSize()

            // Assert
            assertThat(cacheSize).isEqualTo(0)
        }
    }

    @Nested
    @DisplayName("Public Key Retrieval - Error Cases")
    inner class PublicKeyRetrievalErrorTests {
        @Test
        fun `should return null when HTTP client is not configured`() {
            // This is a basic test to ensure null safety in error scenarios
            // Full HTTP tests would require a test server or WireMock

            // Arrange
            val jwksUrl = "https://invalid-url-that-does-not-exist.test/.well-known/jwks.json"

            // Act - This will fail to fetch due to mock HTTP client
            val result = jwksClient.getPublicKey(jwksUrl, "kid-1")

            // Assert - Should return null on error (as per implementation)
            assertThat(result).isNull()
        }
    }

    @Nested
    @DisplayName("Initialization")
    inner class InitializationTests {
        @Test
        fun `should initialize with HTTP client`() {
            // Act
            val client = JwksClient(httpClient)

            // Assert
            assertThat(client).isNotNull
            assertThat(client.getCacheSize()).isEqualTo(0)
        }
    }
}
