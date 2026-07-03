package io.github.salomax.neotool.banking.service.billimport

import io.github.salomax.neotool.banking.domain.billimport.InstallmentInfo
import io.github.salomax.neotool.banking.domain.transaction.CreditDebitType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.math.BigDecimal
import java.nio.charset.StandardCharsets
import java.time.LocalDate

@DisplayName("CsvXpBillParser")
class CsvXpBillParserTest {
    @Nested
    @DisplayName("layout detection")
    inner class LayoutDetectionTests {
        @Test
        fun `detects XP layout with holder column`() {
            val csv =
                """
                Data;Estabelecimento;Portador;Valor;Parcela
                01/03/2026;Mercado Extra;Joao Silva;R$ 45,90;1 de 3
                """.trimIndent()

            assertThat(CsvXpBillParser.detectLayout(csv.toByteArray(StandardCharsets.UTF_8)))
                .isEqualTo(XpBillCsvLayout.WITH_HOLDER)
        }

        @Test
        fun `detects XP layout without holder column`() {
            val csv =
                """
                Data;Estabelecimento;Valor;Parcela
                01/03/2026;Mercado Extra;R$ 45,90;-
                """.trimIndent()

            assertThat(CsvXpBillParser.detectLayout(csv.toByteArray(StandardCharsets.UTF_8)))
                .isEqualTo(XpBillCsvLayout.WITHOUT_HOLDER)
        }

        @Test
        fun `returns null for unsupported CSV header`() {
            val csv =
                """
                Data,Descricao,Valor
                01/03/2026,Compra,45.90
                """.trimIndent()

            assertThat(CsvXpBillParser.detectLayout(csv.toByteArray(StandardCharsets.UTF_8))).isNull()
        }
    }

    @Nested
    @DisplayName("row parsing")
    inner class RowParsingTests {
        @Test
        fun `parses XP rows with holder and installment`() {
            val csv =
                """
                Data;Estabelecimento;Portador;Valor;Parcela
                01/03/2026;Mercado Extra;Joao Silva;R$ 45,90;1 de 3
                02/03/2026;Farmacia;Maria Souza;R$ -51,15;-
                """.trimIndent()

            val rows = CsvXpBillParser.parse(csv.toByteArray(StandardCharsets.UTF_8), XpBillCsvLayout.WITH_HOLDER)

            assertThat(rows).hasSize(2)
            assertThat(rows[0]).isEqualTo(
                XpBillCsvRow(
                    date = LocalDate.of(2026, 3, 1),
                    merchant = "Mercado Extra",
                    holderName = "Joao Silva",
                    signedAmount = BigDecimal("45.90"),
                    installment = InstallmentInfo(current = 1, total = 3),
                ),
            )
            assertThat(rows[1].signedAmount).isEqualByComparingTo(BigDecimal("-51.15"))
            assertThat(rows[1].installment).isNull()
        }

        @Test
        fun `throws when a data row is malformed`() {
            val csv =
                """
                Data;Estabelecimento;Valor;Parcela
                01/03/2026;Mercado Extra;R$ 45,90;-
                invalid-date;Farmacia;R$ 50,00;-
                """.trimIndent()

            val ex =
                assertThrows<XpBillCsvRowParseException> {
                    CsvXpBillParser.parse(csv.toByteArray(StandardCharsets.UTF_8), XpBillCsvLayout.WITHOUT_HOLDER)
                }

            assertThat(ex.lineNumber).isEqualTo(3)
        }

        @Test
        fun `parses XP rows without holder column`() {
            val csv =
                """
                Data;Estabelecimento;Valor;Parcela
                07/03/2026;Posto Shell;R$ 120,00;7 de 7
                """.trimIndent()

            val rows = CsvXpBillParser.parse(csv.toByteArray(StandardCharsets.UTF_8), XpBillCsvLayout.WITHOUT_HOLDER)

            assertThat(rows).singleElement().isEqualTo(
                XpBillCsvRow(
                    date = LocalDate.of(2026, 3, 7),
                    merchant = "Posto Shell",
                    holderName = null,
                    signedAmount = BigDecimal("120.00"),
                    installment = InstallmentInfo(current = 7, total = 7),
                ),
            )
        }
    }

    @Nested
    @DisplayName("installment and amount semantics")
    inner class SemanticsTests {
        @Test
        fun `parseInstallment maps de pattern and dash`() {
            assertThat(CsvXpBillParser.parseInstallment("1 de 3"))
                .isEqualTo(InstallmentInfo(current = 1, total = 3))
            assertThat(CsvXpBillParser.parseInstallment("7 DE 7"))
                .isEqualTo(InstallmentInfo(current = 7, total = 7))
            assertThat(CsvXpBillParser.parseInstallment("-")).isNull()
        }

        @Test
        fun `toCreditDebitType maps negative amounts to credit`() {
            val (creditType, creditAmount) = CsvXpBillParser.toCreditDebitType(BigDecimal("-51.15"))
            assertThat(creditType).isEqualTo(CreditDebitType.CREDIT)
            assertThat(creditAmount).isEqualByComparingTo(BigDecimal("51.15"))

            val (debitType, debitAmount) = CsvXpBillParser.toCreditDebitType(BigDecimal("45.90"))
            assertThat(debitType).isEqualTo(CreditDebitType.DEBIT)
            assertThat(debitAmount).isEqualByComparingTo(BigDecimal("45.90"))
        }
    }
}
