package io.github.salomax.neotool.banking.domain.import.nfce

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.math.BigDecimal

@DisplayName("NfceClient")
class NfceClientTest {
    private lateinit var httpFetcher: NfceHttpFetcher
    private lateinit var client: DefaultNfceClient

    private val qrCodeUrl = "https://example.com/nfce/12345678901234567890123456789012345678901234"

    private fun minimalValidNfceXml(
        chNFe: String = "12345678901234567890123456789012345678901234",
        dhEmi: String = "2024-01-15T10:30:00-03:00",
        vNF: String = "100.50",
        cnpj: String = "12345678000199",
    ) = """
        <nfe>
          <infNFe>
            <chNFe>$chNFe</chNFe>
            <ide><dhEmi>$dhEmi</dhEmi></ide>
            <total><ICMSTot><vNF>$vNF</vNF></ICMSTot></total>
            <emit><CNPJ>$cnpj</CNPJ><xNome>Store</xNome><enderEmit><xMun>São Paulo</xMun><UF>SP</UF></enderEmit></emit>
          </infNFe>
        </nfe>
        """.trimIndent()

    @BeforeEach
    fun setUp() {
        httpFetcher = mock()
        client = DefaultNfceClient(httpFetcher)
    }

    @Nested
    @DisplayName("fetch — success")
    inner class FetchSuccessTests {
        @Test
        fun `returns NfceData when HTTP 200 and valid XML`() {
            whenever(httpFetcher.fetch(eq(qrCodeUrl)))
                .thenReturn(NfceHttpResponse(200, minimalValidNfceXml()))

            val result = client.fetch(qrCodeUrl)

            assertThat(result.chNFe).isEqualTo("12345678901234567890123456789012345678901234")
            assertThat(result.dhEmi).isNotNull()
            assertThat(result.vNF).isEqualByComparingTo(BigDecimal("100.50"))
            assertThat(result.cnpjEmitente).isEqualTo("12345678000199")
            assertThat(result.nomeEmitente).isEqualTo("Store")
            assertThat(result.municipio).isEqualTo("São Paulo")
            assertThat(result.uf).isEqualTo("SP")
            assertThat(result.items).isEmpty()
            verify(httpFetcher).fetch(qrCodeUrl)
        }

        @Test
        fun `uses chNFe from URL when 44 digits present in URL`() {
            val xmlWithoutChNFe =
                """
                <nfe>
                  <infNFe>
                    <ide><dhEmi>2024-01-15T10:30:00-03:00</dhEmi></ide>
                    <total><ICMSTot><vNF>50.00</vNF></ICMSTot></total>
                    <emit><CNPJ>11111111000111</CNPJ></emit>
                  </infNFe>
                </nfe>
                """.trimIndent()
            whenever(httpFetcher.fetch(any())).thenReturn(NfceHttpResponse(200, xmlWithoutChNFe))

            val result = client.fetch(qrCodeUrl)

            assertThat(result.chNFe).isEqualTo("12345678901234567890123456789012345678901234")
        }

        @Test
        fun `parses items from det prod nodes`() {
            val xmlWithItems =
                """
                <nfe>
                  <infNFe>
                    <chNFe>12345678901234567890123456789012345678901234</chNFe>
                    <ide><dhEmi>2024-01-15T10:30:00-03:00</dhEmi></ide>
                    <total><ICMSTot><vNF>25.00</vNF></ICMSTot></total>
                    <emit><CNPJ>12345678000199</CNPJ></emit>
                    <det><prod><xProd>Item A</xProd><vProd>15.00</vProd><qCom>1</qCom><uCom>UN</uCom><NCM>1234</NCM></prod></det>
                    <det><prod><xProd>Item B</xProd><vProd>10.00</vProd><qCom>2</qCom></prod></det>
                  </infNFe>
                </nfe>
                """.trimIndent()
            whenever(httpFetcher.fetch(any())).thenReturn(NfceHttpResponse(200, xmlWithItems))

            val result = client.fetch(qrCodeUrl)

            assertThat(result.items).hasSize(2)
            assertThat(result.items[0].xProd).isEqualTo("Item A")
            assertThat(result.items[0].vProd).isEqualByComparingTo(BigDecimal("15.00"))
            assertThat(result.items[0].qCom).isEqualByComparingTo(BigDecimal("1"))
            assertThat(result.items[0].uCom).isEqualTo("UN")
            assertThat(result.items[0].ncm).isEqualTo("1234")
            assertThat(result.items[1].xProd).isEqualTo("Item B")
            assertThat(result.items[1].vProd).isEqualByComparingTo(BigDecimal("10.00"))
            assertThat(result.items[1].qCom).isEqualByComparingTo(BigDecimal("2"))
            assertThat(result.items[1].uCom).isEqualTo("UN")
        }

        @Test
        fun `accepts cStat 100 and 150`() {
            val xml100 =
                "<nfe><infNFe><cStat>100</cStat><chNFe>12345678901234567890123456789012345678901234</chNFe>" +
                    "<ide><dhEmi>2024-01-15T10:30:00-03:00</dhEmi></ide>" +
                    "<total><ICMSTot><vNF>1</vNF></ICMSTot></total>" +
                    "<emit><CNPJ>12345678000199</CNPJ></emit></infNFe></nfe>"
            whenever(httpFetcher.fetch(any())).thenReturn(NfceHttpResponse(200, xml100))
            assertThat(client.fetch(qrCodeUrl).chNFe).isNotNull()

            val xml150 =
                "<nfe><infNFe><cStat>150</cStat><chNFe>12345678901234567890123456789012345678901234</chNFe>" +
                    "<ide><dhEmi>2024-01-15T10:30:00-03:00</dhEmi></ide>" +
                    "<total><ICMSTot><vNF>1</vNF></ICMSTot></total>" +
                    "<emit><CNPJ>12345678000199</CNPJ></emit></infNFe></nfe>"
            whenever(httpFetcher.fetch(any())).thenReturn(NfceHttpResponse(200, xml150))
            assertThat(client.fetch(qrCodeUrl).chNFe).isNotNull()
        }
    }

