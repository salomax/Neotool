package io.github.salomax.neotool.banking.service.transaction

import io.github.salomax.neotool.banking.model.domain.transaction.MerchantWebsiteNormalizer
import io.micronaut.context.annotation.Property
import jakarta.inject.Singleton
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

@Singleton
class MerchantLogoUrlBuilder(
    @Property(name = "banking.merchant-logo.token", defaultValue = "")
    private val logoDevToken: String,
) {
    fun hasToken(): Boolean = logoDevToken.isNotBlank()

    fun buildLogoDevUrl(domainOrWebsite: String): String? {
        if (!hasToken()) return null
        val normalizedDomain = MerchantWebsiteNormalizer.normalizeDomain(domainOrWebsite) ?: return null
        val encodedDomain = URLEncoder.encode(normalizedDomain, StandardCharsets.UTF_8)
        val encodedToken = URLEncoder.encode(logoDevToken, StandardCharsets.UTF_8)
        return "https://img.logo.dev/$encodedDomain?token=$encodedToken&size=80&format=png"
    }
}
