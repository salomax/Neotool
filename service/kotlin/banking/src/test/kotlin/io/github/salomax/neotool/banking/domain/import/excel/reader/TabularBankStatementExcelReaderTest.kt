package io.github.salomax.neotool.banking.service.import.excel

import io.github.salomax.neotool.banking.domain.import.CanonicalProcessorType
import io.github.salomax.neotool.banking.service.import.fingerprint.ExcelFingerprintClassifier
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.math.BigDecimal

@DisplayName("TabularBankStatementExcelReader")
class TabularBankStatementExcelReaderTest {
    private val reader = TabularBankStatementExcelReader()

    private fun loadFixture(name: String): String =
        javaClass.getResourceAsStream("/fixtures/excel/$name")!!.readBytes().toString(Charsets.UTF_8)

    @Test
    fun `parses known tabular profile fixture into canonical deterministic output`() {
        val extractedText = loadFixture("bank_statement_tabular_known_profile_extracted.txt")

        val output =
            reader.parse(
                DeterministicExcelReaderInput(
                    profileId = ExcelFingerprintClassifier.PROFILE_BANK_STATEMENT_TABULAR_V1,
                    documentType = "BANK_STATEMENT",
                    extractedText = extractedText,
                    extractionMetadata =
                        mapOf(
                            "reference_year" to 2026,
                            "sheet_names" to listOf("Extrato"),
                        ),
                ),
            )

        assertThat(output.processorType).isEqualTo(CanonicalProcessorType.DETERMINISTIC)
        assertThat(output.readerId).isEqualTo("excel-reader-bank-statement-tabular-v1")
        assertThat(output.confidence).isEqualTo(0.95)
        assertThat(output.warnings).doesNotContain("no_transaction_rows_parsed")
        assertThat(output.normalizedRecords).hasSize(3)

        val first = output.normalizedRecords[0]
        assertThat(first["record_type"]).isEqualTo("TRANSACTION")
        assertThat(first["date"]).isEqualTo("2026-02-03")
        assertThat(first["description"]).isEqualTo("Salario Empresa XYZ")
        assertThat(first["amount"]).isEqualTo(BigDecimal("5600.00"))
        assertThat(first["credit_debit_type"]).isEqualTo("CREDIT")

        val second = output.normalizedRecords[1]
        assertThat(second["date"]).isEqualTo("2026-02-04")
        assertThat(second["amount"]).isEqualTo(BigDecimal("120.45"))
        assertThat(second["credit_debit_type"]).isEqualTo("DEBIT")

        val third = output.normalizedRecords[2]
        assertThat(third["date"]).isEqualTo("2026-02-05")
        assertThat(third["amount"]).isEqualTo(BigDecimal("250.00"))
        assertThat(third["credit_debit_type"]).isEqualTo("CREDIT")
    }

    @Test
    fun `returns warnings and zero confidence when no parseable transaction rows`() {
        val output =
            reader.parse(
                DeterministicExcelReaderInput(
                    profileId = ExcelFingerprintClassifier.PROFILE_BANK_STATEMENT_TABULAR_V1,
                    documentType = "BANK_STATEMENT",
                    extractedText = "workbook_type=XLSX\nsheet_count=1\n## sheet[0] Extrato\n(empty)\n",
                    extractionMetadata = emptyMap(),
                ),
            )

        assertThat(output.confidence).isEqualTo(0.0)
        assertThat(output.normalizedRecords).isEmpty()
        assertThat(output.warnings).contains("no_transaction_rows_parsed", "missing_reference_year")
    }

    @Test
    fun `parse is deterministic and reproducible for same input`() {
        val extractedText = loadFixture("bank_statement_tabular_known_profile_extracted.txt")
        val input =
            DeterministicExcelReaderInput(
                profileId = ExcelFingerprintClassifier.PROFILE_BANK_STATEMENT_TABULAR_V1,
                documentType = "BANK_STATEMENT",
                extractedText = extractedText,
                extractionMetadata = mapOf("reference_year" to 2026),
            )

        val first = reader.parse(input)
        val second = reader.parse(input)

        assertThat(first).isEqualTo(second)
    }
}