    @Nested
    @DisplayName("fetch — HTTP errors")
    inner class FetchHttpErrorTests {
        @Test
        fun `throws NfceUnavailableException when status is not 200`() {
            whenever(httpFetcher.fetch(eq(qrCodeUrl))).thenReturn(NfceHttpResponse(404, "Not Found"))

            val ex = assertThrows<NfceUnavailableException> { client.fetch(qrCodeUrl) }

            assertThat(ex.message).contains("HTTP 404")
        }

        @Test
        fun `throws NfceUnavailableException when httpFetcher throws`() {
            whenever(httpFetcher.fetch(any())).thenThrow(RuntimeException("Connection timeout"))

            val ex = assertThrows<NfceUnavailableException> { client.fetch(qrCodeUrl) }

            assertThat(ex.message).contains("NFC-e fetch failed")
            assertThat(ex.cause).hasMessageContaining("Connection timeout")
        }
    }

    @Nested
    @DisplayName("fetch — invalid content")
    inner class FetchInvalidContentTests {
        @Test
        fun `throws NfceInvalidException when response body is empty`() {
            whenever(httpFetcher.fetch(any())).thenReturn(NfceHttpResponse(200, ""))

            val ex = assertThrows<NfceInvalidException> { client.fetch(qrCodeUrl) }

            assertThat(ex.message).contains("empty")
        }

        @Test
        fun `throws NfceInvalidException when response body is blank`() {
            whenever(httpFetcher.fetch(any())).thenReturn(NfceHttpResponse(200, "   \n  "))

            val ex = assertThrows<NfceInvalidException> { client.fetch(qrCodeUrl) }

            assertThat(ex.message).contains("empty")
        }

        @Test
        fun `throws NfceInvalidException when XML is malformed`() {
            whenever(httpFetcher.fetch(any())).thenReturn(NfceHttpResponse(200, "<nfe><unclosed>"))

            val ex = assertThrows<NfceInvalidException> { client.fetch(qrCodeUrl) }

            assertThat(ex.message).contains("malformed")
        }

        @Test
        fun `throws NfceInvalidException when cStat is not 100 or 150`() {
            val xmlCancelled =
                "<nfe><infNFe><cStat>101</cStat><chNFe>12345678901234567890123456789012345678901234</chNFe>" +
                    "<ide><dhEmi>2024-01-15T10:30:00-03:00</dhEmi></ide>" +
                    "<total><ICMSTot><vNF>1</vNF></ICMSTot></total>" +
                    "<emit><CNPJ>12345678000199</CNPJ></emit></infNFe></nfe>"
            whenever(httpFetcher.fetch(any())).thenReturn(NfceHttpResponse(200, xmlCancelled))

            val ex = assertThrows<NfceInvalidException> { client.fetch(qrCodeUrl) }

            assertThat(ex.message).contains("cStat=101")
        }
    }
}
