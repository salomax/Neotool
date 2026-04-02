package io.github.salomax.neotool.banking.domain.import.nfce

import io.github.salomax.neotool.banking.service.import.nfce.*
import io.github.salomax.neotool.banking.client.import.nfce.*
import io.github.salomax.neotool.banking.service.import.*
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
        cMunFG: String? = null,
        tradeName: String? = null,
        stateRegistration: String? = null,
        addressXml: String = "<xMun>São Paulo</xMun><UF>SP</UF>",
    ) = """
        <nfe>
          <infNFe>
            <chNFe>$chNFe</chNFe>
            <ide><dhEmi>$dhEmi</dhEmi>${cMunFG?.let { "<cMunFG>$it</cMunFG>" } ?: ""}</ide>
            <total><ICMSTot><vNF>$vNF</vNF></ICMSTot></total>
            <emit><CNPJ>$cnpj</CNPJ><xNome>Store</xNome>${tradeName?.let { "<xFant>$it</xFant>" } ?: ""}${stateRegistration?.let { "<IE>$it</IE>" } ?: ""}<enderEmit>$addressXml</enderEmit></emit>
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
            assertThat(result.cMunFG).isNull()
            verify(httpFetcher).fetch(qrCodeUrl)
        }

        @Test
        fun `parses cMunFG when present and keeps null when absent`() {
            whenever(httpFetcher.fetch(eq(qrCodeUrl)))
                .thenReturn(NfceHttpResponse(200, minimalValidNfceXml(cMunFG = "3547809")))

            val withMunicipalityCode = client.fetch(qrCodeUrl)

            assertThat(withMunicipalityCode.cMunFG).isEqualTo("3547809")

            whenever(httpFetcher.fetch(eq(qrCodeUrl)))
                .thenReturn(NfceHttpResponse(200, minimalValidNfceXml(cMunFG = null)))

            val withoutMunicipalityCode = client.fetch(qrCodeUrl)

            assertThat(withoutMunicipalityCode.cMunFG).isNull()
        }

        @Test
        fun `parses issuer trade name state registration and full address`() {
            whenever(httpFetcher.fetch(eq(qrCodeUrl)))
                .thenReturn(
                    NfceHttpResponse(
                        200,
                        minimalValidNfceXml(
                            tradeName = "Mercado Exemplo",
                            stateRegistration = "123456789112",
                            addressXml =
                                """
                                <xLgr>Rua das Flores</xLgr>
                                <nro>123</nro>
                                <xCpl>Loja 4</xCpl>
                                <xBairro>Centro</xBairro>
                                <xMun>São Paulo</xMun>
                                <UF>SP</UF>
                                <CEP>01234000</CEP>
                                """.trimIndent(),
                        ),
                    ),
                )

            val result = client.fetch(qrCodeUrl)

            assertThat(result.nomeFantasiaEmitente).isEqualTo("Mercado Exemplo")
            assertThat(result.inscricaoEstadualEmitente).isEqualTo("123456789112")
            assertThat(result.enderecoEmitente).isEqualTo("Rua das Flores, 123 • Loja 4 • Centro • São Paulo - SP")
            assertThat(result.cepEmitente).isEqualTo("01234000")
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
        fun `parses extended fields from det prod nodes`() {
            val xmlWithExtended =
                """
                <nfe>
                  <infNFe>
                    <chNFe>12345678901234567890123456789012345678901234</chNFe>
                    <ide><dhEmi>2024-01-15T10:30:00-03:00</dhEmi></ide>
                    <total><ICMSTot><vNF>45.80</vNF></ICMSTot></total>
                    <emit><CNPJ>12345678000199</CNPJ></emit>
                    <det nItem="1"><prod><xProd>ARROZ TIPO 1</xProd><cEAN>7891234567890</cEAN><cProd>000123</cProd><vProd>22.90</vProd><vUnCom>22.90</vUnCom><vDesc>2.00</vDesc><qCom>1</qCom><uCom>UN</uCom><NCM>10063021</NCM></prod></det>
                    <det nItem="2"><prod><xProd>FEIJAO PRETO</xProd><cEAN>SEM GTIN</cEAN><vProd>22.90</vProd><qCom>2</qCom><uCom>KG</uCom><NCM>07133319</NCM></prod></det>
                  </infNFe>
                </nfe>
                """.trimIndent()
            whenever(httpFetcher.fetch(any())).thenReturn(NfceHttpResponse(200, xmlWithExtended))

            val result = client.fetch(qrCodeUrl)

            assertThat(result.items).hasSize(2)
            val item1 = result.items[0]
            assertThat(item1.cEAN).isEqualTo("7891234567890")
            assertThat(item1.cProd).isEqualTo("000123")
            assertThat(item1.vUnCom).isEqualByComparingTo(BigDecimal("22.90"))
            assertThat(item1.vDesc).isEqualByComparingTo(BigDecimal("2.00"))
            assertThat(item1.nItem).isEqualTo(1)

            val item2 = result.items[1]
            assertThat(item2.cEAN).isNull() // SEM GTIN filtered out
            assertThat(item2.cProd).isNull()
            assertThat(item2.vUnCom).isNull()
            assertThat(item2.vDesc).isNull()
            assertThat(item2.nItem).isEqualTo(2)
        }

        @Test
        fun `parses CPF destinatario and tributos from XML`() {
            val xmlWithCpfAndTrib =
                """
                <nfe>
                  <infNFe>
                    <chNFe>12345678901234567890123456789012345678901234</chNFe>
                    <ide><dhEmi>2024-01-15T10:30:00-03:00</dhEmi></ide>
                    <total><ICMSTot><vNF>100.00</vNF><vTotTrib>15.30</vTotTrib></ICMSTot></total>
                    <emit><CNPJ>12345678000199</CNPJ></emit>
                    <dest><CPF>32167209851</CPF></dest>
                    <det nItem="1"><prod><xProd>Item A</xProd><vProd>100.00</vProd><qCom>1</qCom><uCom>UN</uCom><NCM>1234</NCM></prod><imposto><vTotTrib>15.30</vTotTrib></imposto></det>
                  </infNFe>
                </nfe>
                """.trimIndent()
            whenever(httpFetcher.fetch(any())).thenReturn(NfceHttpResponse(200, xmlWithCpfAndTrib))

            val result = client.fetch(qrCodeUrl)

            assertThat(result.cpfDestinatario).isEqualTo("32167209851")
            assertThat(result.vTotTrib).isEqualByComparingTo(BigDecimal("15.30"))
            assertThat(result.items[0].vTribItem).isEqualByComparingTo(BigDecimal("15.30"))
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
