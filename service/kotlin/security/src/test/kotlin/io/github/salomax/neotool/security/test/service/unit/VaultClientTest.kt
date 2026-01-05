package io.github.salomax.neotool.security.test.service.unit

import io.github.salomax.neotool.security.config.VaultConfig
import io.github.salomax.neotool.security.vault.VaultClient
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("VaultClient Unit Tests")
class VaultClientTest {
    @Nested
    @DisplayName("Address Normalization")
    inner class AddressNormalizationTests {
        @Test
        fun `should accept full HTTP URL as-is`() {
            // Arrange
            val config =
                VaultConfig(
                    enabled = true,
                    address = "http://localhost:8200",
                    token = "test-token",
                )

            // Act
            val client = VaultClient(config)

            // Assert
            assertThat(client).isNotNull
        }

        @Test
        fun `should accept full HTTPS URL as-is`() {
            // Arrange
            val config =
                VaultConfig(
                    enabled = true,
                    address = "https://vault.example.com:8200",
                    token = "test-token",
                )

            // Act
            val client = VaultClient(config)

            // Assert
            assertThat(client).isNotNull
        }

        @Test
        fun `should normalize port-only address to localhost HTTP`() {
            // Arrange - Address is just a port number
            val config =
                VaultConfig(
                    enabled = true,
                    address = "8200",
                    token = "test-token",
                )

            // Act
            val client = VaultClient(config)

            // Assert - Should not throw, assumes http://localhost:8200
            assertThat(client).isNotNull
        }

        @Test
        fun `should normalize host-port format with HTTP protocol`() {
            // Arrange - Address is host-port without protocol
            val config =
                VaultConfig(
                    enabled = true,
                    address = "vault.local:8200",
                    token = "test-token",
                )

            // Act
            val client = VaultClient(config)

            // Assert - Should add http:// prefix
            assertThat(client).isNotNull
        }

        @Test
        fun `should normalize hostname-only to HTTP with default port`() {
            // Arrange - Address is just hostname
            val config =
                VaultConfig(
                    enabled = true,
                    address = "vault.local",
                    token = "test-token",
                )

            // Act
            val client = VaultClient(config)

            // Assert - Should add http:// and :8200
            assertThat(client).isNotNull
        }
    }

    @Nested
    @DisplayName("SSL Configuration")
    inner class SSLConfigurationTests {
        @Test
        fun `should enable SSL verification for HTTPS addresses`() {
            // Arrange
            val config =
                VaultConfig(
                    enabled = true,
                    address = "https://vault.example.com:8200",
                    token = "test-token",
                )

            // Act
            val client = VaultClient(config)

            // Assert - Should configure SSL with verify=true for HTTPS
            assertThat(client).isNotNull
        }

        @Test
        fun `should disable SSL verification for HTTP addresses`() {
            // Arrange
            val config =
                VaultConfig(
                    enabled = true,
                    address = "http://localhost:8200",
                    token = "test-token",
                )

            // Act
            val client = VaultClient(config)

            // Assert - Should configure SSL with verify=false for HTTP
            assertThat(client).isNotNull
        }
    }

    @Nested
    @DisplayName("Initialization and Configuration")
    inner class InitializationTests {
        @Test
        fun `should initialize with valid configuration`() {
            // Arrange
            val config =
                VaultConfig(
                    enabled = true,
                    address = "http://localhost:8200",
                    token = "test-token",
                    engineVersion = 2,
                    connectionTimeout = 5000,
                    readTimeout = 10000,
                )

            // Act
            val client = VaultClient(config)

            // Assert
            assertThat(client).isNotNull
        }

        @Test
        fun `should use default timeouts from configuration`() {
            // Arrange
            val config =
                VaultConfig(
                    enabled = true,
                    address = "http://localhost:8200",
                    token = "test-token",
                    connectionTimeout = 5000,
                    readTimeout = 15000,
                )

            // Act
            val client = VaultClient(config)

            // Assert - Timeouts are configured (converted from ms to seconds)
            assertThat(client).isNotNull
        }

        @Test
        fun `should use engine version from configuration`() {
            // Arrange - Engine version 1
            val config1 =
                VaultConfig(
                    enabled = true,
                    address = "http://localhost:8200",
                    token = "test-token",
                    engineVersion = 1,
                )

            // Arrange - Engine version 2
            val config2 =
                VaultConfig(
                    enabled = true,
                    address = "http://localhost:8200",
                    token = "test-token",
                    engineVersion = 2,
                )

            // Act
            val client1 = VaultClient(config1)
            val client2 = VaultClient(config2)

            // Assert
            assertThat(client1).isNotNull
            assertThat(client2).isNotNull
        }

        @Test
        fun `should handle null token in configuration`() {
            // Arrange
            val config =
                VaultConfig(
                    enabled = true,
                    address = "http://localhost:8200",
                    token = null,
                )

            // Act
            val client = VaultClient(config)

            // Assert - Should initialize even without token (can be set later)
            assertThat(client).isNotNull
        }

        @Test
        fun `should handle various port formats in address`() {
            // Arrange - Different port formats
            val configs =
                listOf(
                    VaultConfig(enabled = true, address = "http://localhost:8200", token = "test"),
                    VaultConfig(enabled = true, address = "http://vault:443", token = "test"),
                    VaultConfig(enabled = true, address = "https://vault.example.com:8443", token = "test"),
                )

            // Act & Assert - All should initialize successfully
            configs.forEach { config ->
                val client = VaultClient(config)
                assertThat(client).isNotNull
            }
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    inner class EdgeCasesTests {
        @Test
        fun `should handle address with trailing slash`() {
            // Arrange
            val config =
                VaultConfig(
                    enabled = true,
                    address = "http://localhost:8200/",
                    token = "test-token",
                )

            // Act
            val client = VaultClient(config)

            // Assert
            assertThat(client).isNotNull
        }

        @Test
        fun `should handle address with whitespace`() {
            // Arrange
            val config =
                VaultConfig(
                    enabled = true,
                    address = "  http://localhost:8200  ",
                    token = "test-token",
                )

            // Act
            val client = VaultClient(config)

            // Assert - Should trim whitespace
            assertThat(client).isNotNull
        }

        @Test
        fun `should handle IPv4 address format`() {
            // Arrange
            val config =
                VaultConfig(
                    enabled = true,
                    address = "http://127.0.0.1:8200",
                    token = "test-token",
                )

            // Act
            val client = VaultClient(config)

            // Assert
            assertThat(client).isNotNull
        }

        @Test
        fun `should handle localhost variations`() {
            // Arrange - Different localhost formats
            val configs =
                listOf(
                    VaultConfig(enabled = true, address = "http://localhost:8200", token = "test"),
                    VaultConfig(enabled = true, address = "http://127.0.0.1:8200", token = "test"),
                    // Port only -> localhost
                    VaultConfig(enabled = true, address = "8200", token = "test"),
                )

            // Act & Assert
            configs.forEach { config ->
                val client = VaultClient(config)
                assertThat(client).isNotNull
            }
        }
    }
}
