package io.github.salomax.neotool.banking.domain.import.statement

import io.github.salomax.neotool.banking.domain.transaction.CreditDebitType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.math.BigDecimal

@DisplayName("AssistantStatementTransactionExtractor")
class AssistantStatementTransactionExtractorTest {
    private val extractor = AssistantStatementTransactionExtractor()

    @Test
    fun `extract parses grouped multiline same-day transfers as separate rows`() {
        val rawText =
            """
            Marcos Afonso Salomão Junior
            CPF •••.885.958-•• Agência 0001 Conta
            13560704-2
            01 DE JANEIRO DE 2025 a 31 DE JANEIRO DE 2025 VALORES EM R$
            07 JAN 2025 Total de saídas - 550,00
            Transferência enviada pelo Pix Antonio Carlos Guidoni - •••.470.428-•• - BCO DO 550,00
            BRASIL S.A. (0001) Agência: 7079 Conta: 35017-6
            08 JAN 2025 Total de saídas - 7.000,00
            Transferência enviada Marcos Afonso Salomao Junior - •••.885.958-•• - XP 1.000,00
            Investimentos Corretora de Câmbio Títulos e
            Valores Mobiliários S.A. (0102) Agência: 1 Conta:
            679873-5
            Transferência enviada pelo Pix LILIAN PRISCILLA ONISHI SALOMAO - •••.672.098- 6.000,00
            •• - BCO DO BRASIL S.A. (0001) Agência: 4851
            Conta: 6263735-5
            """.trimIndent()

        val result =
            extractor.extract(
                rawText = rawText,
                meta =
                    AssistantStatementExtractionMeta(
                        bankName = "Nubank",
                        statementPeriod = AssistantStatementPeriod(from = "2025-01-01", to = "2025-01-31"),
                        parserConfig =
                            AssistantStatementParserConfig(
                                layoutType = "DATE_GROUPED_MULTILINE",
                                dateFormat = "DD_MMM_YYYY",
                                dateHeaderPattern = "^(\\d{2} DE [A-Z]+ DE \\d{4})$",
                                amountPattern = "([\\d.,]+)",
                                decimalSeparator = "comma",
                                signRule = "KEYWORD_BASED",
                                ignoreLineKinds = listOf("DAY_TOTAL", "RUNNING_BALANCE", "SECTION_HEADER", "HEADER", "FOOTER"),
                                examples = listOf("Transferência enviada pelo Pix Maria Jose da Silva - •••.311.704-•• - BCO 250,00"),
                                defaultCreditDebitType = "DEBIT",
                            ),
                    ),
            )

        assertThat(result.warnings).doesNotContain("no_transaction_rows_parsed")
        assertThat(result.rows).hasSize(3)
        assertThat(result.rows.map { it.date.toString() }).containsExactly("2025-01-07", "2025-01-08", "2025-01-08")
        assertThat(result.rows.map { it.amount }).containsExactly(
            BigDecimal("550.00"),
            BigDecimal("1000.00"),
            BigDecimal("6000.00"),
        )
        assertThat(result.rows.map { it.creditDebitType }).containsOnly(CreditDebitType.DEBIT)
        assertThat(result.rows.map { it.description })
            .anySatisfy { description -> assertThat(description).contains("Antonio Carlos Guidoni", "BRASIL S.A.") }
            .anySatisfy { description -> assertThat(description).contains("Marcos Afonso Salomao Junior", "XP Investimentos") }
            .anySatisfy { description -> assertThat(description).contains("LILIAN PRISCILLA ONISHI SALOMAO", "BCO DO BRASIL S.A.") }
    }
}
