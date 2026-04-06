package io.github.salomax.neotool.banking.service.investment

import io.github.salomax.neotool.banking.domain.portfolio.PortfolioRecalculationJob
import io.github.salomax.neotool.banking.domain.portfolio.PortfolioRecalculationStatus
import io.micronaut.serde.annotation.Serdeable
import jakarta.inject.Singleton
import org.slf4j.LoggerFactory
import reactor.core.publisher.Flux
import reactor.core.publisher.Sinks
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@Serdeable
data class PortfolioRecalculationStatusPayload(
    val jobId: UUID,
    val accountId: UUID,
    val financialAccountId: UUID?,
    val status: PortfolioRecalculationStatus,
    val recalculateFrom: LocalDate,
    val daysProcessed: Int?,
    /** Machine-readable code for client i18n (see docs/08-architecture/error-handling-system.md). */
    val errorCode: String? = null,
    val errorDetails: String? = null,
    val startedAt: Instant? = null,
    val completedAt: Instant? = null,
) {
    companion object {
        fun from(job: PortfolioRecalculationJob): PortfolioRecalculationStatusPayload =
            PortfolioRecalculationStatusPayload(
                jobId = requireNotNull(job.id),
                accountId = job.accountId,
                financialAccountId = job.financialAccountId,
                status = job.status,
                recalculateFrom = job.recalculateFrom,
                daysProcessed = job.daysProcessed,
                errorCode = job.errorCode,
                errorDetails = job.errorDetails,
                startedAt = job.startedAt,
                completedAt = job.completedAt,
            )
    }
}

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
