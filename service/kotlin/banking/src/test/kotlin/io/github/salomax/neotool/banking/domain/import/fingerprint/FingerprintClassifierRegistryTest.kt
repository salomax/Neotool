package io.github.salomax.neotool.banking.service.import.fingerprint

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("FingerprintClassifierRegistry")
class FingerprintClassifierRegistryTest {
    private val registry =
        FingerprintClassifierRegistry(
            listOf(
                PdfFingerprintClassifier(),
                ExcelFingerprintClassifier(),
            ),
        )

    @Test
    fun `registry routes pdf classification to pdf classifier`() {
        val result =
            registry.classify(
                FingerprintClassificationInput(
                    fileType = FingerprintFileType.PDF,
                    documentType = "CREDIT_CARD_BILL",
                    extractedText = "Nubank\nResumo da Fatura\nTotal a Pagar",
                    extractionMetadata = emptyMap(),
                ),
            )

        assertThat(result.status).isEqualTo(FingerprintClassification.KNOWN_PROFILE)
        assertThat(result.profileId).isEqualTo("pdf-nubank-credit-card-bill-v1")
    }

    @Test
    fun `registry routes known xlsx bank statement classification to excel reader profile`() {
        val result =
            registry.classify(
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
}
