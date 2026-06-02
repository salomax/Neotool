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

@DisplayName("CsvNubankParser")
class CsvNubankParserTest {
    private val expectedCardHeader = "date,transaction_type,title,amount,category,charges,Identificador"
    private val expectedAccountHeader = "Data,Valor,Identificador,Descrição"

    private fun loadFixture(name: String): ByteArray =
        javaClass.getResourceAsStream("/fixtures/csv/$name")!!.readBytes()

    @Nested
    @DisplayName("reader contract")
    inner class ReaderContractTests {
        @Test
        fun `returns stable reader id`() {
            assertThat(CsvNubankParser.id()).isEqualTo("csv-nubank")
        }

        @Test
        fun `supports nubank account fingerprint with high score`() {
            val bytes = loadFixture("nubank_account_sample.csv")
            val fingerprint = CsvFingerprintBuilder.build(bytes)

            val match = CsvNubankParser.supports(fingerprint)

            assertThat(match.readerId).isEqualTo("csv-nubank")
            assertThat(match.score).isGreaterThanOrEqualTo(0.85)
            assertThat(match.reasons).contains("exact_nubank_account_header")
        }

        @Test
        fun `supports nubank card fingerprint with high score`() {
            val bytes = loadFixture("nubank_sample.csv")
            val fingerprint = CsvFingerprintBuilder.build(bytes)

            val match = CsvNubankParser.supports(fingerprint)

            assertThat(match.readerId).isEqualTo("csv-nubank")
            assertThat(match.score).isGreaterThanOrEqualTo(0.85)
            assertThat(match.reasons).contains("exact_nubank_card_header")
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

            val match = CsvNubankParser.supports(fingerprint)

            assertThat(match.readerId).isEqualTo("csv-nubank")
            assertThat(match.score).isLessThan(0.60)
        }
    }

