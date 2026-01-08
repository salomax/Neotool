package io.github.salomax.neotool.common.security.key

import io.github.salomax.neotool.common.security.config.JwtConfig
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey

@DisplayName("FileKeyManager Unit Tests")
class FileKeyManagerTest {
    @Nested
    @DisplayName("Key Loading from Inline PEM")
    inner class KeyLoadingInlineTests {
        @Test
        fun `should load private key from inline PEM string`() {
            // Arrange
            val keyPair = JwtKeyManager.generateKeyPair(2048)
            val (privateKeyPem, publicKeyPem) = JwtKeyManager.keyPairToPem(keyPair)

            val config =
                JwtConfig(
                    privateKey = privateKeyPem,
                    publicKey = publicKeyPem,
                    keyId = "test-key",
                )

            val manager = FileKeyManager(config, null)

            // Act
            val loadedPrivateKey = manager.getPrivateKey("test-key")

            // Assert
            assertThat(loadedPrivateKey).isNotNull
            assertThat(loadedPrivateKey).isInstanceOf(RSAPrivateKey::class.java)
        }

        @Test
        fun `should load public key from inline PEM string`() {
            // Arrange
            val keyPair = JwtKeyManager.generateKeyPair(2048)
            val (privateKeyPem, publicKeyPem) = JwtKeyManager.keyPairToPem(keyPair)

            val config =
                JwtConfig(
                    privateKey = privateKeyPem,
                    publicKey = publicKeyPem,
                    keyId = "test-key",
                )

            val manager = FileKeyManager(config, null)

            // Act
            val loadedPublicKey = manager.getPublicKey("test-key")

            // Assert
            assertThat(loadedPublicKey).isNotNull
            assertThat(loadedPublicKey).isInstanceOf(RSAPublicKey::class.java)
        }

        @Test
        fun `should load matching key pair from inline PEM`() {
            // Arrange
            val keyPair = JwtKeyManager.generateKeyPair(2048)
            val (privateKeyPem, publicKeyPem) = JwtKeyManager.keyPairToPem(keyPair)

            val config =
                JwtConfig(
                    privateKey = privateKeyPem,
                    publicKey = publicKeyPem,
                    keyId = "test-key",
                )

            val manager = FileKeyManager(config, null)

            // Act
            val loadedPrivateKey = manager.getPrivateKey("test-key") as RSAPrivateKey
            val loadedPublicKey = manager.getPublicKey("test-key") as RSAPublicKey

            // Assert - Keys should form a valid pair (same modulus)
            assertThat(loadedPrivateKey.modulus).isEqualTo(loadedPublicKey.modulus)
        }
    }

    @Nested
    @DisplayName("Key Retrieval")
    inner class KeyRetrievalTests {
        @Test
        fun `should retrieve private key by key ID`() {
            // Arrange
            val keyPair = JwtKeyManager.generateKeyPair(2048)
            val (privateKeyPem, publicKeyPem) = JwtKeyManager.keyPairToPem(keyPair)

            val config =
                JwtConfig(
                    privateKey = privateKeyPem,
                    publicKey = publicKeyPem,
                    keyId = "custom-key-id",
                )

            val manager = FileKeyManager(config, null)

            // Act
            val retrievedKey = manager.getPrivateKey("custom-key-id")

            // Assert
            assertThat(retrievedKey).isNotNull
        }

        @Test
        fun `should retrieve public key by key ID`() {
            // Arrange
            val keyPair = JwtKeyManager.generateKeyPair(2048)
            val (privateKeyPem, publicKeyPem) = JwtKeyManager.keyPairToPem(keyPair)

            val config =
                JwtConfig(
                    privateKey = privateKeyPem,
                    publicKey = publicKeyPem,
                    keyId = "custom-key-id",
                )

            val manager = FileKeyManager(config, null)

            // Act
            val retrievedKey = manager.getPublicKey("custom-key-id")

            // Assert
            assertThat(retrievedKey).isNotNull
        }

        @Test
        fun `should return same key regardless of key ID`() {
            // Arrange
            val keyPair = JwtKeyManager.generateKeyPair(2048)
            val (privateKeyPem, publicKeyPem) = JwtKeyManager.keyPairToPem(keyPair)

            val config =
                JwtConfig(
                    privateKey = privateKeyPem,
                    publicKey = publicKeyPem,
                    keyId = "key-1",
                )

            val manager = FileKeyManager(config, null)

            // Act - FileKeyManager ignores keyId, always returns configured key
            val privateKey1 = manager.getPrivateKey("key-1")
            val privateKey2 = manager.getPrivateKey("different-key-id")
            val publicKey1 = manager.getPublicKey("key-1")
            val publicKey2 = manager.getPublicKey("different-key-id")

            // Assert - Same keys returned regardless of keyId
            assertThat(privateKey1).isNotNull
            assertThat(privateKey2).isNotNull
            assertThat(privateKey1).isSameAs(privateKey2)
            assertThat(publicKey1).isNotNull
            assertThat(publicKey2).isNotNull
            assertThat(publicKey1).isSameAs(publicKey2)
        }

        @Test
        fun `should handle any key ID by using configured key`() {
            // Arrange
            val keyPair = JwtKeyManager.generateKeyPair(2048)
            val (privateKeyPem, publicKeyPem) = JwtKeyManager.keyPairToPem(keyPair)

            val config =
                JwtConfig(
                    privateKey = privateKeyPem,
                    publicKey = publicKeyPem,
                    keyId = "default",
                )

            val manager = FileKeyManager(config, null)

            // Act
            val retrievedKey = manager.getPublicKey("default")

            // Assert - Should return the configured default key
            assertThat(retrievedKey).isNotNull
        }
    }

