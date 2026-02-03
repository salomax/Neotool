package io.github.salomax.neotool.comms.template.registry

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.Locale

class LocaleResolverTest {
    @Test
    fun `should resolve exact locale match`() {
        val available = setOf(Locale("pt", "BR"), Locale.ENGLISH)
        val resolved = LocaleResolver.resolve(Locale("pt", "BR"), available, Locale.ENGLISH)
        assertThat(resolved).isEqualTo(Locale("pt", "BR"))
    }

    @Test
    fun `should fallback to language when exact match not found`() {
        val available = setOf(Locale("pt"), Locale.ENGLISH)
        val resolved = LocaleResolver.resolve(Locale("pt", "BR"), available, Locale.ENGLISH)
        assertThat(resolved).isEqualTo(Locale("pt"))
    }

    @Test
    fun `should fallback to default locale when language not found`() {
        val available = setOf(Locale.ENGLISH)
        val resolved = LocaleResolver.resolve(Locale("pt", "BR"), available, Locale.ENGLISH)
        assertThat(resolved).isEqualTo(Locale.ENGLISH)
    }

    @Test
    fun `should return null when no locale found`() {
        val available = setOf(Locale("es"))
        val resolved = LocaleResolver.resolve(Locale("pt", "BR"), available, Locale.ENGLISH)
        assertThat(resolved).isNull()
    }

    @Test
    fun `getFallbackChain returns locale then language-only then default when locale has country`() {
        val locale = Locale("pt", "BR")
        val defaultLocale = Locale.ENGLISH
        val chain = LocaleResolver.getFallbackChain(locale, defaultLocale)
        assertThat(chain).hasSize(3)
        assertThat(chain[0]).isEqualTo(locale)
        assertThat(chain[1]).isEqualTo(Locale("pt"))
        assertThat(chain[2]).isEqualTo(defaultLocale)
    }

    @Test
    fun `getFallbackChain returns single locale when default equals requested`() {
        val locale = Locale.ENGLISH
        val chain = LocaleResolver.getFallbackChain(locale, locale)
        assertThat(chain).hasSize(1)
        assertThat(chain[0]).isEqualTo(locale)
    }

    @Test
    fun `getFallbackChain returns locale and default when language-only equals default`() {
        val locale = Locale("pt", "BR")
        val defaultLocale = Locale("pt")
        val chain = LocaleResolver.getFallbackChain(locale, defaultLocale)
        assertThat(chain).hasSize(2)
        assertThat(chain[0]).isEqualTo(locale)
        assertThat(chain[1]).isEqualTo(Locale("pt"))
    }

    @Test
    fun `getFallbackChain for locale with only language does not add duplicate language-only`() {
        val locale = Locale.GERMAN
        val defaultLocale = Locale.ENGLISH
        val chain = LocaleResolver.getFallbackChain(locale, defaultLocale)
        assertThat(chain).hasSize(2)
        assertThat(chain[0]).isEqualTo(locale)
        assertThat(chain[1]).isEqualTo(defaultLocale)
    }
}
