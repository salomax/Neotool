package io.github.salomax.neotool.comms.template.registry

import java.util.Locale

/**
 * Utility for resolving locale fallback chains.
 *
 * Implements fallback strategy:
 * 1. Try exact match (e.g., "pt-BR")
 * 2. Try language only (e.g., "pt")
 * 3. Try default locale (e.g., "en")
 */
object LocaleResolver {
    /**
     * Resolve locale with fallback chain.
     *
     * @param requestedLocale The requested locale
     * @param availableLocales Set of available locales in template
     * @param defaultLocale Default locale to use if no match found
     * @return Resolved locale from availableLocales, or null if no match
     */
    fun resolve(
        requestedLocale: Locale,
        availableLocales: Set<Locale>,
        defaultLocale: Locale,
    ): Locale? {
        // 1. Try exact match
        if (availableLocales.contains(requestedLocale)) {
            return requestedLocale
        }

        // 2. Try language only (e.g., pt-BR -> pt)
        val languageOnly = Locale.Builder().setLanguage(requestedLocale.language).build()
        if (availableLocales.contains(languageOnly)) {
            return languageOnly
        }

        // 3. Try default locale
        if (availableLocales.contains(defaultLocale)) {
            return defaultLocale
        }

        // No match found
        return null
    }

    /**
     * Generate fallback chain for a locale.
     * Useful for logging/debugging.
     *
     * @param locale The locale
     * @param defaultLocale Default locale
     * @return List of locales to try in order
     */
    fun getFallbackChain(
        locale: Locale,
        defaultLocale: Locale,
    ): List<Locale> {
        val chain = mutableListOf<Locale>()
        chain.add(locale)
        if (locale.language.isNotEmpty() && locale.country.isNotEmpty()) {
            chain.add(Locale.Builder().setLanguage(locale.language).build())
        }
        val languageOnly = Locale.Builder().setLanguage(locale.language).build()
        if (defaultLocale != locale && defaultLocale != languageOnly) {
            chain.add(defaultLocale)
        }
        return chain
    }
}
