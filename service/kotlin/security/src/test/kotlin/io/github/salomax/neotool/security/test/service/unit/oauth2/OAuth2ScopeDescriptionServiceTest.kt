package io.github.salomax.neotool.security.test.service.unit.oauth2

import io.github.salomax.neotool.security.service.oauth2.DefaultOAuth2ScopePermissionMapper
import io.github.salomax.neotool.security.service.oauth2.OAuth2ScopeDescriptionService
import io.github.salomax.neotool.security.service.oauth2.OAuth2Scopes
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class OAuth2ScopeDescriptionServiceTest {
    private val service = OAuth2ScopeDescriptionService(DefaultOAuth2ScopePermissionMapper())

    @Test
    fun `every scope has non fallback descriptions in supported locales`() {
        service.validateDescriptions()

        listOf("en", "pt", "pt-BR").forEach { locale ->
            val descriptions = service.describe(OAuth2Scopes.all.joinToString(" "), locale)

            assertThat(descriptions).hasSize(OAuth2Scopes.all.size)
            assertThat(descriptions.map { it.description })
                .doesNotContain(OAuth2ScopeDescriptionService.FALLBACK_DESCRIPTION)
        }
    }

    @Test
    fun `cashflow read description discloses all accounts and investments`() {
        val description =
            service.describe(OAuth2Scopes.FINANCE_CASHFLOW_READ, "en")
                .single()
                .description

        assertThat(description).contains("all accounts", "investment accounts")
    }

    @Test
    fun `accept language list resolves first language tag before q values`() {
        val description =
            service.describe(OAuth2Scopes.FINANCE_BILLS_READ, "pt-BR,pt;q=0.9,en;q=0.8")
                .single()
                .description

        assertThat(description).isEqualTo("Ler todo o seu histórico de faturas do cartão de crédito.")
    }
}
