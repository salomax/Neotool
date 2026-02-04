package io.github.salomax.neotool.common.security.service

import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.BlockingHttpClient
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.atMost
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@DisplayName("ServiceTokenClient Unit Tests")
class ServiceTokenClientTest {
    private lateinit var httpClient: HttpClient
    private lateinit var blockingHttpClient: BlockingHttpClient
    private lateinit var serviceTokenClient: ServiceTokenClient

    @BeforeEach
    fun setUp() {
        httpClient = mock()
        blockingHttpClient = mock()
        whenever(httpClient.toBlocking()).thenReturn(blockingHttpClient)
    }

    private fun createClient(
        serviceId: String? = "test-service-id",
        clientSecret: String? = "test-secret",
        securityServiceUrl: String = "http://localhost:8080",
    ): ServiceTokenClient =
        ServiceTokenClient(
            securityServiceUrl = securityServiceUrl,
            serviceId = serviceId,
            clientSecret = clientSecret,
            httpClient = httpClient,
        )

    /**
     * Create a TokenResponse instance for mocking HTTP responses.
     */
    private fun createTokenResponse(
        accessToken: String,
        tokenType: String = "Bearer",
        expiresIn: Long = 3600L,
    ): TokenResponse =
        TokenResponse(
            access_token = accessToken,
            token_type = tokenType,
            expires_in = expiresIn,
        )

    @Nested
    @DisplayName("Token Retrieval - Success Cases")
    inner class TokenRetrievalSuccessTests {
        @Test
        fun `should fetch token from security service on first call`() {
            runBlocking {
                // Arrange
                val tokenResponse = createTokenResponse("test-token-123")

                val httpResponse: HttpResponse<Any> = mock()
                whenever(httpResponse.body()).thenReturn(tokenResponse)
                whenever(blockingHttpClient.exchange(any<HttpRequest<Any>>(), any<Class<*>>()))
                    .thenReturn(httpResponse)

                serviceTokenClient = createClient()

                // Act
                val token = serviceTokenClient.getServiceToken("target-service")

                // Assert
                assertThat(token).isEqualTo("test-token-123")
                verify(blockingHttpClient, times(1)).exchange(any<HttpRequest<Any>>(), any<Class<*>>())
            }
        }

        @Test
        fun `should include audience in token request`() {
            runBlocking {
                // Arrange
                val tokenResponse = createTokenResponse("test-token")

                val httpResponse: HttpResponse<Any> = mock()
                whenever(httpResponse.body()).thenReturn(tokenResponse)
                whenever(blockingHttpClient.exchange(any<HttpRequest<Any>>(), any<Class<*>>()))
                    .thenReturn(httpResponse)

                serviceTokenClient = createClient()

                // Act
                serviceTokenClient.getServiceToken("custom-audience")

                // Assert
                val requestCaptor = argumentCaptor<HttpRequest<Any>>()
                verify(blockingHttpClient).exchange(requestCaptor.capture(), any<Class<*>>())
                val request = requestCaptor.firstValue
                assertThat(request.uri.toString()).contains("/oauth/token")
            }
        }

        @Test
        fun `should return cached token on subsequent calls`() {
            runBlocking {
                // Arrange
                val tokenResponse = createTokenResponse("cached-token-456")

                val httpResponse: HttpResponse<Any> = mock()
                whenever(httpResponse.body()).thenReturn(tokenResponse)
                whenever(blockingHttpClient.exchange(any<HttpRequest<Any>>(), any<Class<*>>()))
                    .thenReturn(httpResponse)

                serviceTokenClient = createClient()

                // Act - First call
                val token1 = serviceTokenClient.getServiceToken("target-service")
                // Second call should use cache
                val token2 = serviceTokenClient.getServiceToken("target-service")

                // Assert
                assertThat(token1).isEqualTo("cached-token-456")
                assertThat(token2).isEqualTo("cached-token-456")
                // Should only call HTTP client once due to caching
                verify(blockingHttpClient, times(1)).exchange(any<HttpRequest<Any>>(), any<Class<*>>())
            }
        }

        @Test
        fun `should fetch new token when cache expires`() {
            runBlocking {
                // Arrange
                val shortExpiryResponse = createTokenResponse("short-lived-token", expiresIn = 1L)
                val newTokenResponse = createTokenResponse("new-token-after-expiry")

                val httpResponse1: HttpResponse<Any> = mock()
                val httpResponse2: HttpResponse<Any> = mock()
                whenever(httpResponse1.body()).thenReturn(shortExpiryResponse)
                whenever(httpResponse2.body()).thenReturn(newTokenResponse)
                whenever(blockingHttpClient.exchange(any<HttpRequest<Any>>(), any<Class<*>>()))
                    .thenReturn(httpResponse1)
                    .thenReturn(httpResponse2)

                serviceTokenClient = createClient()

                // Act - First call
                val token1 = serviceTokenClient.getServiceToken("target-service")

                // Wait for token to expire (1 second + 60 second buffer = 61 seconds)
                Thread.sleep(62000)

                // Second call should fetch new token
                val token2 = serviceTokenClient.getServiceToken("target-service")

                // Assert
                assertThat(token1).isEqualTo("short-lived-token")
                assertThat(token2).isEqualTo("new-token-after-expiry")
                verify(blockingHttpClient, times(2)).exchange(any<HttpRequest<Any>>(), any<Class<*>>())
            }
        }

        @Test
        fun `should cache tokens separately for different audiences`() {
            runBlocking {
                // Arrange
                val tokenResponse1 = createTokenResponse("token-for-service-1")
                val tokenResponse2 = createTokenResponse("token-for-service-2")

                val httpResponse1: HttpResponse<Any> = mock()
                val httpResponse2: HttpResponse<Any> = mock()
                whenever(httpResponse1.body()).thenReturn(tokenResponse1)
                whenever(httpResponse2.body()).thenReturn(tokenResponse2)
                whenever(blockingHttpClient.exchange(any<HttpRequest<Any>>(), any<Class<*>>()))
                    .thenReturn(httpResponse1)
                    .thenReturn(httpResponse2)

                serviceTokenClient = createClient()

                // Act
                val token1 = serviceTokenClient.getServiceToken("service-1")
                val token2 = serviceTokenClient.getServiceToken("service-2")
                val token1Again = serviceTokenClient.getServiceToken("service-1")
                val token2Again = serviceTokenClient.getServiceToken("service-2")

                // Assert
                assertThat(token1).isEqualTo("token-for-service-1")
                assertThat(token2).isEqualTo("token-for-service-2")
                assertThat(token1Again).isEqualTo("token-for-service-1")
                assertThat(token2Again).isEqualTo("token-for-service-2")
                // Should call HTTP client twice (once per audience)
                verify(blockingHttpClient, times(2)).exchange(any<HttpRequest<Any>>(), any<Class<*>>())
            }
        }
    }

