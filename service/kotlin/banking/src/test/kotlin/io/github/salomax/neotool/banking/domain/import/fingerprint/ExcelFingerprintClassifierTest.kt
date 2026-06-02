package io.github.salomax.neotool.banking.service.import.fingerprint

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("ExcelFingerprintClassifier")
class ExcelFingerprintClassifierTest {
    private val classifier = ExcelFingerprintClassifier()

    @Test
    fun `xlsx known bank statement tabular layout returns known classification`() {
        val result =
            classifier.classify(
                FingerprintClassificationInput(
                    fileType = FingerprintFileType.XLSX,
                    documentType = "BANK_STATEMENT",
                    extractedText = "| row | c1 | c2 | c3 |\n| 1 | Data | Descricao | Valor |",
                    extractionMetadata = mapOf("sheet_names" to listOf("Extrato")),
                ),
            )

        assertThat(result.status).isEqualTo(FingerprintClassification.KNOWN_PROFILE)
        assertThat(result.profileId).isEqualTo(ExcelFingerprintClassifier.PROFILE_BANK_STATEMENT_TABULAR_V1)
    }

    @Test
    fun `xlsx unknown layout returns unknown classification`() {
        val result =
            classifier.classify(
                FingerprintClassificationInput(
                    fileType = FingerprintFileType.XLSX,
                    documentType = "BANK_STATEMENT",
                    extractedText = null,
                    extractionMetadata = emptyMap(),
                ),
            )

        assertThat(result.status).isEqualTo(FingerprintClassification.UNKNOWN_PROFILE)
        assertThat(result.reason).isEqualTo("no_known_excel_signature_match")
    }

    @Test
    fun `xls returns unknown classification`() {
        val result =
            classifier.classify(
                FingerprintClassificationInput(
                    fileType = FingerprintFileType.XLS,
                    documentType = "BANK_STATEMENT",
                    extractedText = null,
                    extractionMetadata = emptyMap(),
                ),
            )

        assertThat(result.status).isEqualTo(FingerprintClassification.UNKNOWN_PROFILE)
        assertThat(result.reason).isEqualTo("no_known_excel_signature_match")
    }
}
