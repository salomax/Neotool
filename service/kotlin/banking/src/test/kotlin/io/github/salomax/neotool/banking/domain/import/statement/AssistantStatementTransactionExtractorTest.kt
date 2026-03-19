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
            04 JAN 2025 Total de saídas - 2.250,00
            Transferência enviada pelo Pix LILIAN PRISCILLA ONISHI SALOMAO - •••.672.098- 2.000,00
            •• - BCO DO BRASIL S.A. (0001) Agência: 4851
            Conta: 6263735-5
            Transferência enviada pelo Pix Maria Jose da Silva - •••.311.704-•• - BCO 250,00
            SANTANDER (BRASIL) S.A. (0033) Agência: 3200
            Conta: 2000051-7
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
                                dateHeaderPattern = "^(\\d{2}\\s+[A-Z]{3}\\s+\\d{4})\\b",
                                amountPattern = "([\\-−]?[\\d.]+,\\d{2})",
                                decimalSeparator = "comma",
                                creditPatterns = listOf("recebid"),
                                debitPatterns = listOf("enviad"),
                                skipLinePatterns = listOf("^.*Total de saídas.*$", "^Saldo.*$"),
                                defaultCreditDebitType = "DEBIT",
                            ),
                    ),
            )

        assertThat(result.warnings).doesNotContain("no_transaction_rows_parsed")
        assertThat(result.rows).hasSize(2)
        assertThat(result.rows.map { it.date.toString() }).containsOnly("2025-01-04")
        assertThat(result.rows.map { it.amount }).containsExactlyInAnyOrder(BigDecimal("2000.00"), BigDecimal("250.00"))
        assertThat(result.rows.map { it.creditDebitType }).containsOnly(CreditDebitType.DEBIT)
        assertThat(result.rows.map { it.description })
            .anySatisfy { description -> assertThat(description).contains("LILIAN PRISCILLA ONISHI SALOMAO", "BCO DO BRASIL S.A.") }
            .anySatisfy { description -> assertThat(description).contains("Maria Jose da Silva", "SANTANDER (BRASIL) S.A.") }
    }
}
