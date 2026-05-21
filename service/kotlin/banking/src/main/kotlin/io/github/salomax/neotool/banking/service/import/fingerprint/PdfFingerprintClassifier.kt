package io.github.salomax.neotool.banking.service.import.fingerprint

import jakarta.inject.Singleton
import java.util.Locale

@Singleton
open class PdfFingerprintClassifier : FingerprintClassifier {
    override val supportedFileTypes: Set<FingerprintFileType> = setOf(FingerprintFileType.PDF)

    private data class PdfSignature(
        val profileId: String,
        val requiredTokens: List<String>,
    )

    private val signatures =
        listOf(
            PdfSignature(
                profileId = "pdf-nubank-credit-card-bill-v1",
                requiredTokens = listOf("nubank", "fatura"),
            ),
            PdfSignature(
                profileId = "pdf-itau-credit-card-bill-v1",
                requiredTokens = listOf("itau", "vencimento", "fatura"),
            ),
            PdfSignature(
                profileId = "pdf-bradesco-credit-card-bill-v1",
                requiredTokens = listOf("bradesco", "fatura", "cartao"),
            ),
            PdfSignature(
                profileId = "pdf-santander-credit-card-bill-v1",
                requiredTokens = listOf("santander", "fatura", "vencimento"),
            ),
        )

    override fun classify(input: FingerprintClassificationInput): FingerprintClassification {
        require(input.fileType == FingerprintFileType.PDF) {
            "PdfFingerprintClassifier supports only PDF file type"
        }

        val metadataProfile =
            readMetadataProfileId(input.extractionMetadata)
                ?: readNestedFingerprintProfileId(input.extractionMetadata)
        if (metadataProfile != null) {
            return FingerprintClassification.known(
                profileId = metadataProfile,
                reason = "metadata_profile_id_match",
            )
        }

        val normalizedText = normalize(input.extractedText)
        val signature =
            signatures.firstOrNull { candidate ->
                candidate.requiredTokens.all { token -> normalizedText.contains(token) }
            }
        if (signature != null) {
            return FingerprintClassification.known(
                profileId = signature.profileId,
                reason = "text_signature_match",
            )
        }

        return FingerprintClassification.unknown("no_known_pdf_signature_match")
    }

    private fun readMetadataProfileId(metadata: Map<String, Any?>): String? {
        val keys =
            listOf(
                "profile_id",
                "known_profile_id",
                "fingerprint_profile",
                "fingerprint_id",
            )
        return keys
            .firstNotNullOfOrNull { key ->
                metadata[key]?.toString()?.trim()?.takeIf { it.isNotEmpty() }
            }
    }

    private fun readNestedFingerprintProfileId(metadata: Map<String, Any?>): String? {
        val fingerprint = metadata["fingerprint"] as? Map<*, *> ?: return null
        val status = fingerprint["status"]?.toString()?.trim()?.lowercase(Locale.ROOT)
        val profileId = fingerprint["profile_id"]?.toString()?.trim()
        return if (status == FingerprintClassification.KNOWN_PROFILE && !profileId.isNullOrBlank()) {
            profileId
        } else {
            null
        }
    }

    private fun normalize(text: String?): String =
        text
            .orEmpty()
            .lowercase(Locale.ROOT)
            .replace("á", "a")
            .replace("à", "a")
            .replace("ã", "a")
            .replace("â", "a")
            .replace("é", "e")
            .replace("ê", "e")
            .replace("í", "i")
            .replace("ó", "o")
            .replace("ô", "o")
            .replace("õ", "o")
            .replace("ú", "u")
            .replace("ç", "c")
}
