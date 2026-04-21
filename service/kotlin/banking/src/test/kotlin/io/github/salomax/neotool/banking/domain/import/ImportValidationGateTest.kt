package io.github.salomax.neotool.banking.domain.import

import io.github.salomax.neotool.banking.domain.import.CanonicalProcessorType
import io.github.salomax.neotool.banking.model.dto.imports.ImportValidationCounters
import io.github.salomax.neotool.banking.model.dto.imports.ImportValidationQuality
import io.github.salomax.neotool.banking.service.import.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("ImportValidationGate")
class ImportValidationGateTest {
    private val gate = ImportValidationGate()

    @Test
    fun `llm warnings route to review required`() {
        val decision =
            gate.evaluateBeforeCommit(
                quality =
                    ImportValidationQuality(
                        processorType = CanonicalProcessorType.LLM,
                        warnings = listOf("schema_warning"),
                    ),
                totalRows = 5,
                invalidRowCount = 0,
            )

        assertThat(decision).isNotNull
        assertThat(decision!!.status).isEqualTo(ImportJobStatus.REVIEW_REQUIRED)
        assertThat(decision.reason).isEqualTo("llm_warnings_present")
    }

    @Test
    fun `llm invalid rows routes to failed`() {
        val decision =
            gate.evaluateBeforeCommit(
                quality =
                    ImportValidationQuality(
                        processorType = CanonicalProcessorType.LLM,
                        warnings = emptyList(),
                    ),
                totalRows = 3,
                invalidRowCount = 1,
            )

        assertThat(decision).isNotNull
        assertThat(decision!!.status).isEqualTo(ImportJobStatus.FAILED)
        assertThat(decision.reason).isEqualTo("llm_invalid_rows")
    }

    @Test
    fun `deterministic reader has no pre-commit block`() {
        val decision =
            gate.evaluateBeforeCommit(
                quality =
                    ImportValidationQuality(
                        processorType = CanonicalProcessorType.DETERMINISTIC,
                        warnings = emptyList(),
                    ),
                totalRows = 2,
                invalidRowCount = 0,
            )

        assertThat(decision).isNull()
    }

    @Test
    fun `final status follows duplicate and error counters`() {
        assertThat(
            gate.decideFinalStatus(
                ImportValidationCounters(
                    importedCount = 1,
                    skippedCount = 0,
                    duplicateReviewCount = 2,
                    errorCount = 0,
                ),
            ),
        ).isEqualTo(ImportJobStatus.REVIEW_REQUIRED)

        assertThat(
            gate.decideFinalStatus(
                ImportValidationCounters(
                    importedCount = 0,
                    skippedCount = 0,
                    duplicateReviewCount = 0,
                    errorCount = 2,
                ),
            ),
        ).isEqualTo(ImportJobStatus.FAILED)

        assertThat(
            gate.decideFinalStatus(
                ImportValidationCounters(
                    importedCount = 1,
                    skippedCount = 1,
                    duplicateReviewCount = 0,
                    errorCount = 0,
                ),
            ),
        ).isEqualTo(ImportJobStatus.COMPLETED)
    }
}
