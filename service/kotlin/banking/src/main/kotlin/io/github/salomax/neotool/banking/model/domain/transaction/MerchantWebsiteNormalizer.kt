package io.github.salomax.neotool.banking.model.domain.transaction

object MerchantWebsiteNormalizer {
    private val domainRegex =
        Regex("^(?!-)(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\\.)+[a-z]{2,63}$")

    fun normalizeDomain(raw: String?): String? {
        val value = raw?.trim()?.ifEmpty { return null } ?: return null

        val withoutScheme = value.replace(Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://"), "")
        val hostCandidate =
            withoutScheme
                .substringBefore('/')
                .substringBefore('?')
                .substringBefore('#')
                .trim()
                .trimEnd('.')
                .lowercase()
                .removePrefix("www.")
                .ifEmpty { return null }

        if (hostCandidate.length > 200) return null
        if (!domainRegex.matches(hostCandidate)) return null
        return hostCandidate
    }
}
