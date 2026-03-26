package io.github.salomax.neotool.banking.domain.import.nfce

import io.github.salomax.neotool.banking.client.import.nfce.NfceInvalidException
import io.github.salomax.neotool.banking.service.import.nfce.SpNfceHtmlParser
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.math.BigDecimal

@DisplayName("SpNfceHtmlParser")
class SpNfceHtmlParserTest {

    private val chNFe = "35240112345678000199650010000012341123456789"

    private fun minimalSpHtml(
        chNFe: String = this.chNFe,
        dhEmi: String = "15/01/2024 10:30:00",
        vNF: String = "100,50",
        cnpj: String = "12.345.678/0001-99",
        nomeEmitente: String = "Supermercado Teste",
        municipio: String = "São Paulo",
        uf: String = "SP",
        cpf: String? = null,
        vTotTrib: String? = null,
        itemsHtml: String = "",
    ) = """
        <html>
        <body>
        <label>Chave de Acesso</label>
        <span class="chave">$chNFe</span>

        <label>Data de Emissão</label>
        <span class="data">$dhEmi</span>

        <label>Valor Total da Nota Fiscal</label>
        <span class="valor">$vNF</span>

        <label>CNPJ</label>
        <span class="cnpj">$cnpj</span>

        <label>Nome / Razão Social</label>
        <span class="nome">$nomeEmitente</span>

        <label>Município</label>
        <span class="mun">$municipio</span>

        <label>UF</label>
        <span class="uf">$uf</span>

        ${cpf?.let { """<label>CPF</label><span class="cpf">$it</span>""" } ?: ""}
        ${vTotTrib?.let { """<label>Tributos Totais Incidentes (Lei Federal 12.741/2012) R$</label><span class="totalNumb txtObs">$it</span>""" } ?: ""}

        $itemsHtml
        </body>
        </html>
    """.trimIndent()

    private fun itemHtml(
        xProd: String = "ARROZ TIPO 1 5KG",
        ncm: String = "10063021",
        cEAN: String = "7891234567890",
        cProd: String = "000123",
        vUnCom: String = "22,90",
        vProd: String = "22,90",
        qCom: String = "1,0000",
        uCom: String = "UN",
        vDesc: String? = null,
        vTribItem: String? = null,
    ): String {
        val descBlock = """<span class="fixo-prod-serv-descricao"><span>$xProd</span></span>"""
        val ncmBlock = """<label>Código NCM</label><span class="ncm">$ncm</span>"""
        val eanBlock = """<label>Código EAN Comercial</label><span class="ean">$cEAN</span>"""
        val prodBlock = """<label>Código do Produto</label><span class="cprod">$cProd</span>"""
        val vUnComBlock = """<label>Valor unitário de comercialização</label><span class="vun">$vUnCom</span>"""
        val vProdBlock = """<label>Valor do Produto</label><span class="vprod">$vProd</span>"""
        val qComBlock = """<label>Quantidade Comercial</label><span class="qcom">$qCom</span>"""
        val uComBlock = """<label>Unidade Comercial</label><span class="ucom">$uCom</span>"""
        val vDescBlock = if (vDesc != null) """<label>Valor do Desconto</label><span class="vdesc">$vDesc</span>""" else ""
        val vTribBlock = if (vTribItem != null) """<label>Valor Aproximado dos Tributos</label><span class="vtrib">$vTribItem</span>""" else ""
        return "$descBlock\n$ncmBlock\n$eanBlock\n$prodBlock\n$vUnComBlock\n$vProdBlock\n$qComBlock\n$uComBlock\n$vDescBlock\n$vTribBlock"
    }

