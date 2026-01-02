package io.github.salomax.neotool.security.test.service

import io.github.salomax.neotool.common.security.config.JwtConfig
import io.github.salomax.neotool.common.security.config.VaultConfig
import io.github.salomax.neotool.common.security.key.FileKeyManager
import io.github.salomax.neotool.common.security.key.KeyManagerFactory
import io.github.salomax.neotool.common.security.key.VaultKeyManager
import io.github.salomax.neotool.common.security.vault.VaultClient
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@DisplayName("KeyManagerFactory Tests")
class KeyManagerFactoryTest {
    @Test
    fun `should return FileKeyManager when Vault is not configured`() {
        val vaultConfig = VaultConfig(enabled = false)
        val fileKeyManager = FileKeyManager(JwtConfig())

        val factory =
            KeyManagerFactory(
                vaultConfig = vaultConfig,
                vaultClient = null,
                fileKeyManager = fileKeyManager,
                vaultKeyManager = null,
            )

        val manager = factory.getKeyManager()

        assertThat(manager).isInstanceOf(FileKeyManager::class.java)
    }

    @Test
    fun `should return FileKeyManager when Vault is enabled but not available`() {
        val vaultConfig = VaultConfig(enabled = true, token = "test-token")
        val vaultClient = mock<VaultClient>()
        whenever(vaultClient.isAvailable()).thenReturn(false)
        val fileKeyManager = FileKeyManager(JwtConfig())

        val factory =
            KeyManagerFactory(
                vaultConfig = vaultConfig,
                vaultClient = vaultClient,
                fileKeyManager = fileKeyManager,
                vaultKeyManager = null,
            )

        val manager = factory.getKeyManager()

        assertThat(manager).isInstanceOf(FileKeyManager::class.java)
    }

    @Test
    fun `should return VaultKeyManager when Vault is enabled and available`() {
        val vaultConfig = VaultConfig(enabled = true, token = "test-token")
        val vaultClient = mock<VaultClient>()
        whenever(vaultClient.isAvailable()).thenReturn(true)
        val fileKeyManager = FileKeyManager(JwtConfig())
        val vaultKeyManager = mock<VaultKeyManager>()

        val factory =
            KeyManagerFactory(
                vaultConfig = vaultConfig,
                vaultClient = vaultClient,
                fileKeyManager = fileKeyManager,
                vaultKeyManager = vaultKeyManager,
            )

        val manager = factory.getKeyManager()

        assertThat(manager).isInstanceOf(VaultKeyManager::class.java)
    }
}
