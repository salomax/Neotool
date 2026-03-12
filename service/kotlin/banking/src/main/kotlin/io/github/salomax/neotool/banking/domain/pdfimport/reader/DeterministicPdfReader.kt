package io.github.salomax.neotool.banking.domain.pdfimport.reader

import io.github.salomax.neotool.banking.domain.import.CanonicalReaderOutput

data class DeterministicPdfReaderInput(
    val profileId: String,
    val documentType: String?,
    val extractedText: String,
    val extractionMetadata: Map<String, Any?>,
)

interface DeterministicPdfReader {
    val readerId: String
    val supportedProfileIds: Set<String>

    fun parse(input: DeterministicPdfReaderInput): CanonicalReaderOutput
}
