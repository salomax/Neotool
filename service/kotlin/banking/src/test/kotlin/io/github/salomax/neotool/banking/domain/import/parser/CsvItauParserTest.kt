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

@DisplayName("CsvItauParser")
class CsvItauParserTest {
    private fun loadFixture(name: String): ByteArray =
        javaClass.getResourceAsStream("/fixtures/csv/$name")!!.readBytes()

    @Nested
    @DisplayName("reader contract")
    inner class ReaderContractTests {
        @Test
        fun `returns stable reader id`() {
            assertThat(CsvItauParser.id()).isEqualTo("csv-itau")
        }

        @Test
        fun `supports itau fingerprint with high score`() {
            val bytes = loadFixture("itau_account_sample.csv")
            val fingerprint = CsvFingerprintBuilder.build(bytes)

            val match = CsvItauParser.supports(fingerprint)

            assertThat(match.readerId).isEqualTo("csv-itau")
            assertThat(match.score).isGreaterThanOrEqualTo(0.85)
            assertThat(match.reasons).contains("core_columns_match", "itau_header_hints")
        }

        @Test
        fun `returns low score for unrelated fingerprint`() {
            val csv =
                """
                posted_at;gross_value;memo
                2026-01-01;1000,00;Settlement reference
                2026-01-02;250,00;Other credit
                """.trimIndent()
            val fingerprint = CsvFingerprintBuilder.build(csv.toByteArray(StandardCharsets.UTF_8))

            val match = CsvItauParser.supports(fingerprint)

            assertThat(match.score).isLessThan(0.60)
        }
    }

    @Nested
    @DisplayName("parse")
    inner class ParseTests {
        @Test
        fun `parses itau fixture with signed amount column`() {
            val bytes = loadFixture("itau_account_sample.csv")

            val result = CsvItauParser.parse(bytes)

            assertThat(result).hasSize(3)

            val credit = result[0]
            assertThat(credit.providerTransactionId).isEqualTo("12345")
            assertThat(credit.creditDebitType).isEqualTo(CreditDebitType.CREDIT)
            assertThat(credit.amount).isEqualByComparingTo(BigDecimal("1250.00"))
            assertThat(credit.date).isEqualTo(LocalDate.of(2025, 12, 1))
            assertThat(credit.description).contains("PIX RECEBIDO")

            val debit = result[1]
            assertThat(debit.providerTransactionId).isEqualTo("12346")
            assertThat(debit.creditDebitType).isEqualTo(CreditDebitType.DEBIT)
            assertThat(debit.amount).isEqualByComparingTo(BigDecimal("350.45"))
            assertThat(debit.date).isEqualTo(LocalDate.of(2025, 12, 2))

            val debitTwo = result[2]
            assertThat(debitTwo.providerTransactionId).isEqualTo("12347")
            assertThat(debitTwo.creditDebitType).isEqualTo(CreditDebitType.DEBIT)
            assertThat(debitTwo.amount).isEqualByComparingTo(BigDecimal("1000.00"))
        }

        @Test
        fun `parses csv with debit and credit columns`() {
            val csv =
                """
                Data;Histórico;Débito;Crédito;Documento
                01/12/2025;Compra mercado;50,00;0,00;A-1
                02/12/2025;TED recebida;0,00;500,00;A-2
                """.trimIndent()

            val result = CsvItauParser.parse(csv.toByteArray(StandardCharsets.UTF_8))

            assertThat(result).hasSize(2)
            assertThat(result[0].creditDebitType).isEqualTo(CreditDebitType.DEBIT)
            assertThat(result[0].amount).isEqualByComparingTo(BigDecimal("50.00"))
            assertThat(result[1].creditDebitType).isEqualTo(CreditDebitType.CREDIT)
            assertThat(result[1].amount).isEqualByComparingTo(BigDecimal("500.00"))
        }
    }
}
