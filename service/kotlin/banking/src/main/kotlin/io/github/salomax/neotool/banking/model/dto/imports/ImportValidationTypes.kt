package io.github.salomax.neotool.banking.model.dto.imports

import io.github.salomax.neotool.banking.domain.import.CanonicalProcessorType
import io.github.salomax.neotool.banking.domain.import.ImportJobStatus

data class ImportValidationQuality(
    val processorType: CanonicalProcessorType?,
    val warnings: List<String> = emptyList(),
    /** LLM parse_metadata.confidence when present (0..1). */
    val confidence: Double? = null,
)

data class ImportValidationPreDecision(
    val status: ImportJobStatus,
    val reason: String,
    val details: Map<String, Any?>,
)

data class ImportValidationCounters(
    val importedCount: Int,
    val skippedCount: Int,
    val duplicateReviewCount: Int,
    val errorCount: Int,
)
