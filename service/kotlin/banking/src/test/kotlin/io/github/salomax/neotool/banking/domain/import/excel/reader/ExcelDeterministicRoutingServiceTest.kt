package io.github.salomax.neotool.banking.service.import.excel

import io.github.salomax.neotool.banking.domain.import.CanonicalProcessorType
import io.github.salomax.neotool.banking.service.import.fingerprint.ExcelFingerprintClassifier
import io.github.salomax.neotool.banking.service.import.fingerprint.FingerprintFileType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("ExcelDeterministicRoutingService")
class ExcelDeterministicRoutingServiceTest {
    private val service = ExcelDeterministicRoutingService()
    private val knownExtractedText =
        """
        workbook_type=XLSX
        sheet_count=1

        ## sheet[0] Extrato
        | row | c1 | c2 | c3 |
        | --- | --- | --- | --- |
        | 1 | Data | Descricao | Valor |
        | 2 | 03/02/2026 | Salario | 3200,00 |
        """.trimIndent()

    @Test
    fun `known excel fingerprint routes to deterministic reader`() {
        val output =
            service.route(
                fileType = FingerprintFileType.XLSX,
                documentType = "BANK_STATEMENT",
                extractedText = knownExtractedText,
                extractionMetadata =
                    mapOf(
                        "sheet_names" to listOf("Extrato"),
                        "reference_year" to 2026,
                    ),
            )

        assertThat(output).isNotNull
        assertThat(output?.processorType).isEqualTo(CanonicalProcessorType.DETERMINISTIC)
        assertThat(output?.readerId).isEqualTo("excel-reader-bank-statement-tabular-v1")
        assertThat(output?.normalizedRecords).hasSize(1)
    }

    @Test
    fun `unknown excel fingerprint returns null`() {
        val output =
            service.route(
                fileType = FingerprintFileType.XLSX,
                documentType = "BANK_STATEMENT",
                extractedText = "workbook_type=XLSX\nsheet_count=1\n## sheet[0] Planilha\n(empty)\n",
                extractionMetadata = mapOf("sheet_names" to listOf("Planilha")),
            )

        assertThat(output).isNull()
    }

    @Test
    fun `reader supports classifier profile id`() {
        assertThat(TabularBankStatementExcelReader().supportedProfileIds)
            .contains(ExcelFingerprintClassifier.PROFILE_BANK_STATEMENT_TABULAR_V1)
    }
}