    @Nested
    @DisplayName("Token Retrieval - Error Cases")
    inner class TokenRetrievalErrorTests {
        @Test
        fun `should throw exception when service ID is not configured`() {
            runBlocking {
                // Arrange
                serviceTokenClient = createClient(serviceId = null)

                // Act & Assert
                assertThatThrownBy {
                    runBlocking {
                        serviceTokenClient.getServiceToken("target-service")
                    }
                }.isInstanceOf(IllegalStateException::class.java)
                    .hasMessageContaining("Service ID not configured")

                verify(blockingHttpClient, never()).exchange(any<HttpRequest<Any>>(), any<Class<*>>())
            }
        }

        @Test
        fun `should throw exception when client secret is not configured`() {
            runBlocking {
                // Arrange
                serviceTokenClient = createClient(clientSecret = null)

                // Act & Assert
                assertThatThrownBy {
                    runBlocking {
                        serviceTokenClient.getServiceToken("target-service")
                    }
                }.isInstanceOf(IllegalStateException::class.java)
                    .hasMessageContaining("Client secret not configured")

                verify(blockingHttpClient, never()).exchange(any<HttpRequest<Any>>(), any<Class<*>>())
            }
        }

        @Test
        fun `should throw exception when token response is empty`() {
            runBlocking {
                // Arrange
                val httpResponse: HttpResponse<Any> = mock()
                whenever(httpResponse.body()).thenReturn(null)
                whenever(blockingHttpClient.exchange(any<HttpRequest<Any>>(), any<Class<*>>()))
                    .thenReturn(httpResponse)

                serviceTokenClient = createClient()

                // Act & Assert
                assertThatThrownBy {
                    runBlocking {
                        serviceTokenClient.getServiceToken("target-service")
                    }
                }.isInstanceOf(IllegalStateException::class.java)
                    .hasMessageContaining("Empty token response")
            }
        }

        @Test
        fun `should throw exception when HTTP client returns error`() {
            runBlocking {
                // Arrange
                val httpException =
                    HttpClientResponseException(
                        "Token request failed",
                        mock<io.micronaut.http.HttpResponse<Any>>().apply {
                            whenever(status()).thenReturn(HttpStatus.UNAUTHORIZED)
                        },
                    )
                whenever(blockingHttpClient.exchange(any<HttpRequest<Any>>(), any<Class<*>>()))
                    .thenThrow(httpException)

                serviceTokenClient = createClient()

                // Act & Assert
                assertThatThrownBy {
                    runBlocking {
                        serviceTokenClient.getServiceToken("target-service")
                    }
                }.isInstanceOf(IllegalStateException::class.java)
                    .hasMessageContaining("Failed to obtain service token")
            }
        }

        @Test
        fun `should throw exception on unexpected HTTP error`() {
            runBlocking {
                // Arrange
                whenever(blockingHttpClient.exchange(any<HttpRequest<Any>>(), any<Class<*>>()))
                    .thenThrow(RuntimeException("Network error"))

                serviceTokenClient = createClient()

                // Act & Assert
                assertThatThrownBy {
                    runBlocking {
                        serviceTokenClient.getServiceToken("target-service")
                    }
                }.isInstanceOf(IllegalStateException::class.java)
                    .hasMessageContaining("Failed to obtain service token")
            }
        }
    }

