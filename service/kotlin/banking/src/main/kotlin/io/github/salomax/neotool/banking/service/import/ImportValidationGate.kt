package io.github.salomax.neotool.banking.service.import

import io.github.salomax.neotool.banking.domain.import.CanonicalProcessorType
import io.github.salomax.neotool.banking.domain.import.ImportJobStatus
import io.github.salomax.neotool.banking.model.dto.imports.ImportValidationCounters
import io.github.salomax.neotool.banking.model.dto.imports.ImportValidationPreDecision
import io.github.salomax.neotool.banking.model.dto.imports.ImportValidationQuality
import jakarta.inject.Singleton

@Singleton
open class ImportValidationGate {
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
