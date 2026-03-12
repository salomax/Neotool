package io.github.salomax.neotool.banking.domain.import

import jakarta.inject.Singleton

data class ImportValidationQuality(
    val processorType: CanonicalProcessorType?,
    val confidence: Double?,
    val warnings: List<String> = emptyList(),
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

@Singleton
open class ImportValidationGate {
    companion object {
        private const val MIN_LLM_CONFIDENCE = 0.85
    }

    fun evaluateBeforeCommit(
        quality: ImportValidationQuality,
        totalRows: Int,
        invalidRowCount: Int,
    ): ImportValidationPreDecision? {
        val processorType = quality.processorType
        if (processorType != CanonicalProcessorType.LLM) return null

        if (totalRows <= 0) {
            return ImportValidationPreDecision(
                status = ImportJobStatus.FAILED,
                reason = "llm_no_rows",
                details =
                    mapOf(
                        "gate_reason" to "llm_no_rows",
                        "processor_type" to processorType.name,
                    ),
            )
        }

        if (invalidRowCount > 0) {
            return ImportValidationPreDecision(
                status = ImportJobStatus.FAILED,
                reason = "llm_invalid_rows",
                details =
                    mapOf(
                        "gate_reason" to "llm_invalid_rows",
                        "processor_type" to processorType.name,
                        "invalid_row_count" to invalidRowCount,
                    ),
            )
        }

        val confidence = (quality.confidence ?: 1.0).coerceIn(0.0, 1.0)
        if (confidence < MIN_LLM_CONFIDENCE) {
            return ImportValidationPreDecision(
                status = ImportJobStatus.REVIEW_REQUIRED,
                reason = "llm_low_confidence",
                details =
                    mapOf(
                        "gate_reason" to "llm_low_confidence",
                        "processor_type" to processorType.name,
                        "confidence" to confidence,
                        "threshold" to MIN_LLM_CONFIDENCE,
                    ),
            )
        }

        if (quality.warnings.isNotEmpty()) {
            return ImportValidationPreDecision(
                status = ImportJobStatus.REVIEW_REQUIRED,
                reason = "llm_warnings_present",
                details =
                    mapOf(
                        "gate_reason" to "llm_warnings_present",
                        "processor_type" to processorType.name,
                        "warnings" to quality.warnings.take(20),
                    ),
            )
        }

        return null
    }

    fun decideFinalStatus(counters: ImportValidationCounters): ImportJobStatus =
        when {
            counters.duplicateReviewCount > 0 -> ImportJobStatus.REVIEW_REQUIRED
            counters.importedCount == 0 &&
                counters.skippedCount == 0 &&
                counters.errorCount > 0 ->
                ImportJobStatus.FAILED
            else -> ImportJobStatus.COMPLETED
        }
}
