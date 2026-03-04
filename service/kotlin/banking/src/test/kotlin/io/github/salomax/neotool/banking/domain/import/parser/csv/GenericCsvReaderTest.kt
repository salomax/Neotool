package io.github.salomax.neotool.banking.domain.import.parser.csv

import io.github.salomax.neotool.banking.domain.transaction.CreditDebitType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.nio.charset.StandardCharsets
import java.time.LocalDate

@DisplayName("GenericCsvReader")
class GenericCsvReaderTest {

    @Nested
    @DisplayName("parse")
    inner class ParseTests {

        @Test
        fun `parses csv with explicit headers and amount column`() {
            val csv = """
                date,amount,description,id
                2026-01-01,-10.50,Coffee,tx-1
                2026-01-02,25.00,Refund,tx-2
            """.trimIndent()

            val result = GenericCsvReader.parse(csv.toByteArray(StandardCharsets.UTF_8))

            assertThat(result).hasSize(2)

            assertThat(result[0].providerTransactionId).isEqualTo("tx-1")
            assertThat(result[0].creditDebitType).isEqualTo(CreditDebitType.DEBIT)
            assertThat(result[0].amount).isEqualByComparingTo(BigDecimal("10.50"))
            assertThat(result[0].date).isEqualTo(LocalDate.of(2026, 1, 1))
            assertThat(result[0].description).isEqualTo("Coffee")

            assertThat(result[1].providerTransactionId).isEqualTo("tx-2")
            assertThat(result[1].creditDebitType).isEqualTo(CreditDebitType.CREDIT)
            assertThat(result[1].amount).isEqualByComparingTo(BigDecimal("25.00"))
        }

        @Test
        fun `parses semicolon csv with br date and amount formats`() {
            val csv = """
                Data;Valor;Descrição;Identificador
                01/01/2026;-1.234,56;Transferência enviada via Pix;abc-1
                02/01/2026;950,10;Pagamento de boleto;abc-2
            """.trimIndent()

            val result = GenericCsvReader.parse(csv.toByteArray(StandardCharsets.UTF_8))

            assertThat(result).hasSize(2)
            assertThat(result[0].providerTransactionId).isEqualTo("abc-1")
            assertThat(result[0].creditDebitType).isEqualTo(CreditDebitType.DEBIT)
            assertThat(result[0].amount).isEqualByComparingTo(BigDecimal("1234.56"))
            assertThat(result[0].description).contains("Transferência enviada via Pix")

            assertThat(result[1].providerTransactionId).isEqualTo("abc-2")
            assertThat(result[1].creditDebitType).isEqualTo(CreditDebitType.CREDIT)
            assertThat(result[1].amount).isEqualByComparingTo(BigDecimal("950.10"))
        }

        @Test
        fun `parses csv without header using inferred columns`() {
            val csv = """
                2026-01-01,-10.50,Pix enviado para Joao
                2026-01-02,20.00,Transferencia recebida
            """.trimIndent()

            val result = GenericCsvReader.parse(csv.toByteArray(StandardCharsets.UTF_8))

            assertThat(result).hasSize(2)
            assertThat(result[0].creditDebitType).isEqualTo(CreditDebitType.DEBIT)
            assertThat(result[0].date).isEqualTo(LocalDate.of(2026, 1, 1))
            assertThat(result[0].description).contains("Pix enviado")

            assertThat(result[1].creditDebitType).isEqualTo(CreditDebitType.CREDIT)
            assertThat(result[1].amount).isEqualByComparingTo(BigDecimal("20.00"))
        }

        @Test
        fun `parses csv with debit and credit columns when amount is absent`() {
            val csv = """
                Data,Debito,Credito,Historico
                01/01/2026,50,0,Compra mercado
                02/01/2026,0,100,Transferencia recebida
            """.trimIndent()

            val result = GenericCsvReader.parse(csv.toByteArray(StandardCharsets.UTF_8))

            assertThat(result).hasSize(2)
            assertThat(result[0].creditDebitType).isEqualTo(CreditDebitType.DEBIT)
            assertThat(result[0].amount).isEqualByComparingTo(BigDecimal("50"))
            assertThat(result[1].creditDebitType).isEqualTo(CreditDebitType.CREDIT)
            assertThat(result[1].amount).isEqualByComparingTo(BigDecimal("100"))
        }

        @Test
        fun `skips rows that do not have minimum parseable fields`() {
            val csv = """
                date,amount,description
                invalid,-10.00,Row invalid date
                2026-01-02,abc,Row invalid amount
                2026-01-03,20.00,Row valid
            """.trimIndent()

            val result = GenericCsvReader.parse(csv.toByteArray(StandardCharsets.UTF_8))

            assertThat(result).hasSize(1)
            assertThat(result[0].date).isEqualTo(LocalDate.of(2026, 1, 3))
            assertThat(result[0].amount).isEqualByComparingTo(BigDecimal("20.00"))
        }

        @Test
        fun `ignores provider transaction id when document column repeats heavily`() {
            val csv = """
                Data,Valor,Descrição,Nº documento
                01/02/2026,-10,Pix enviado,9903
                02/02/2026,-20,Compra mercado,9903
                03/02/2026,50,Transferência,9903
            """.trimIndent()

            val result = GenericCsvReader.parse(csv.toByteArray(StandardCharsets.UTF_8))

            assertThat(result).hasSize(3)
            assertThat(result).allSatisfy { row -> assertThat(row.providerTransactionId).isNull() }
        }
    }

    @Test
    fun `supports returns high score when date and amount patterns exist`() {
        val csv = """
            date,amount,description
            2026-01-01,-10.50,Pix enviado
        """.trimIndent()
        val fingerprint = CsvFingerprintBuilder.build(csv.toByteArray(StandardCharsets.UTF_8))

        val match = GenericCsvReader.supports(fingerprint)

        assertThat(match.readerId).isEqualTo("csv-generic")
        assertThat(match.score).isGreaterThan(0.80)
        assertThat(match.reasons).contains("date_pattern_detected", "amount_pattern_detected")
    }
}
