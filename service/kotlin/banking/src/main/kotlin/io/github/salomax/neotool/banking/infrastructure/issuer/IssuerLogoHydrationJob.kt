package io.github.salomax.neotool.banking.infrastructure.issuer

import io.github.salomax.neotool.banking.domain.issuer.IssuerRepository
import io.github.salomax.neotool.banking.domain.issuer.port.AssetUploadPort
import io.github.salomax.neotool.banking.infrastructure.logodev.LogoDevClient
import io.micronaut.context.annotation.Requires
import io.micronaut.context.event.StartupEvent
import io.micronaut.runtime.event.annotation.EventListener
import jakarta.inject.Singleton
import mu.KotlinLogging

/**
 * Startup job that fetches institution logos from logo.dev and stores them
 * in the public asset bucket. Runs asynchronously (virtual thread) to avoid
 * delaying app startup.
 *
 * Idempotent: only processes issuers where logo_key IS NULL AND domain IS NOT NULL.
 * Safe to re-run; already-hydrated issuers are skipped automatically.
 *
 * Disable in tests or environments without logo.dev access:
 *   banking.issuer.logo-hydration.enabled: false
 */
@Singleton
@Requires(property = "banking.issuer.logo-hydration.enabled", value = "true", defaultValue = "true")
@Requires(beans = [LogoDevClient::class, AssetUploadPort::class])
open class IssuerLogoHydrationJob(
    private val issuerRepository: IssuerRepository,
    private val logoDevClient: LogoDevClient,
    private val assetUploadPort: AssetUploadPort,
) {
    private val logger = KotlinLogging.logger {}

    @EventListener
    fun onStartup(event: StartupEvent) {
        Thread.ofVirtual()
            .name("issuer-logo-hydration")
            .start { hydrate() }
    }

    private fun hydrate() {
        val pending = issuerRepository.findAllPendingLogoHydration()
        if (pending.isEmpty()) {
            logger.debug { "IssuerLogoHydration: all issuers already hydrated" }
            return
        }

        logger.info { "IssuerLogoHydration: starting — ${pending.size} issuers pending" }
        var success = 0
        var failures = 0

        pending.forEach { issuer ->
            runCatching {
                val bytes = logoDevClient.fetch(issuer.domain!!)
                val key = assetUploadPort.upload(
                    key = "issuers/${issuer.id}.png",
                    bytes = bytes,
                    contentType = "image/png",
                )
                issuerRepository.updateLogoKey(issuer.id!!, key)
                success++
                Thread.sleep(200) // respect logo.dev rate limit (~5 req/s on free tier)
            }.onFailure { e ->
                failures++
                logger.warn { "IssuerLogoHydration: failed for '${issuer.name}' (${issuer.domain}): ${e.message}" }
            }
        }

        logger.info { "IssuerLogoHydration: done — $success succeeded, $failures failed" }
    }
}