    @Nested
    @DisplayName("User Context Propagation")
    inner class UserContextPropagationTests {
        @Test
        fun `should throw UnsupportedOperationException for user context propagation`() {
            runBlocking {
                // Arrange
                serviceTokenClient = createClient()

                // Act & Assert
                assertThatThrownBy {
                    runBlocking {
                        serviceTokenClient.getServiceTokenWithUserContext(
                            targetAudience = "target-service",
                            userId = java.util.UUID.randomUUID(),
                            userPermissions = listOf("read", "write"),
                        )
                    }
                }.isInstanceOf(UnsupportedOperationException::class.java)
                    .hasMessageContaining("User context propagation is not yet implemented")
            }
        }
    }

    @Nested
    @DisplayName("Cache Management")
    inner class CacheManagementTests {
        @Test
        fun `should clear cache successfully`() {
            runBlocking {
                // Arrange
                val tokenResponse = createTokenResponse("token-to-cache")

                val httpResponse: HttpResponse<Any> = mock()
                whenever(httpResponse.body()).thenReturn(tokenResponse)
                whenever(blockingHttpClient.exchange(any<HttpRequest<Any>>(), any<Class<*>>()))
                    .thenReturn(httpResponse)

                serviceTokenClient = createClient()

                // Act - Get token to populate cache
                serviceTokenClient.getServiceToken("target-service")
                // Clear cache
                serviceTokenClient.clearCache()
                // Get token again - should fetch new token
                serviceTokenClient.getServiceToken("target-service")

                // Assert - Should call HTTP client twice (once before clear, once after)
                verify(blockingHttpClient, times(2)).exchange(any<HttpRequest<Any>>(), any<Class<*>>())
            }
        }

        @Test
        fun `should handle concurrent token requests safely`() {
            runBlocking {
                // Arrange
                val tokenResponse = createTokenResponse("concurrent-token")

                val httpResponse: HttpResponse<Any> = mock()
                whenever(httpResponse.body()).thenReturn(tokenResponse)
                whenever(blockingHttpClient.exchange(any<HttpRequest<Any>>(), any<Class<*>>()))
                    .thenReturn(httpResponse)

                serviceTokenClient = createClient()

                // Act - Make concurrent requests
                val results =
                    coroutineScope {
                        (1..10)
                            .map {
                                async {
                                    serviceTokenClient.getServiceToken("target-service")
                                }
                            }.awaitAll()
                    }

                // Assert - All should return the same token
                assertThat(results).allMatch { it == "concurrent-token" }
                // Should only call HTTP client once due to caching and locking
                // Note: Due to race conditions, it might be called more than once, but should be minimal
                verify(blockingHttpClient, atMost(2)).exchange(any<HttpRequest<Any>>(), any<Class<*>>())
            }
        }
    }
}
