package io.github.salomax.neotool.banking.domain.import.fingerprint

import jakarta.inject.Singleton
import java.text.Normalizer

@Singleton
open class ExcelFingerprintClassifier : FingerprintClassifier {
    override val supportedFileTypes: Set<FingerprintFileType> =
        setOf(FingerprintFileType.XLSX, FingerprintFileType.XLS)

    override fun classify(input: FingerprintClassificationInput): FingerprintClassification {
        require(input.fileType in supportedFileTypes) {
            "ExcelFingerprintClassifier supports only XLSX/XLS file types"
        }
        if (isKnownBankStatementProfile(input)) {
            return FingerprintClassification.known(
                profileId = PROFILE_BANK_STATEMENT_TABULAR_V1,
                reason = "sheet_header_and_document_type_match",
            )
        }
        return FingerprintClassification.unknown("no_known_excel_signature_match")
    }

    private fun isKnownBankStatementProfile(input: FingerprintClassificationInput): Boolean {
        if (!isBankStatementDocumentType(input.documentType)) return false

        val metadataProfileId = input.extractionMetadata["profile_id"]?.toString()
        if (metadataProfileId == PROFILE_BANK_STATEMENT_TABULAR_V1) {
            return true
        }

        val sheetNames = extractSheetNames(input.extractionMetadata).map(::normalize)
        val hasKnownSheetName = sheetNames.any { name -> SHEET_NAME_HINTS.any { name.contains(it) } }

        val text = normalize(input.extractedText.orEmpty())
        val hasDateHint = text.contains(" data ") || text.contains(" date ")
        val hasDescriptionHint =
            text.contains(" descricao ") ||
                text.contains(" historico ") ||
                text.contains(" lancamento ")
        val hasAmountHint =
            text.contains(" valor ") ||
                text.contains(" amount ") ||
                text.contains(" credito ") ||
                text.contains(" debito ")

        return hasKnownSheetName && hasDateHint && hasDescriptionHint && hasAmountHint
    }

    private fun extractSheetNames(metadata: Map<String, Any?>): List<String> {
        val raw = metadata["sheet_names"] ?: return emptyList()
        if (raw !is List<*>) return emptyList()
        return raw.mapNotNull { it?.toString() }
    }

    private fun isBankStatementDocumentType(documentType: String?): Boolean =
        documentType
            ?.trim()
            ?.uppercase()
            ?.let { it == "BANK_STATEMENT" }
            ?: false

    private fun normalize(value: String): String =
        " " +
            Normalizer
                .normalize(value, Normalizer.Form.NFD)
                .replace(ACCENT_REGEX, "")
                .lowercase()
                .replace(WHITESPACE_REGEX, " ")
                .trim() +
            " "

    companion object {
        const val PROFILE_BANK_STATEMENT_TABULAR_V1 = "excel-bank-statement-tabular-v1"
        private val SHEET_NAME_HINTS =
            setOf("extrato", "lancamentos", "lancamento", "movimentacoes", "movimentacao", "resumo")
        private val ACCENT_REGEX = Regex("\\p{M}+")
        private val WHITESPACE_REGEX = Regex("\\s+")
    }
}
