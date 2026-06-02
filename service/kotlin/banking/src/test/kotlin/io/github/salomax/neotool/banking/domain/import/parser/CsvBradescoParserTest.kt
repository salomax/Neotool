package io.github.salomax.neotool.banking.service.import.parser

import io.github.salomax.neotool.banking.domain.transaction.CreditDebitType
import io.github.salomax.neotool.banking.service.import.parser.csv.CsvFingerprintBuilder
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.nio.charset.StandardCharsets
import java.time.LocalDate

@DisplayName("CsvBradescoParser")
class CsvBradescoParserTest {
    private fun loadFixture(name: String): ByteArray =
        javaClass.getResourceAsStream("/fixtures/csv/$name")!!.readBytes()

    @Nested
    @DisplayName("reader contract")
    inner class ReaderContractTests {
        @Test
        fun `returns stable reader id`() {
            assertThat(CsvBradescoParser.id()).isEqualTo("csv-bradesco")
        }

        @Test
        fun `supports bradesco fingerprint with high score`() {
            val bytes = loadFixture("bradesco_account_sample.csv")
            val fingerprint = CsvFingerprintBuilder.build(bytes)

            val match = CsvBradescoParser.supports(fingerprint)

            assertThat(match.readerId).isEqualTo("csv-bradesco")
            assertThat(match.score).isGreaterThanOrEqualTo(0.85)
            assertThat(match.reasons).contains("core_bradesco_columns_match", "bradesco_header_hints")
        }

        @Test
        fun `returns low score for unrelated fingerprint`() {
            val csv =
                """
                reference|amount|memo
                2026-01-01|1000.00|Settlement reference
                2026-01-02|250.00|Other credit
                """.trimIndent()
            val fingerprint = CsvFingerprintBuilder.build(csv.toByteArray(StandardCharsets.UTF_8))

            val match = CsvBradescoParser.supports(fingerprint)

            assertThat(match.score).isLessThan(0.60)
        }
    }

    @Nested
    @DisplayName("parse")
    inner class ParseTests {
        @Test
        fun `parses bradesco fixture with debit and credit columns`() {
            val bytes = loadFixture("bradesco_account_sample.csv")

            val result = CsvBradescoParser.parse(bytes)

            assertThat(result).hasSize(3)

            val credit = result[0]
            assertThat(credit.providerTransactionId).isEqualTo("998877")
            assertThat(credit.creditDebitType).isEqualTo(CreditDebitType.CREDIT)
            assertThat(credit.amount).isEqualByComparingTo(BigDecimal("35206.39"))
            assertThat(credit.date).isEqualTo(LocalDate.of(2025, 11, 1))
            assertThat(credit.description).contains("PIX RECEBIDA")

            val debit = result[1]
            assertThat(debit.providerTransactionId).isEqualTo("998878")
            assertThat(debit.creditDebitType).isEqualTo(CreditDebitType.DEBIT)
            assertThat(debit.amount).isEqualByComparingTo(BigDecimal("300.00"))
            assertThat(debit.date).isEqualTo(LocalDate.of(2025, 11, 3))

            val tariff = result[2]
            assertThat(tariff.providerTransactionId).isEqualTo("998879")
            assertThat(tariff.creditDebitType).isEqualTo(CreditDebitType.DEBIT)
            assertThat(tariff.amount).isEqualByComparingTo(BigDecimal("45.90"))
        }

        @Test
        fun `parses single amount variant when signed value is present`() {
            val csv =
                """
                Data;Historico;Documento;Valor
                01/11/2025;Transferencia recebida;B-1;100,00
                02/11/2025;Pagamento boleto;B-2;-50,00
                """.trimIndent()

            val result = CsvBradescoParser.parse(csv.toByteArray(StandardCharsets.UTF_8))

            assertThat(result).hasSize(2)
            assertThat(result[0].creditDebitType).isEqualTo(CreditDebitType.CREDIT)
            assertThat(result[0].amount).isEqualByComparingTo(BigDecimal("100.00"))
            assertThat(result[1].creditDebitType).isEqualTo(CreditDebitType.DEBIT)
            assertThat(result[1].amount).isEqualByComparingTo(BigDecimal("50.00"))
        }
    }
}
