package io.github.salomax.neotool.banking.domain.import.nfce

import io.github.salomax.neotool.banking.service.import.nfce.DefaultNfceClient
import io.github.salomax.neotool.banking.service.import.nfce.NfceFetcher
import io.github.salomax.neotool.banking.service.import.nfce.NfceFetcherRouter
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.time.Instant

@DisplayName("NfceFetcherRouter")
class NfceFetcherRouterTest {
    private val spFetcher: NfceFetcher = mock()
    private val defaultClient: DefaultNfceClient = mock()

    private val spNfceData =
        NfceData(
            chNFe = "35240112345678000199650010000012341123456789",
            dhEmi = Instant.now(),
            vNF = BigDecimal("100.00"),
            cnpjEmitente = "12345678000199",
            nomeEmitente = "Store SP",
            municipio = "São Paulo",
            uf = "SP",
            items = emptyList(),
        )

    private val defaultNfceData =
        NfceData(
            chNFe = "43240112345678000199650010000012341123456789",
            dhEmi = Instant.now(),
            vNF = BigDecimal("50.00"),
            cnpjEmitente = "98765432000111",
            nomeEmitente = "Store RS",
            municipio = "Porto Alegre",
            uf = "RS",
            items = emptyList(),
        )

    @Test
    fun `routes SP URL (UF 35) to SpNfceFetcher`() {
        whenever(spFetcher.supportedUfCodes()).thenReturn(setOf("35"))
        whenever(spFetcher.fetch(any())).thenReturn(spNfceData)
        val router = NfceFetcherRouter(listOf(spFetcher), defaultClient)

        val url = "https://nfce.fazenda.sp.gov.br/qr?p=35240112345678000199650010000012341123456789"
        val result = router.fetch(url)

        assertThat(result.uf).isEqualTo("SP")
        verify(spFetcher).fetch(url)
        verify(defaultClient, never()).fetch(any())
    }

    @Test
    fun `routes unknown UF to defaultClient`() {
        whenever(spFetcher.supportedUfCodes()).thenReturn(setOf("35"))
        whenever(defaultClient.fetch(any())).thenReturn(defaultNfceData)
        val router = NfceFetcherRouter(listOf(spFetcher), defaultClient)

        val url = "https://dfe-portal.svrs.rs.gov.br/qr?p=43240112345678000199650010000012341123456789"
        val result = router.fetch(url)

        assertThat(result.uf).isEqualTo("RS")
        verify(defaultClient).fetch(url)
        verify(spFetcher, never()).fetch(any())
    }

    @Test
    fun `routes URL without chNFe to defaultClient`() {
        whenever(spFetcher.supportedUfCodes()).thenReturn(setOf("35"))
        whenever(defaultClient.fetch(any())).thenReturn(defaultNfceData)
        val router = NfceFetcherRouter(listOf(spFetcher), defaultClient)

        val url = "https://example.com/nfce"
        router.fetch(url)

        verify(defaultClient).fetch(url)
    }
}
