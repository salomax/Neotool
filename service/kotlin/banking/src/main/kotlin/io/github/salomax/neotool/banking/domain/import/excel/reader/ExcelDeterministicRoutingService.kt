package io.github.salomax.neotool.banking.domain.import.excel.reader

import io.github.salomax.neotool.banking.domain.import.CanonicalReaderOutput
import io.github.salomax.neotool.banking.domain.import.fingerprint.ExcelFingerprintClassifier
import io.github.salomax.neotool.banking.domain.import.fingerprint.FingerprintClassificationInput
import io.github.salomax.neotool.banking.domain.import.fingerprint.FingerprintClassifierRegistry
import io.github.salomax.neotool.banking.domain.import.fingerprint.FingerprintFileType
import jakarta.inject.Singleton

@Singleton
open class ExcelDeterministicRoutingService(
    private val fingerprintClassifierRegistry: FingerprintClassifierRegistry =
        FingerprintClassifierRegistry(
            listOf(
                ExcelFingerprintClassifier(),
            ),
        ),
    private val readerRouter: DeterministicExcelReaderRouter =
        DeterministicExcelReaderRouter(
            registry =
                DeterministicExcelReaderRegistry(
                    readers =
                        listOf(
                            TabularBankStatementExcelReader(),
                        ),
                ),
        ),
) {
    open fun route(
        fileType: FingerprintFileType,
        documentType: String?,
        extractedText: String,
        extractionMetadata: Map<String, Any?>,
    ): CanonicalReaderOutput? {
        val classification =
            fingerprintClassifierRegistry.classify(
                FingerprintClassificationInput(
                    fileType = fileType,
                    documentType = documentType,
                    extractedText = extractedText,
                    extractionMetadata = extractionMetadata,
                ),
            )
        val profileId = classification.profileId ?: return null
        return readerRouter.parse(
            profileId = profileId,
            documentType = documentType,
            extractedText = extractedText,
            extractionMetadata = extractionMetadata,
        )
    }
}