    @Nested
    @DisplayName("parse — fixture")
    inner class ParseFixtureTests {
        @Test
        fun `parses nubank_sample csv and returns expected transactions`() {
            val bytes = loadFixture("nubank_sample.csv")

            val result = CsvNubankParser.parse(bytes)

            assertThat(result).hasSize(4)

            val coffee = result[0]
            assertThat(coffee.providerTransactionId).isEqualTo("abc-001")
            assertThat(coffee.creditDebitType).isEqualTo(CreditDebitType.DEBIT)
            assertThat(coffee.amount).isEqualByComparingTo(BigDecimal("25.50"))
            assertThat(coffee.date).isEqualTo(LocalDate.of(2024, 1, 15))
            assertThat(coffee.description).isEqualTo("Coffee Shop")
            assertThat(coffee.descriptionRaw).isEqualTo("Food")
            assertThat(coffee.totalInstallments).isNull()
            assertThat(coffee.currencyCode).isEqualTo("BRL")

            val payment = result[1]
            assertThat(payment.providerTransactionId).isEqualTo("abc-002")
            assertThat(payment.creditDebitType).isEqualTo(CreditDebitType.CREDIT)
            assertThat(payment.amount).isEqualByComparingTo(BigDecimal("500.00"))
            assertThat(payment.date).isEqualTo(LocalDate.of(2024, 1, 16))
            assertThat(payment.description).isEqualTo("Card payment")
            assertThat(payment.totalInstallments).isEqualTo(1)

            val supermarket = result[2]
            assertThat(supermarket.providerTransactionId).isEqualTo("abc-003")
            assertThat(supermarket.creditDebitType).isEqualTo(CreditDebitType.DEBIT)
            assertThat(supermarket.amount).isEqualByComparingTo(BigDecimal("120.30"))
            assertThat(supermarket.totalInstallments).isEqualTo(3)

            val restaurant = result[3]
            assertThat(restaurant.providerTransactionId).isEqualTo("abc-004")
            assertThat(restaurant.description).isEqualTo("Restaurant, Bar")
            assertThat(restaurant.creditDebitType).isEqualTo(CreditDebitType.DEBIT)
            assertThat(restaurant.amount).isEqualByComparingTo(BigDecimal("89.00"))
        }

        @Test
        fun `parses nubank account csv and returns expected transactions`() {
            val bytes = loadFixture("nubank_account_sample.csv")

            val result = CsvNubankParser.parse(bytes)

            assertThat(result).hasSize(3)

            val debit = result[0]
            assertThat(debit.providerTransactionId).isEqualTo("acc-001")
            assertThat(debit.creditDebitType).isEqualTo(CreditDebitType.DEBIT)
            assertThat(debit.amount).isEqualByComparingTo(BigDecimal("300.00"))
            assertThat(debit.date).isEqualTo(LocalDate.of(2025, 11, 1))
            assertThat(debit.description).contains("Pix")

            val credit = result[1]
            assertThat(credit.providerTransactionId).isEqualTo("acc-002")
            assertThat(credit.creditDebitType).isEqualTo(CreditDebitType.CREDIT)
            assertThat(credit.amount).isEqualByComparingTo(BigDecimal("35206.39"))
            assertThat(credit.date).isEqualTo(LocalDate.of(2025, 11, 3))

            val boleto = result[2]
            assertThat(boleto.providerTransactionId).isEqualTo("acc-003")
            assertThat(boleto.creditDebitType).isEqualTo(CreditDebitType.DEBIT)
            assertThat(boleto.amount).isEqualByComparingTo(BigDecimal("230.50"))
        }

        @Test
        fun `parses real nubank account sample with accents and masked document`() {
            val csv =
                """
                Data,Valor,Identificador,Descrição
                01/11/2025,-300.00,69064c80-bd30-48e2-b312-e6fc65485a31,Transferência enviada pelo Pix - MARIA JOSE DA SILVA - •••.311.704-•• - BCO SANTANDER (BRASIL) S.A. (0033) Agência: 3200 Conta: 2000051-7
                03/11/2025,35206.39,6908ac71-4354-4dfb-87b7-297cfc9bd1d2,Transferência Recebida - MARCOS AFONSO SALOMAO JUNIOR - •••.885.958-•• - XP Investimentos Corretora de Câmbio Títulos e Valores Mobiliários S.A. (0102) Agência: 1 Conta: 297233-5
                03/11/2025,-75000.00,6908dc8c-43e3-4bd8-9bcb-86565e95c2b3,Transferência enviada pelo Pix - Marcos Afonso Salomao Junior - •••.885.958-•• - BANCO BTG PACTUAL S.A. (0208) Agência: 1 Conta: 572434-9
                """.trimIndent()

            val result = CsvNubankParser.parse(csv.toByteArray(StandardCharsets.UTF_8))

            assertThat(result).hasSize(3)
            assertThat(result[0].providerTransactionId).isEqualTo("69064c80-bd30-48e2-b312-e6fc65485a31")
            assertThat(result[0].creditDebitType).isEqualTo(CreditDebitType.DEBIT)
            assertThat(result[0].amount).isEqualByComparingTo(BigDecimal("300.00"))
            assertThat(result[0].description).contains("Transferência enviada pelo Pix")

            assertThat(result[1].providerTransactionId).isEqualTo("6908ac71-4354-4dfb-87b7-297cfc9bd1d2")
            assertThat(result[1].creditDebitType).isEqualTo(CreditDebitType.CREDIT)
            assertThat(result[1].amount).isEqualByComparingTo(BigDecimal("35206.39"))

            assertThat(result[2].providerTransactionId).isEqualTo("6908dc8c-43e3-4bd8-9bcb-86565e95c2b3")
            assertThat(result[2].creditDebitType).isEqualTo(CreditDebitType.DEBIT)
            assertThat(result[2].amount).isEqualByComparingTo(BigDecimal("75000.00"))
        }
    }

    @Nested
    @DisplayName("parse — header")
    inner class ParseHeaderTests {
        @Test
        fun `returns empty list when header does not match`() {
            val csv = "other,columns,here\n2024-01-01,purchase,Title,10.00,,,id-1"
            val result = CsvNubankParser.parse(csv.toByteArray(StandardCharsets.UTF_8))

            assertThat(result).isEmpty()
        }

        @Test
        fun `returns empty list when bytes are empty`() {
            val result = CsvNubankParser.parse(ByteArray(0))

            assertThat(result).isEmpty()
        }

        @Test
        fun `returns empty list when only header no data rows`() {
            val result = CsvNubankParser.parse(expectedCardHeader.toByteArray(StandardCharsets.UTF_8))

            assertThat(result).isEmpty()
        }

        @Test
        fun `accepts header case insensitive`() {
            val csv =
                "DATE,TRANSACTION_TYPE,TITLE,AMOUNT,CATEGORY,CHARGES,IDENTIFICADOR\n" +
                    "2024-01-01,purchase,Title,10.00,,,id-1"
            val result = CsvNubankParser.parse(csv.toByteArray(StandardCharsets.UTF_8))

            assertThat(result).hasSize(1)
            assertThat(result[0].providerTransactionId).isEqualTo("id-1")
        }

        @Test
        fun `accepts account header with and without accents`() {
            val csvWithAccent = "$expectedAccountHeader\n01/11/2025,-10.00,id-1,Pix enviado"
            val csvWithoutAccent = "DATA,VALOR,IDENTIFICADOR,DESCRICAO\n01/11/2025,-10.00,id-2,Pix enviado"

            val resultWithAccent = CsvNubankParser.parse(csvWithAccent.toByteArray(StandardCharsets.UTF_8))
            val resultWithoutAccent = CsvNubankParser.parse(csvWithoutAccent.toByteArray(StandardCharsets.UTF_8))

            assertThat(resultWithAccent).hasSize(1)
            assertThat(resultWithoutAccent).hasSize(1)
            assertThat(resultWithAccent[0].providerTransactionId).isEqualTo("id-1")
            assertThat(resultWithoutAccent[0].providerTransactionId).isEqualTo("id-2")
        }

        @Test
        fun `accepts header with UTF-8 BOM`() {
            val csv = "\uFEFF$expectedAccountHeader\n01/11/2025,-10.00,id-1,Pix enviado"

            val result = CsvNubankParser.parse(csv.toByteArray(StandardCharsets.UTF_8))

            assertThat(result).hasSize(1)
            assertThat(result[0].providerTransactionId).isEqualTo("id-1")
        }
    }

