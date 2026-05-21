package io.github.salomax.neotool.banking.service.import.excel

import io.github.salomax.neotool.banking.domain.import.CanonicalReaderOutput
import jakarta.inject.Singleton

@Singleton
open class DeterministicExcelReaderRouter(
    private val registry: DeterministicExcelReaderRegistry,
) {
    open fun parse(
        profileId: String,
        documentType: String?,
        extractedText: String,
        extractionMetadata: Map<String, Any?>,
    ): CanonicalReaderOutput? {
        val reader = registry.findByProfile(profileId) ?: return null
        return reader.parse(
            DeterministicExcelReaderInput(
                profileId = profileId,
                documentType = documentType,
                extractedText = extractedText,
                extractionMetadata = extractionMetadata,
            ),
        )
    }
}
