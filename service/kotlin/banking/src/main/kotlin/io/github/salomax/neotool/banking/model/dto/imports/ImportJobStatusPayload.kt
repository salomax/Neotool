package io.github.salomax.neotool.banking.model.dto.imports

import io.github.salomax.neotool.banking.domain.import.ImportJob
import io.github.salomax.neotool.banking.domain.import.ImportJobStatus
import io.micronaut.serde.annotation.Serdeable
import java.util.UUID

/**
 * Payload for import job status updates (in-process SSE / event bridge).
 */
@Serdeable
data class ImportJobStatusPayload(
    val jobId: UUID,
    val accountId: UUID,
    val status: ImportJobStatus,
    val errorDetails: String? = null,
    val importedCount: Int?,
    val skippedCount: Int?,
    val duplicateReviewCount: Int?,
    val errorCount: Int?,
) {
    companion object {
        fun from(job: ImportJob): ImportJobStatusPayload =
            ImportJobStatusPayload(
                jobId = job.id!!,
                accountId = job.accountId,
                status = job.status,
                errorDetails = job.errorDetails,
                importedCount = job.importedCount,
                skippedCount = job.skippedCount,
                duplicateReviewCount = job.duplicateReviewCount,
                errorCount = job.errorCount,
            )
    }
}
