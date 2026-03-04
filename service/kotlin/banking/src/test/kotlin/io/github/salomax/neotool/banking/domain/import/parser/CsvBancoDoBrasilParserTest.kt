package io.github.salomax.neotool.banking.domain.import.parser

import io.github.salomax.neotool.banking.domain.import.parser.csv.CsvFingerprintBuilder
import io.github.salomax.neotool.banking.domain.transaction.CreditDebitType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.nio.charset.Charset
import java.time.LocalDate

@DisplayName("CsvBancoDoBrasilParser")
class CsvBancoDoBrasilParserTest {

    @Nested
    @DisplayName("reader contract")
    inner class ReaderContractTests {

        @Test
        fun `returns stable reader id`() {
            assertThat(CsvBancoDoBrasilParser.id()).isEqualTo("csv-banco-do-brasil")
        }

        @Test
        fun `supports banco do brasil fingerprint with high score`() {
            val csv = """
                "Data","Lançamento","Detalhes","Nº documento","Valor","Tipo Lançamento"
                "03/02/2026","Pix - Enviado","03/02 17:39 ROBERTA","20301","-50,00","Saída"
            """.trimIndent()
            val bytes = csv.toByteArray(Charset.forName("ISO-8859-1"))
            val fingerprint = CsvFingerprintBuilder.build(bytes)

            val match = CsvBancoDoBrasilParser.supports(fingerprint)

            assertThat(match.readerId).isEqualTo("csv-banco-do-brasil")
            assertThat(match.score).isGreaterThanOrEqualTo(0.85)
            assertThat(match.reasons).contains("core_columns_match", "bb_header_hints")
        }
    }

    @Nested
    @DisplayName("parse")
    inner class ParseTests {

        @Test
        fun `parses banco do brasil csv with latin encoding and fallback description`() {
            val csv = """
                "Data","Lançamento","Detalhes","Nº documento","Valor","Tipo Lançamento"
                "03/02/2026","Pix - Enviado","03/02 17:39 ROBERTA LOPES ADURENS","20301","-50,00","Saída"
                "03/02/2026","BB Rende Fácil","Rende Facil","9903","50,00","Entrada"
                "06/02/2026","Crédito Sistema CDA","","94532586","606,00","Entrada"
                "00/00/0000","Saldo do dia","","","0,00",""
            """.trimIndent()
            val bytes = csv.toByteArray(Charset.forName("ISO-8859-1"))

            val result = CsvBancoDoBrasilParser.parse(bytes)

            assertThat(result).hasSize(3)
            assertThat(result).allSatisfy { row ->
                assertThat(row.providerTransactionId).isNull()
                assertThat(row.description).doesNotContain("�")
            }

            assertThat(result[0].creditDebitType).isEqualTo(CreditDebitType.DEBIT)
            assertThat(result[0].amount).isEqualByComparingTo(BigDecimal("50.00"))
            assertThat(result[0].date).isEqualTo(LocalDate.of(2026, 2, 3))
            assertThat(result[0].description).contains("Pix - Enviado")

            assertThat(result[1].creditDebitType).isEqualTo(CreditDebitType.CREDIT)
            assertThat(result[1].amount).isEqualByComparingTo(BigDecimal("50.00"))
            assertThat(result[1].description).contains("BB Rende Fácil")

            assertThat(result[2].creditDebitType).isEqualTo(CreditDebitType.CREDIT)
            assertThat(result[2].amount).isEqualByComparingTo(BigDecimal("606.00"))
            assertThat(result[2].description).isEqualTo("Crédito Sistema CDA")
        }
    }
}