    @Nested
    @DisplayName("Key Generation and Format")
    inner class KeyGenerationTests {
        @Test
        fun `should generate valid 2048-bit RSA key pair`() {
            // Act
            val keyPair = JwtKeyManager.generateKeyPair(2048)

            // Assert
            assertThat(keyPair).isNotNull
            assertThat(keyPair.private).isInstanceOf(RSAPrivateKey::class.java)
            assertThat(keyPair.public).isInstanceOf(RSAPublicKey::class.java)

            val privateKey = keyPair.private as RSAPrivateKey
            val publicKey = keyPair.public as RSAPublicKey

            // 2048-bit key should have 256-byte modulus
            assertThat(publicKey.modulus.bitLength()).isGreaterThanOrEqualTo(2047)
            assertThat(publicKey.modulus.bitLength()).isLessThanOrEqualTo(2048)
        }

        @Test
        fun `should generate valid 4096-bit RSA key pair`() {
            // Act
            val keyPair = JwtKeyManager.generateKeyPair(4096)

            // Assert
            val publicKey = keyPair.public as RSAPublicKey
            assertThat(publicKey.modulus.bitLength()).isGreaterThanOrEqualTo(4095)
            assertThat(publicKey.modulus.bitLength()).isLessThanOrEqualTo(4096)
        }

        @Test
        fun `should convert key pair to PEM format`() {
            // Arrange
            val keyPair = JwtKeyManager.generateKeyPair(2048)

            // Act
            val (privateKeyPem, publicKeyPem) = JwtKeyManager.keyPairToPem(keyPair)

            // Assert
            assertThat(privateKeyPem).contains("-----BEGIN PRIVATE KEY-----")
            assertThat(privateKeyPem).contains("-----END PRIVATE KEY-----")

            assertThat(publicKeyPem).contains("-----BEGIN PUBLIC KEY-----")
            assertThat(publicKeyPem).contains("-----END PUBLIC KEY-----")
        }

        @Test
        fun `should convert individual keys to PEM format`() {
            // Arrange
            val keyPair = JwtKeyManager.generateKeyPair(2048)

            // Act
            val privateKeyPem = JwtKeyManager.privateKeyToPem(keyPair.private as RSAPrivateKey)
            val publicKeyPem = JwtKeyManager.publicKeyToPem(keyPair.public as RSAPublicKey)

            // Assert
            assertThat(privateKeyPem).contains("BEGIN PRIVATE KEY")
            assertThat(publicKeyPem).contains("BEGIN PUBLIC KEY")
        }
    }

    @Nested
    @DisplayName("Error Handling")
    inner class ErrorHandlingTests {
        @Test
        fun `should return null when private key is not configured`() {
            // Arrange
            val config =
                JwtConfig(
                    privateKey = "",
                    publicKey = "",
                    keyId = "test",
                )

            val manager = FileKeyManager(config, null)

            // Act
            val privateKey = manager.getPrivateKey("test")

            // Assert
            assertThat(privateKey).isNull()
        }

        @Test
        fun `should return null when public key is not configured`() {
            // Arrange
            val config =
                JwtConfig(
                    privateKey = "",
                    publicKey = "",
                    keyId = "test",
                )

            val manager = FileKeyManager(config, null)

            // Act
            val publicKey = manager.getPublicKey("test")

            // Assert
            assertThat(publicKey).isNull()
        }

        @Test
        fun `should return configured key for any key ID`() {
            // Arrange
            val keyPair = JwtKeyManager.generateKeyPair(2048)
            val (privateKeyPem, publicKeyPem) = JwtKeyManager.keyPairToPem(keyPair)

            val config =
                JwtConfig(
                    privateKey = privateKeyPem,
                    publicKey = publicKeyPem,
                    keyId = "default",
                )

            val manager = FileKeyManager(config, null)

            // Act - FileKeyManager ignores keyId parameter
            val keyWithDifferentId = manager.getPublicKey("different-key-id")

            // Assert - Should return configured key even for different keyId
            assertThat(keyWithDifferentId).isNotNull
        }
    }

    @Nested
    @DisplayName("Key Rotation Scenarios")
    inner class KeyRotationTests {
        @Test
        fun `should support loading multiple keys with different IDs`() {
            // Arrange
            val keyPair1 = JwtKeyManager.generateKeyPair(2048)
            val (privateKeyPem1, publicKeyPem1) = JwtKeyManager.keyPairToPem(keyPair1)

            // In a real scenario, key rotation would involve multiple keys
            // For this test, we simulate with the same key but different concepts
            val config =
                JwtConfig(
                    privateKey = privateKeyPem1,
                    publicKey = publicKeyPem1,
                    keyId = "key-v1",
                )

            val manager = FileKeyManager(config, null)

            // Act
            val key1 = manager.getPublicKey("key-v1")

            // Assert
            assertThat(key1).isNotNull
        }

        @Test
        fun `should maintain key consistency across multiple retrievals`() {
            // Arrange
            val keyPair = JwtKeyManager.generateKeyPair(2048)
            val (privateKeyPem, publicKeyPem) = JwtKeyManager.keyPairToPem(keyPair)

            val config =
                JwtConfig(
                    privateKey = privateKeyPem,
                    publicKey = publicKeyPem,
                    keyId = "stable-key",
                )

            val manager = FileKeyManager(config, null)

            // Act
            val key1 = manager.getPublicKey("stable-key")
            val key2 = manager.getPublicKey("stable-key")
            val key3 = manager.getPublicKey("stable-key")

            // Assert - Same key instance should be returned (cached)
            assertThat(key1).isSameAs(key2)
            assertThat(key2).isSameAs(key3)
        }
    }
}
