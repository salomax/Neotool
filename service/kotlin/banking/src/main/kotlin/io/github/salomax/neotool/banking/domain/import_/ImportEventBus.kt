package io.github.salomax.neotool.banking.domain.import_

import io.github.salomax.neotool.banking.domain.import.ImportJob
import io.github.salomax.neotool.banking.domain.import.ImportJobStatus
import jakarta.inject.Singleton
import org.slf4j.LoggerFactory
import reactor.core.publisher.Flux
import reactor.core.publisher.Sinks
import java.util.UUID

/**
 * Payload for import job status updates (in-process SSE / event bridge).
 */
data class ImportJobStatusPayload(
    val jobId: UUID,
    val accountId: UUID,
    val status: ImportJobStatus,
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
                importedCount = job.importedCount,
                skippedCount = job.skippedCount,
                duplicateReviewCount = job.duplicateReviewCount,
                errorCount = job.errorCount,
            )
    }
}

/**
 * In-process event bus for import job status updates.
 * Multicast sink: multiple subscribers each receive all emitted events.
 * Buffer 256 with no auto-cancel when there are no subscribers; emit when no subscribers is a no-op.
 */
@Singleton
class ImportEventBus {
    private val log = LoggerFactory.getLogger(javaClass)
    private val sink: Sinks.Many<ImportJobStatusPayload> =
        Sinks.many().multicast().onBackpressureBuffer(256, false)

    fun emit(payload: ImportJobStatusPayload) {
        val result = sink.tryEmitNext(payload)
        if (result.isFailure) {
            log.warn("ImportEventBus emit failed for jobId={}: {}", payload.jobId, result)
        }
    }

    fun asFlux(): Flux<ImportJobStatusPayload> = sink.asFlux()
}
