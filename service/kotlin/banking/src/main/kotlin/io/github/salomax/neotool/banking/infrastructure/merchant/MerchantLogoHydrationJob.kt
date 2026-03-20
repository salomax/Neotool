package io.github.salomax.neotool.banking.infrastructure.merchant

import io.github.salomax.neotool.banking.domain.issuer.port.AssetUploadPort
import io.github.salomax.neotool.banking.domain.transaction.MerchantRepository
import io.github.salomax.neotool.banking.infrastructure.logodev.LogoDevClient
import io.micronaut.context.annotation.Requires
import io.micronaut.context.event.StartupEvent
import io.micronaut.runtime.event.annotation.EventListener
import jakarta.inject.Singleton
import mu.KotlinLogging

/**
 * Startup job that fetches merchant logos from logo.dev and stores them in the
 * Asset service, indexed by domain in the merchant_logo_cache table.
 *
 * Design:
 *  - One logo per distinct domain — multiple merchants sharing a domain reuse the same asset.
 *  - Idempotent: only processes domains absent from merchant_logo_cache.
 *  - Runs on a virtual thread to avoid blocking app startup.
 *  - Rate-limited to ~5 req/s (200 ms sleep between requests).
 *
 * Disable in tests or environments without logo.dev access:
 *   banking.merchant.logo-hydration.enabled: false
 */
@Singleton
@Requires(property = "banking.merchant.logo-hydration.enabled", value = "true", defaultValue = "true")
@Requires(beans = [LogoDevClient::class, AssetUploadPort::class])
open class MerchantLogoHydrationJob(
    private val merchantRepository: MerchantRepository,
    private val logoDevClient: LogoDevClient,
    private val assetUploadPort: AssetUploadPort,
) {
    private val logger = KotlinLogging.logger {}

    @EventListener
    fun onStartup(event: StartupEvent) {
        Thread.ofVirtual()
            .name("merchant-logo-hydration")
            .start { hydrate() }
    }

    private fun hydrate() {
        val pending = merchantRepository.findDistinctDomainsWithoutLogoCache()
        if (pending.isEmpty()) {
            logger.debug { "MerchantLogoHydration: all domains already cached" }
            return
        }

        logger.info { "MerchantLogoHydration: starting — ${pending.size} domains pending" }
        var success = 0
        var failures = 0

        pending.forEach { domain ->
            runCatching {
                val bytes = logoDevClient.fetch(domain)
                val assetId =
                    assetUploadPort.upload(
                        key = "merchants/$domain.png",
                        bytes = bytes,
                        contentType = "image/png",
                    )
                merchantRepository.saveLogoCache(domain, assetId)
                success++
                Thread.sleep(200) // respect logo.dev rate limit (~5 req/s on free tier)
            }.onFailure { e ->
                failures++
                logger.warn { "MerchantLogoHydration: failed for domain='$domain': ${e.message}" }
            }
        }

        logger.info { "MerchantLogoHydration: done — $success succeeded, $failures failed" }
    }
}
