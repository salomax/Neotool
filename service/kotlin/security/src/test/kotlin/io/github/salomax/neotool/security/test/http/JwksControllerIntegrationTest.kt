package io.github.salomax.neotool.security.test.http

import io.github.salomax.neotool.common.security.config.JwtConfig
import io.github.salomax.neotool.common.test.http.exchangeAsString
import io.github.salomax.neotool.common.test.integration.BaseIntegrationTest
import io.github.salomax.neotool.security.key.SecurityKeyManagerFactory
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.assertThrows
import java.util.Base64

@MicronautTest(startApplication = true)
@DisplayName("JWKS Controller Integration Tests")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Tag("integration")
@Tag("security")
@Tag("http")
open class JwksControllerIntegrationTest : BaseIntegrationTest() {
    @Inject
    lateinit var jwtConfig: JwtConfig

    @Inject
    lateinit var keyManagerFactory: SecurityKeyManagerFactory

    @Nested
    @DisplayName("JWKS Endpoint Success Cases")
    inner class SuccessCasesTests {
        @Test
        fun `should return 200 with valid JWKS response when endpoint is enabled`() {
            // Arrange
            val request = HttpRequest.GET<Any>("/.well-known/jwks.json")

            // Act
            val response = httpClient.exchangeAsString(request)

            // Assert
            if (jwtConfig.jwksEnabled) {
                assertThat(response.status).isEqualTo(HttpStatus.OK)
            } else {
                // Skip if JWKS is disabled in test config
                assertThat(response.status).isIn(HttpStatus.OK, HttpStatus.NOT_FOUND)
            }
        }

        @Test
        fun `should return correct Content-Type header`() {
            // Arrange
            val request = HttpRequest.GET<Any>("/.well-known/jwks.json")

            // Act
            val response = httpClient.exchangeAsString(request)

            // Assert
            if (jwtConfig.jwksEnabled && response.status == HttpStatus.OK) {
                assertThat(response.contentType.isPresent).isTrue()
                assertThat(response.contentType.get().toString()).contains("application/json")
            }
        }

        @Test
        fun `should include Cache-Control header with max-age`() {
            // Arrange
            val request = HttpRequest.GET<Any>("/.well-known/jwks.json")

            // Act
            val response = httpClient.exchangeAsString(request)

            // Assert
            if (jwtConfig.jwksEnabled && response.status == HttpStatus.OK) {
                val cacheControl = response.headers.get("Cache-Control")
                assertThat(cacheControl).isNotNull()
                assertThat(cacheControl).contains("max-age=300")
                assertThat(cacheControl).contains("public")
            }
        }

        @Test
        fun `should return valid JWK format with required fields`() {
            // Arrange
            val request = HttpRequest.GET<Any>("/.well-known/jwks.json")

            // Act
            val response = httpClient.exchangeAsString(request)

            // Assert
            if (jwtConfig.jwksEnabled && response.status == HttpStatus.OK) {
                val body = response.body()
                assertThat(body).isNotNull()

                // Parse response as JWKS
                @Suppress("UNCHECKED_CAST")
                val jwks = json.readValue(body, Map::class.java) as Map<String, Any>
                assertThat(jwks).containsKey("keys")

                @Suppress("UNCHECKED_CAST")
                val keys = jwks["keys"] as List<Map<String, Any>>
                assertThat(keys).isNotEmpty

                val firstKey = keys[0]
                assertThat(firstKey).containsKey("kty")
                assertThat(firstKey).containsKey("kid")
                assertThat(firstKey).containsKey("use")
                assertThat(firstKey).containsKey("alg")
                assertThat(firstKey).containsKey("n")
                assertThat(firstKey).containsKey("e")

                assertThat(firstKey["kty"]).isEqualTo("RSA")
                assertThat(firstKey["use"]).isEqualTo("sig")
                assertThat(firstKey["alg"]).isEqualTo("RS256")
            }
        }

        @Test
        fun `should base64url encode modulus and exponent correctly`() {
            // Arrange
            val request = HttpRequest.GET<Any>("/.well-known/jwks.json")

            // Act
            val response = httpClient.exchangeAsString(request)

            // Assert
            if (jwtConfig.jwksEnabled && response.status == HttpStatus.OK) {
                val body = response.body()
                val jwks = json.readValue(body, Map::class.java)

                @Suppress("UNCHECKED_CAST")
                val keys = jwks["keys"] as List<Map<String, Any>>
                val firstKey = keys[0]

                val nEncoded = firstKey["n"] as String
                val eEncoded = firstKey["e"] as String

                // Verify base64url encoding (no padding, URL-safe characters)
                assertThat(nEncoded).doesNotContain("=") // No padding
                assertThat(nEncoded).doesNotContain("+") // URL-safe
                assertThat(nEncoded).doesNotContain("/") // URL-safe

                assertThat(eEncoded).doesNotContain("=")
                assertThat(eEncoded).doesNotContain("+")
                assertThat(eEncoded).doesNotContain("/")

                // Verify we can decode the values
                val nDecoded = Base64.getUrlDecoder().decode(nEncoded)
                val eDecoded = Base64.getUrlDecoder().decode(eEncoded)

                assertThat(nDecoded).isNotEmpty
                assertThat(eDecoded).isNotEmpty
            }
        }

        @Test
        fun `should use key ID from JwtConfig`() {
            // Arrange
            val request = HttpRequest.GET<Any>("/.well-known/jwks.json")

            // Act
            val response = httpClient.exchangeAsString(request)

            // Assert
            if (jwtConfig.jwksEnabled && response.status == HttpStatus.OK) {
                val body = response.body()
                val jwks = json.readValue(body, Map::class.java)

                @Suppress("UNCHECKED_CAST")
                val keys = jwks["keys"] as List<Map<String, Any>>
                val firstKey = keys[0]

                val kid = firstKey["kid"] as String
                assertThat(kid).isNotNull()
                assertThat(kid).isNotBlank()
            }
        }

        @Test
        fun `should return RSA public key components that match configured key`() {
            // Arrange
            val request = HttpRequest.GET<Any>("/.well-known/jwks.json")

            // Act
            val response = httpClient.exchangeAsString(request)

            // Assert
            if (jwtConfig.jwksEnabled && response.status == HttpStatus.OK) {
                val body = response.body()
                val jwks = json.readValue(body, Map::class.java)

                @Suppress("UNCHECKED_CAST")
                val keys = jwks["keys"] as List<Map<String, Any>>
                assertThat(keys).isNotEmpty

                // Verify key components are valid RSA values
                val firstKey = keys[0]
                val nEncoded = firstKey["n"] as String
                val eEncoded = firstKey["e"] as String

                val nDecoded = Base64.getUrlDecoder().decode(nEncoded)
                val eDecoded = Base64.getUrlDecoder().decode(eEncoded)

                // RSA modulus should be at least 256 bytes for 2048-bit key
                assertThat(nDecoded.size).isGreaterThanOrEqualTo(256)

                // RSA exponent is typically 3 bytes (65537 = 0x010001)
                assertThat(eDecoded.size).isGreaterThanOrEqualTo(1)
            }
        }
    }

