package io.github.salomax.neotool.banking.service.import.fingerprint

import io.github.salomax.neotool.banking.service.import.fingerprint.*
import io.github.salomax.neotool.banking.service.import.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("PdfFingerprintClassifier")
class PdfFingerprintClassifierTest {
    private val classifier = PdfFingerprintClassifier()

    @Test
    fun `returns known profile from direct metadata profile_id`() {
        val result =
            classifier.classify(
                FingerprintClassificationInput(
                    fileType = FingerprintFileType.PDF,
                    documentType = "CREDIT_CARD_BILL",
                    extractedText = "irrelevant",
                    extractionMetadata = mapOf("profile_id" to "pdf-custom-v1"),
                ),
            )

        assertThat(result.status).isEqualTo(FingerprintClassification.KNOWN_PROFILE)
        assertThat(result.profileId).isEqualTo("pdf-custom-v1")
        assertThat(result.reason).isEqualTo("metadata_profile_id_match")
    }

    @Test
    fun `returns known profile from nested fingerprint metadata`() {
        val result =
            classifier.classify(
                FingerprintClassificationInput(
                    fileType = FingerprintFileType.PDF,
                    documentType = "CREDIT_CARD_BILL",
                    extractedText = "irrelevant",
                    extractionMetadata =
                        mapOf(
                            "fingerprint" to
                                mapOf(
                                    "status" to "known_profile",
                                    "profile_id" to "pdf-from-fingerprint-v1",
                                ),
                        ),
                ),
            )

        assertThat(result.status).isEqualTo(FingerprintClassification.KNOWN_PROFILE)
        assertThat(result.profileId).isEqualTo("pdf-from-fingerprint-v1")
        assertThat(result.reason).isEqualTo("metadata_profile_id_match")
    }

    @Test
    fun `returns known profile from text signature`() {
        val result =
            classifier.classify(
                FingerprintClassificationInput(
                    fileType = FingerprintFileType.PDF,
                    documentType = "CREDIT_CARD_BILL",
                    extractedText = "Nubank\nResumo da Fatura\nTotal a Pagar R$ 123,45",
                    extractionMetadata = emptyMap(),
                ),
            )

        assertThat(result.status).isEqualTo(FingerprintClassification.KNOWN_PROFILE)
        assertThat(result.profileId).isEqualTo("pdf-nubank-credit-card-bill-v1")
        assertThat(result.reason).isEqualTo("text_signature_match")
    }

    @Test
    fun `returns unknown profile when no signature matches`() {
        val result =
            classifier.classify(
                FingerprintClassificationInput(
                    fileType = FingerprintFileType.PDF,
                    documentType = "CREDIT_CARD_BILL",
                    extractedText = "Random merchant list without identifiable template",
                    extractionMetadata = emptyMap(),
                ),
            )

        assertThat(result.status).isEqualTo(FingerprintClassification.UNKNOWN_PROFILE)
        assertThat(result.profileId).isNull()
        assertThat(result.reason).isEqualTo("no_known_pdf_signature_match")
    }
}