    @Nested
    @DisplayName("parse — header fields")
    inner class HeaderTests {
        @Test
        fun `parses all header fields correctly`() {
            val html = minimalSpHtml()
            val result = SpNfceHtmlParser.parse(html, null)

            assertThat(result.chNFe).isEqualTo(chNFe)
            assertThat(result.dhEmi).isNotNull()
            assertThat(result.vNF).isEqualByComparingTo(BigDecimal("100.50"))
            assertThat(result.cnpjEmitente).isEqualTo("12345678000199")
            assertThat(result.nomeEmitente).isEqualTo("Supermercado Teste")
            assertThat(result.municipio).isEqualTo("São Paulo")
            assertThat(result.uf).isEqualTo("SP")
            assertThat(result.items).isEmpty()
        }

        @Test
        fun `uses chNFe from URL when provided`() {
            val urlChNFe = "99999999999999999999999999999999999999999999"
            val result = SpNfceHtmlParser.parse(minimalSpHtml(), urlChNFe)

            assertThat(result.chNFe).isEqualTo(urlChNFe)
        }

        @Test
        fun `parses vNF with thousand separator`() {
            val html = minimalSpHtml(vNF = "1.234,56")
            val result = SpNfceHtmlParser.parse(html, chNFe)

            assertThat(result.vNF).isEqualByComparingTo(BigDecimal("1234.56"))
        }

        @Test
        fun `strips CNPJ formatting`() {
            val html = minimalSpHtml(cnpj = "12.345.678/0001-99")
            val result = SpNfceHtmlParser.parse(html, chNFe)

            assertThat(result.cnpjEmitente).isEqualTo("12345678000199")
        }

        @Test
        fun `parses CPF destinatario`() {
            val html = minimalSpHtml(cpf = "321.672.098-51")
            val result = SpNfceHtmlParser.parse(html, chNFe)

            assertThat(result.cpfDestinatario).isEqualTo("32167209851")
        }

        @Test
        fun `cpfDestinatario is null when not present`() {
            val html = minimalSpHtml()
            val result = SpNfceHtmlParser.parse(html, chNFe)

            assertThat(result.cpfDestinatario).isNull()
        }

        @Test
        fun `parses tributos totais`() {
            val html = minimalSpHtml(vTotTrib = "44,17")
            val result = SpNfceHtmlParser.parse(html, chNFe)

            assertThat(result.vTotTrib).isEqualByComparingTo(BigDecimal("44.17"))
        }
    }

    @Nested
    @DisplayName("parse — items")
    inner class ItemTests {
        @Test
        fun `parses single item with all fields`() {
            val html = minimalSpHtml(itemsHtml = itemHtml())
            val result = SpNfceHtmlParser.parse(html, chNFe)

            assertThat(result.items).hasSize(1)
            val item = result.items[0]
            assertThat(item.xProd).isEqualTo("ARROZ TIPO 1 5KG")
            assertThat(item.ncm).isEqualTo("10063021")
            assertThat(item.cEAN).isEqualTo("7891234567890")
            assertThat(item.cProd).isEqualTo("000123")
            assertThat(item.vUnCom).isEqualByComparingTo(BigDecimal("22.90"))
            assertThat(item.vProd).isEqualByComparingTo(BigDecimal("22.90"))
            assertThat(item.qCom).isEqualByComparingTo(BigDecimal("1.0000"))
            assertThat(item.uCom).isEqualTo("UN")
            assertThat(item.vDesc).isNull()
            assertThat(item.nItem).isEqualTo(1)
        }

        @Test
        fun `parses item with discount`() {
            val html = minimalSpHtml(itemsHtml = itemHtml(vDesc = "2,50"))
            val result = SpNfceHtmlParser.parse(html, chNFe)

            assertThat(result.items[0].vDesc).isEqualByComparingTo(BigDecimal("2.50"))
        }

        @Test
        fun `parses item tributos`() {
            val html = minimalSpHtml(itemsHtml = itemHtml(vTribItem = "2,81"))
            val result = SpNfceHtmlParser.parse(html, chNFe)

            assertThat(result.items[0].vTribItem).isEqualByComparingTo(BigDecimal("2.81"))
        }

        @Test
        fun `vTribItem is null when not present`() {
            val html = minimalSpHtml(itemsHtml = itemHtml())
            val result = SpNfceHtmlParser.parse(html, chNFe)

            assertThat(result.items[0].vTribItem).isNull()
        }

        @Test
        fun `parses multiple items with correct nItem sequence`() {
            val items = itemHtml(xProd = "ITEM A", vProd = "10,00") + "\n" +
                itemHtml(xProd = "ITEM B", vProd = "20,00")
            val html = minimalSpHtml(itemsHtml = items)
            val result = SpNfceHtmlParser.parse(html, chNFe)

            assertThat(result.items).hasSize(2)
            assertThat(result.items[0].xProd).isEqualTo("ITEM A")
            assertThat(result.items[0].nItem).isEqualTo(1)
            assertThat(result.items[1].xProd).isEqualTo("ITEM B")
            assertThat(result.items[1].nItem).isEqualTo(2)
        }

        @Test
        fun `filters out SEM GTIN from cEAN`() {
            val html = minimalSpHtml(itemsHtml = itemHtml(cEAN = "SEM GTIN"))
            val result = SpNfceHtmlParser.parse(html, chNFe)

            assertThat(result.items[0].cEAN).isNull()
        }

        @Test
        fun `parses item with KG unit and fractional quantity`() {
            val html = minimalSpHtml(
                itemsHtml = itemHtml(
                    xProd = "BANANA NANICA",
                    qCom = "1,2340",
                    uCom = "KG",
                    vUnCom = "5,99",
                    vProd = "7,39",
                ),
            )
            val result = SpNfceHtmlParser.parse(html, chNFe)

            val item = result.items[0]
            assertThat(item.qCom).isEqualByComparingTo(BigDecimal("1.2340"))
            assertThat(item.uCom).isEqualTo("KG")
            assertThat(item.vUnCom).isEqualByComparingTo(BigDecimal("5.99"))
        }
    }

