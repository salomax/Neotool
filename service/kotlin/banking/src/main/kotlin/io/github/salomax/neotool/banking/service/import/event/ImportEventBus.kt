package io.github.salomax.neotool.banking.service.import.event

import io.github.salomax.neotool.banking.model.dto.imports.ImportJobStatusPayload
import jakarta.inject.Singleton
import org.slf4j.LoggerFactory
import reactor.core.publisher.Flux
import reactor.core.publisher.Sinks

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
