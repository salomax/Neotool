package io.github.salomax.neotool.comms.email.provider

import jakarta.inject.Singleton

@Singleton
class EmailProviderRegistry(
    private val providers: List<EmailProvider>,
    private val config: EmailProviderConfig,
) {
    fun resolve(): EmailProvider {
        val configuredId = config.provider
        val provider = providers.firstOrNull { it.id == configuredId }
        if (provider != null) {
            return provider
        }

        if (providers.size == 1) {
            return providers.first()
        }

        throw IllegalStateException(
            "Email provider not found: '$configuredId'. Available providers: ${providers.map { it.id }}",
        )
    }
}