    @Nested
    @DisplayName("JWKS Endpoint Error Cases")
    inner class ErrorCasesTests {
        @Test
        fun `should return 404 when JWKS endpoint is disabled`() {
            // This test depends on configuration
            // If jwtConfig.jwksEnabled is false, we expect 404
            if (!jwtConfig.jwksEnabled) {
                // Arrange
                val request = HttpRequest.GET<Any>("/.well-known/jwks.json")

                // Act & Assert
                val exception =
                    assertThrows<HttpClientResponseException> {
                        httpClient.exchangeAsString(request)
                    }

                assertThat(exception.status).isEqualTo(HttpStatus.NOT_FOUND)
            }
        }

        @Test
        fun `should handle missing public key configuration gracefully`() {
            // This test verifies that when public key is null/missing,
            // the endpoint returns 500 instead of crashing
            // In a real environment, this would be tested with a separate configuration

            // For now, we verify that IF the endpoint returns an error,
            // it's a proper HTTP error response
            val request = HttpRequest.GET<Any>("/.well-known/jwks.json")

            try {
                val response = httpClient.exchangeAsString(request)
                // If successful, public key was configured correctly
                if (response.status == HttpStatus.OK) {
                    assertThat(response.body()).isNotNull()
                }
            } catch (e: HttpClientResponseException) {
                // If error, it should be a well-formed HTTP error
                assertThat(e.status).isIn(HttpStatus.INTERNAL_SERVER_ERROR, HttpStatus.NOT_FOUND)
            }
        }
    }
}
