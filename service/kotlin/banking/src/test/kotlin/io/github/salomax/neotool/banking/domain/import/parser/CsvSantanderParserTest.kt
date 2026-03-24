package io.github.salomax.neotool.banking.service.import.parser

import io.github.salomax.neotool.banking.service.import.parser.*
import io.github.salomax.neotool.banking.service.import.*
import io.github.salomax.neotool.banking.service.import.parser.csv.CsvFingerprintBuilder
import io.github.salomax.neotool.banking.domain.transaction.CreditDebitType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.nio.charset.StandardCharsets
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

@DisplayName("CsvSantanderParser")
class CsvSantanderParserTest {
    private val saoPaulo = ZoneId.of("America/Sao_Paulo")

    private fun loadFixture(name: String): ByteArray =
        javaClass.getResourceAsStream("/fixtures/csv/$name")!!.readBytes()

    @Nested
    @DisplayName("reader contract")
    inner class ReaderContractTests {
        @Test
        fun `returns stable reader id`() {
            assertThat(CsvSantanderParser.id()).isEqualTo("csv-santander")
        }

        @Test
        fun `supports santander fingerprint with high score`() {
            val bytes = loadFixture("santander_account_sample.csv")
            val fingerprint = CsvFingerprintBuilder.build(bytes)

            val match = CsvSantanderParser.supports(fingerprint)

            assertThat(match.readerId).isEqualTo("csv-santander")
            assertThat(match.score).isGreaterThanOrEqualTo(0.85)
            assertThat(match.reasons).contains("core_columns_match", "santander_header_hints")
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

            val match = CsvSantanderParser.supports(fingerprint)

            assertThat(match.score).isLessThan(0.60)
        }
    }

    @Nested
    @DisplayName("parse")
    inner class ParseTests {
        @Test
        fun `parses santander fixture with time and amount`() {
            val bytes = loadFixture("santander_account_sample.csv")

            val result = CsvSantanderParser.parse(bytes)

            assertThat(result).hasSize(3)

            val credit = result[0]
            assertThat(credit.creditDebitType).isEqualTo(CreditDebitType.CREDIT)
            assertThat(credit.amount).isEqualByComparingTo(BigDecimal("1250.00"))
            assertThat(credit.date).isEqualTo(LocalDate.of(2026, 1, 2))
            assertThat(credit.transactionDatetime)
                .isEqualTo(LocalDateTime.of(2026, 1, 2, 8, 15).atZone(saoPaulo).toInstant())
            assertThat(credit.description).contains("PIX RECEBIDO")

            val debit = result[1]
            assertThat(debit.creditDebitType).isEqualTo(CreditDebitType.DEBIT)
            assertThat(debit.amount).isEqualByComparingTo(BigDecimal("350.45"))
            assertThat(debit.transactionDatetime)
                .isEqualTo(LocalDateTime.of(2026, 1, 2, 11, 42).atZone(saoPaulo).toInstant())

            val debitWithSeconds = result[2]
            assertThat(debitWithSeconds.creditDebitType).isEqualTo(CreditDebitType.DEBIT)
            assertThat(debitWithSeconds.amount).isEqualByComparingTo(BigDecimal("1000.00"))
            assertThat(debitWithSeconds.transactionDatetime)
                .isEqualTo(LocalDateTime.of(2026, 1, 3, 18, 30, 55).atZone(saoPaulo).toInstant())
        }

        @Test
        fun `parses without time column and keeps datetime null`() {
            val csv =
                """
                Data;Descricao;Valor;Saldo
                02/01/2026;PIX RECEBIDO;100,00;100,00
                03/01/2026;Pagamento boleto;-50,00;50,00
                """.trimIndent()

            val result = CsvSantanderParser.parse(csv.toByteArray(StandardCharsets.UTF_8))

            assertThat(result).hasSize(2)
            assertThat(result[0].transactionDatetime).isNull()
            assertThat(result[1].transactionDatetime).isNull()
            assertThat(result[0].creditDebitType).isEqualTo(CreditDebitType.CREDIT)
            assertThat(result[1].creditDebitType).isEqualTo(CreditDebitType.DEBIT)
        }
    }
}