    @Nested
    @DisplayName("parse — error cases")
    inner class ErrorTests {
        @Test
        fun `throws NfceInvalidException when dhEmi is missing`() {
            val html = """
                <html>
                <label>Chave de Acesso</label><span>$chNFe</span>
                <label>Valor Total da Nota Fiscal</label><span>100,00</span>
                <label>CNPJ</label><span>12345678000199</span>
                </html>
            """.trimIndent()

            val ex = assertThrows<NfceInvalidException> { SpNfceHtmlParser.parse(html, chNFe) }
            assertThat(ex.message).contains("dhEmi")
        }

        @Test
        fun `throws NfceInvalidException when vNF is missing`() {
            val html = """
                <html>
                <label>Chave de Acesso</label><span>$chNFe</span>
                <label>Data de Emissão</label><span>15/01/2024 10:30:00</span>
                <label>CNPJ</label><span>12345678000199</span>
                </html>
            """.trimIndent()

            val ex = assertThrows<NfceInvalidException> { SpNfceHtmlParser.parse(html, chNFe) }
            assertThat(ex.message).contains("vNF")
        }

        @Test
        fun `throws NfceInvalidException when CNPJ is missing`() {
            val html = """
                <html>
                <label>Chave de Acesso</label><span>$chNFe</span>
                <label>Data de Emissão</label><span>15/01/2024 10:30:00</span>
                <label>Valor Total da Nota Fiscal</label><span>100,00</span>
                </html>
            """.trimIndent()

            val ex = assertThrows<NfceInvalidException> { SpNfceHtmlParser.parse(html, chNFe) }
            assertThat(ex.message).contains("CNPJ")
        }

        @Test
        fun `throws NfceInvalidException when chNFe missing from both URL and HTML`() {
            val html = """
                <html>
                <label>Data de Emissão</label><span>15/01/2024 10:30:00</span>
                <label>Valor Total da Nota Fiscal</label><span>100,00</span>
                <label>CNPJ</label><span>12345678000199</span>
                </html>
            """.trimIndent()

            val ex = assertThrows<NfceInvalidException> { SpNfceHtmlParser.parse(html, null) }
            assertThat(ex.message).contains("chNFe")
        }
    }
}
