package io.github.salomax.neotool.banking.graphql.resolver

import io.github.salomax.neotool.banking.graphql.account.*
import io.github.salomax.neotool.banking.graphql.billimport.*
import io.github.salomax.neotool.banking.graphql.import.*
import io.github.salomax.neotool.banking.graphql.investment.*
import io.github.salomax.neotool.banking.graphql.issuer.*
import io.github.salomax.neotool.banking.graphql.merchant.*
import io.github.salomax.neotool.banking.graphql.transaction.*
import io.github.salomax.neotool.banking.graphql.config.*
import io.github.salomax.neotool.banking.domain.issuer.Issuer
import io.github.salomax.neotool.banking.domain.issuer.IssuerCategory
import io.github.salomax.neotool.banking.domain.issuer.IssuerRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.UUID

@DisplayName("IssuerResolver")
class IssuerResolverTest {
    private lateinit var issuerRepository: IssuerRepository
    private lateinit var resolver: IssuerResolver

    @BeforeEach
    fun setUp() {
        issuerRepository = mock()
        resolver = IssuerResolver(issuerRepository)
    }

    @Test
    fun `listIssuers without categories returns all active issuers`() {
        val issuer =
            Issuer(
                id = UUID.randomUUID(),
                name = "XP Investimentos",
                domain = "xp.com.br",
                compeCode = "102",
                ispb = "02332886",
                logoKey = "issuers/xp.png",
                category = IssuerCategory.BROKERAGE,
            )
        whenever(issuerRepository.findAllActive()).thenReturn(listOf(issuer))

        val result = resolver.listIssuers()

        assertThat(result).hasSize(1)
        assertThat(result.first().name).isEqualTo("XP Investimentos")
        assertThat(result.first().category).isEqualTo(IssuerCategory.BROKERAGE)
        verify(issuerRepository).findAllActive()
        verify(issuerRepository, never()).findActiveByCategories(any())
    }

    @Test
    fun `listIssuers with categories uses filtered repository query`() {
        val categories = listOf(IssuerCategory.CRYPTO_EXCHANGE)
        val issuer =
            Issuer(
                id = UUID.randomUUID(),
                name = "Binance",
                domain = "binance.com",
                compeCode = null,
                ispb = null,
                logoKey = null,
                category = IssuerCategory.CRYPTO_EXCHANGE,
            )
        whenever(issuerRepository.findActiveByCategories(eq(categories))).thenReturn(listOf(issuer))

        val result = resolver.listIssuers(categories)

        assertThat(result).hasSize(1)
        assertThat(result.first().name).isEqualTo("Binance")
        assertThat(result.first().category).isEqualTo(IssuerCategory.CRYPTO_EXCHANGE)
        verify(issuerRepository).findActiveByCategories(eq(categories))
        verify(issuerRepository, never()).findAllActive()
    }

    @Test
    fun `listIssuers with empty categories list treats as no filter`() {
        val issuer =
            Issuer(
                id = UUID.randomUUID(),
                name = "Itau",
                domain = "itau.com.br",
                compeCode = "341",
                ispb = "60701190",
                logoKey = null,
                category = IssuerCategory.BANK,
            )
        whenever(issuerRepository.findAllActive()).thenReturn(listOf(issuer))

        val result = resolver.listIssuers(emptyList())

        assertThat(result).hasSize(1)
        assertThat(result.first().category).isEqualTo(IssuerCategory.BANK)
        verify(issuerRepository).findAllActive()
        verify(issuerRepository, never()).findActiveByCategories(any())
    }
}