    @Nested
    @DisplayName("parse — row filtering")
    inner class ParseRowFilteringTests {
        @Test
        fun `skips rows with blank Identificador`() {
            val csv =
                """
                $expectedCardHeader
                2024-01-01,purchase,With Id,10.00,,,id-a
                2024-01-02,purchase,No Id,20.00,,,
                2024-01-03,debit,With Id 2,30.00,,,id-b
                """.trimIndent()
            val result = CsvNubankParser.parse(csv.toByteArray(StandardCharsets.UTF_8))

            assertThat(result).hasSize(2)
            assertThat(result[0].providerTransactionId).isEqualTo("id-a")
            assertThat(result[1].providerTransactionId).isEqualTo("id-b")
        }

        @Test
        fun `skips rows with negative amount`() {
            val csv =
                """
                $expectedCardHeader
                2024-01-01,purchase,Negative,-15.00,,,id-neg
                """.trimIndent()
            val result = CsvNubankParser.parse(csv.toByteArray(StandardCharsets.UTF_8))

            assertThat(result).isEmpty()
        }

        @Test
        fun `skips rows with fewer than 7 fields`() {
            val csv =
                """
                $expectedCardHeader
                2024-01-01,purchase,Short
                """.trimIndent()
            val result = CsvNubankParser.parse(csv.toByteArray(StandardCharsets.UTF_8))

            assertThat(result).isEmpty()
        }
    }

    @Nested
    @DisplayName("parse — transaction type")
    inner class ParseTransactionTypeTests {
        @Test
        fun `maps payment and credit to CREDIT`() {
            val csv =
                """
                $expectedCardHeader
                2024-01-01,payment,Pay,100.00,,,id-1
                2024-01-02,credit,Refund,50.00,,,id-2
                """.trimIndent()
            val result = CsvNubankParser.parse(csv.toByteArray(StandardCharsets.UTF_8))

            assertThat(result[0].creditDebitType).isEqualTo(CreditDebitType.CREDIT)
            assertThat(result[1].creditDebitType).isEqualTo(CreditDebitType.CREDIT)
        }

        @Test
        fun `maps purchase debit compra to DEBIT`() {
            val csv =
                """
                $expectedCardHeader
                2024-01-01,purchase,Purchase,10.00,,,id-1
                2024-01-02,debit,Debit,20.00,,,id-2
                2024-01-03,compra,Compra,30.00,,,id-3
                """.trimIndent()
            val result = CsvNubankParser.parse(csv.toByteArray(StandardCharsets.UTF_8))

            assertThat(result).allMatch { it.creditDebitType == CreditDebitType.DEBIT }
        }

        @Test
        fun `defaults unknown transaction_type to DEBIT`() {
            val csv =
                """
                $expectedCardHeader
                2024-01-01,unknown,Other,5.00,,,id-1
                """.trimIndent()
            val result = CsvNubankParser.parse(csv.toByteArray(StandardCharsets.UTF_8))

            assertThat(result).hasSize(1)
            assertThat(result[0].creditDebitType).isEqualTo(CreditDebitType.DEBIT)
        }
    }

    @Nested
    @DisplayName("parse — amount and format")
    inner class ParseAmountTests {
        @Test
        fun `parses amount with comma as decimal separator when quoted`() {
            val csv =
                """
                $expectedCardHeader
                2024-01-01,purchase,Item,"99,99",,,id-1
                """.trimIndent()
            val result = CsvNubankParser.parse(csv.toByteArray(StandardCharsets.UTF_8))

            assertThat(result).hasSize(1)
            assertThat(result[0].amount).isEqualByComparingTo(BigDecimal("99.99"))
        }
    }
}
