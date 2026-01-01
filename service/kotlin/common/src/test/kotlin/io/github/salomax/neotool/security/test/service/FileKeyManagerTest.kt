package io.github.salomax.neotool.security.test.service

import io.github.salomax.neotool.security.config.JwtConfig
import io.github.salomax.neotool.security.service.FileKeyManager
import io.github.salomax.neotool.security.service.JwtKeyManager
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("FileKeyManager Tests")
class FileKeyManagerTest {

    @Test
    fun `should return true for isAvailable`() {
        val config = JwtConfig()
        val manager = FileKeyManager(config)

        assertThat(manager.isAvailable()).isTrue()
    }

    @Test
    fun `should return null when no keys configured`() {
        val config = JwtConfig()
        val manager = FileKeyManager(config)

        assertThat(manager.getPrivateKey("test")).isNull()
        assertThat(manager.getPublicKey("test")).isNull()
    }

    @Test
    fun `should load keys from inline configuration`() {
        val keyPair = JwtKeyManager.generateKeyPair(2048)
        val (privateKeyPem, publicKeyPem) = JwtKeyManager.keyPairToPem(keyPair)

        val config = JwtConfig(
            privateKey = privateKeyPem,
            publicKey = publicKeyPem,
        )
        val manager = FileKeyManager(config)

        val privateKey = manager.getPrivateKey("test")
        val publicKey = manager.getPublicKey("test")

        assertThat(privateKey).isNotNull()
        assertThat(publicKey).isNotNull()
    }

    @Test
    fun `should return secret from config`() {
        val config = JwtConfig(secret = "test-secret-key-for-hs256-algorithm")
        val manager = FileKeyManager(config)

        val secret = manager.getSecret("test")

        assertThat(secret).isEqualTo("test-secret-key-for-hs256-algorithm")
    }

    @Test
    fun `should return null for default secret`() {
        val config = JwtConfig() // Uses default secret
        val manager = FileKeyManager(config)

        val secret = manager.getSecret("test")

        // Default secret should be filtered out
        assertThat(secret).isNull()
    }
}

