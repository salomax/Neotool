package io.github.salomax.neotool.banking.domain.import.excel.reader

import io.github.salomax.neotool.banking.domain.import.CanonicalReaderOutput

data class DeterministicExcelReaderInput(
    val profileId: String,
    val documentType: String?,
    val extractedText: String,
    val extractionMetadata: Map<String, Any?>,
)

interface DeterministicExcelReader {
    val readerId: String
    val supportedProfileIds: Set<String>

    fun parse(input: DeterministicExcelReaderInput): CanonicalReaderOutput
}
