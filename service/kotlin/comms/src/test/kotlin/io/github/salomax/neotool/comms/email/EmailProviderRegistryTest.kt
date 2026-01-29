package io.github.salomax.neotool.comms.email

import io.github.salomax.neotool.comms.email.provider.EmailProvider
import io.github.salomax.neotool.comms.email.provider.EmailProviderConfig
import io.github.salomax.neotool.comms.email.provider.EmailProviderRegistry
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class EmailProviderRegistryTest {
    private class TestProvider(override val id: String) : EmailProvider {
        override fun send(request: io.github.salomax.neotool.comms.email.events.EmailSendRequestedPayload) =
            io.github.salomax.neotool.comms.email.provider.EmailProviderResult(providerId = id)
    }

    @Test
    fun `resolves configured provider`() {
        val providers = listOf(TestProvider("a"), TestProvider("b"))
        val registry = EmailProviderRegistry(providers, EmailProviderConfig(provider = "b"))

        val resolved = registry.resolve()

        assertThat(resolved.id).isEqualTo("b")
    }

    @Test
    fun `falls back to single provider when configured id not found`() {
        val providers = listOf(TestProvider("only"))
        val registry = EmailProviderRegistry(providers, EmailProviderConfig(provider = "missing"))

        val resolved = registry.resolve()

        assertThat(resolved.id).isEqualTo("only")
    }

    @Test
    fun `throws when configured provider not found and multiple providers exist`() {
        val providers = listOf(TestProvider("a"), TestProvider("b"))
        val registry = EmailProviderRegistry(providers, EmailProviderConfig(provider = "missing"))

        assertThatThrownBy { registry.resolve() }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("Email provider not found")
    }
}
