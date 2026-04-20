package io.github.salomax.neotool.banking.service.investment.event

import io.github.salomax.neotool.banking.model.dto.investment.PortfolioRecalculationStatusPayload
import jakarta.inject.Singleton
import org.slf4j.LoggerFactory
import reactor.core.publisher.Flux
import reactor.core.publisher.Sinks

@Singleton
class PortfolioRecalculationEventBus {
    private val log = LoggerFactory.getLogger(javaClass)
    private val sink: Sinks.Many<PortfolioRecalculationStatusPayload> =
        Sinks.many().multicast().onBackpressureBuffer(256, false)

    fun emit(payload: PortfolioRecalculationStatusPayload) {
        val result = sink.tryEmitNext(payload)
        if (result.isFailure) {
            log.warn("PortfolioRecalculationEventBus emit failed for jobId={}: {}", payload.jobId, result)
        }
    }

    fun asFlux(): Flux<PortfolioRecalculationStatusPayload> = sink.asFlux()
}
