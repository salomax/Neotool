package io.github.salomax.neotool.banking.service.transaction

import io.github.salomax.neotool.banking.client.assets.AssetsServiceClient
import io.github.salomax.neotool.banking.client.assets.PresignedUrlHttpClient
import io.github.salomax.neotool.banking.repo.transaction.MerchantRepository
import io.micronaut.context.annotation.Property
import jakarta.inject.Singleton
import mu.KotlinLogging
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.concurrent.CompletableFuture

@Singleton
open class MerchantLogoHydrationService(
    private val merchantRepository: MerchantRepository,
    private val assetsServiceClient: AssetsServiceClient,
    private val presignedUrlHttpClient: PresignedUrlHttpClient,
    @Property(name = "banking.merchant-logo.token", defaultValue = "")
    private val logoDevToken: String,
    @Property(name = "banking.assets-service.merchant-logo-namespace", defaultValue = "merchant-logos")
    private val merchantLogoNamespace: String,
) {
    private val logger = KotlinLogging.logger {}

    open fun hydrateForDomainAsync(domain: String) {
        if (logoDevToken.isBlank()) return
        CompletableFuture.runAsync {
            runCatching { hydrateForDomain(domain) }
                .onFailure { error ->
                    logger.warn(error) { "Merchant logo hydration failed for domain=$domain" }
                }
        }
    }

    open fun hydrateMissingLogos(limit: Int = 25) {
        if (logoDevToken.isBlank()) return
        merchantRepository.findDomainsMissingLogo(limit.coerceIn(1, 200)).forEach { domain ->
            hydrateForDomainAsync(domain)
        }
    }

    open fun hydrateForDomain(domain: String) {
        if (logoDevToken.isBlank()) return
        val normalizedDomain = MerchantWebsiteNormalizer.normalizeDomain(domain) ?: return
        val now = Instant.now()
        val cached = merchantRepository.findLogoCacheByDomain(normalizedDomain)
        if (cached != null && cached.fetchedAt.isAfter(now.minus(30, ChronoUnit.DAYS))) {
            return
        }

        val logoUrl = buildLogoUrl(normalizedDomain)
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
        merchantRepository.upsertLogoCache(normalizedDomain, upload.id.toString(), now)
    }

    private fun buildLogoUrl(domain: String): String {
        val encodedDomain = URLEncoder.encode(domain, StandardCharsets.UTF_8)
        val encodedToken = URLEncoder.encode(logoDevToken, StandardCharsets.UTF_8)
        return "https://img.logo.dev/$encodedDomain?token=$encodedToken&size=80&format=png"
    }
}
