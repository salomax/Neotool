package io.github.salomax.neotool.banking.service.transaction

import io.github.salomax.neotool.banking.client.assets.AssetsServiceClient
import io.github.salomax.neotool.banking.client.assets.PresignedUrlHttpClient
import io.github.salomax.neotool.banking.repo.transaction.MerchantRepository
import io.micronaut.context.annotation.Property
import jakarta.inject.Singleton
import mu.KotlinLogging
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.concurrent.CompletableFuture

@Singleton
open class MerchantLogoHydrationService(
    private val merchantRepository: MerchantRepository,
    private val assetsServiceClient: AssetsServiceClient,
    private val presignedUrlHttpClient: PresignedUrlHttpClient,
    private val merchantLogoUrlBuilder: MerchantLogoUrlBuilder,
    @Property(name = "banking.assets-service.merchant-logo-namespace")
    private val merchantLogoNamespace: String,
) {
    private val logger = KotlinLogging.logger {}

    open fun hydrateForDomainAsync(domain: String) {
        if (!merchantLogoUrlBuilder.hasToken()) return
        CompletableFuture.runAsync {
            runCatching { hydrateForDomain(domain) }
                .onFailure { error ->
                    logger.warn(error) { "Merchant logo hydration failed for domain=$domain" }
                }
        }
    }

    open fun hydrateMissingLogos(limit: Int = 25) {
        if (!merchantLogoUrlBuilder.hasToken()) return
        merchantRepository.findDomainsMissingLogo(limit.coerceIn(1, 200)).forEach { domain ->
            hydrateForDomainAsync(domain)
        }
    }

    open fun hydrateForDomain(domain: String) {
        val normalizedDomain = MerchantWebsiteNormalizer.normalizeDomain(domain) ?: return
        val logoUrl = merchantLogoUrlBuilder.buildLogoDevUrl(normalizedDomain) ?: return
        val now = Instant.now()
        val cached = merchantRepository.findLogoCacheByDomain(normalizedDomain)
        if (cached != null && cached.fetchedAt.isAfter(now.minus(30, ChronoUnit.DAYS))) {
            return
        }

        val bytes = presignedUrlHttpClient.get(logoUrl)
        if (bytes.isEmpty()) {
            logger.debug { "Merchant logo hydration returned empty payload for domain=$normalizedDomain" }
            return
        }

        val upload =
            assetsServiceClient.createAssetUpload(
                namespace = merchantLogoNamespace,
                mimeType = "image/png",
                originalFilename = "$normalizedDomain.png",
                sizeBytes = bytes.size.toLong(),
            )
        presignedUrlHttpClient.put(upload.uploadUrl, bytes, "image/png")
        assetsServiceClient.confirmAssetUpload(upload.id)
        merchantRepository.upsertLogoCache(normalizedDomain, upload.id, now)
    }
}
