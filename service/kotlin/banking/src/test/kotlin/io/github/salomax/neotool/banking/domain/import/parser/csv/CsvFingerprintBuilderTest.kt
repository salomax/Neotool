package io.github.salomax.neotool.banking.domain.import.parser.csv

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets

@DisplayName("CsvFingerprintBuilder")
class CsvFingerprintBuilderTest {
    private fun loadFixture(name: String): ByteArray =
        javaClass.getResourceAsStream("/fixtures/csv/$name")!!.readBytes()

    @Nested
    @DisplayName("build")
    inner class BuildTests {
        @Test
        fun `builds fingerprint for nubank account fixture`() {
            val bytes = loadFixture("nubank_account_sample.csv")

            val result = CsvFingerprintBuilder.build(bytes)

            assertThat(result.encoding).isEqualTo("UTF-8")
            assertThat(result.delimiter).isEqualTo(',')
            assertThat(result.hasHeader).isTrue()
            assertThat(result.normalizedColumns)
                .containsExactly("data", "valor", "identificador", "descricao")
            assertThat(result.datePatterns).contains(CsvDatePattern.BR_DD_MM_YYYY_SLASH)
            assertThat(result.amountPatterns).contains(CsvAmountPattern.DOT_DECIMAL)
            assertThat(result.descriptionTokens)
                .contains("transferencia", "pix", "pagamento", "boleto")
        }

        @Test
        fun `detects semicolon delimiter and br amount format with thousands`() {
            val csv =
                """
                Data;Valor;Descrição
                01/01/2026;-1.234,56;Transferência enviada via Pix
                02/01/2026;950,10;Pagamento de boleto
                """.trimIndent()

            val result = CsvFingerprintBuilder.build(csv.toByteArray(StandardCharsets.UTF_8))

            assertThat(result.delimiter).isEqualTo(';')
            assertThat(result.hasHeader).isTrue()
            assertThat(result.normalizedColumns).containsExactly("data", "valor", "descricao")
            assertThat(result.datePatterns).contains(CsvDatePattern.BR_DD_MM_YYYY_SLASH)
            assertThat(result.amountPatterns)
                .contains(CsvAmountPattern.THOUSANDS_DOT_DECIMAL_COMMA, CsvAmountPattern.COMMA_DECIMAL)
            assertThat(result.descriptionTokens).contains("transferencia", "pix", "pagamento", "boleto")
        }

        @Test
        fun `detects data-only csv without header`() {
            val csv =
                """
                2026-01-01,-10.50,Pix enviado
                2026-01-02,25.00,Transferencia recebida
                """.trimIndent()

            val result = CsvFingerprintBuilder.build(csv.toByteArray(StandardCharsets.UTF_8))

            assertThat(result.delimiter).isEqualTo(',')
            assertThat(result.hasHeader).isFalse()
            assertThat(result.normalizedColumns).isEmpty()
            assertThat(result.datePatterns).contains(CsvDatePattern.ISO_YYYY_MM_DD)
            assertThat(result.amountPatterns).contains(CsvAmountPattern.DOT_DECIMAL)
            assertThat(result.descriptionTokens).contains("pix", "transferencia")
        }

        @Test
        fun `accepts bom in header row and normalizes columns`() {
            val csv = "\uFEFFData,Valor,Descrição\n01/11/2025,-10.00,Pix enviado"

            val result = CsvFingerprintBuilder.build(csv.toByteArray(StandardCharsets.UTF_8))

            assertThat(result.hasHeader).isTrue()
            assertThat(result.normalizedColumns).containsExactly("data", "valor", "descricao")
        }

        @Test
        fun `detects header on single-line csv without data rows`() {
            val csv = "Data;Hora;Descricao;Valor;Saldo\n"

            val result = CsvFingerprintBuilder.build(csv.toByteArray(StandardCharsets.UTF_8))

            assertThat(result.delimiter).isEqualTo(';')
            assertThat(result.hasHeader).isTrue()
            assertThat(result.normalizedColumns).containsExactly("data", "hora", "descricao", "valor", "saldo")
            assertThat(result.datePatterns).isEmpty()
            assertThat(result.amountPatterns).isEmpty()
        }

        @Test
        fun `detects latin1 encoded csv and normalizes accent headers`() {
            val csv =
                """
                "Data","Lançamento","Detalhes","Nº documento","Valor","Tipo Lançamento"
                "03/02/2026","BB Rende Fácil","Rende Facil","9903","50,00","Entrada"
                """.trimIndent()

            val result = CsvFingerprintBuilder.build(csv.toByteArray(Charset.forName("ISO-8859-1")))

            assertThat(result.encoding).isEqualTo("windows-1252")
            assertThat(result.normalizedColumns)
                .containsExactly("data", "lancamento", "detalhes", "n_documento", "valor", "tipo_lancamento")
        }
    }
}
