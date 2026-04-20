package io.github.salomax.neotool.banking.job.investment

import io.github.salomax.neotool.banking.client.investment.messaging.PortfolioRecalculationRequestedEvent
import io.github.salomax.neotool.banking.client.investment.messaging.PortfolioRecalculationRequestedProducer
import io.github.salomax.neotool.banking.repo.portfolio.PortfolioRecalculationJobRepository
import io.micronaut.context.annotation.Requires
import io.micronaut.context.annotation.Value
import io.micronaut.scheduling.annotation.Scheduled
import jakarta.inject.Singleton
import mu.KotlinLogging
import java.time.Instant

/**
 * Dispatches already-persisted PENDING recalculation jobs to Kafka.
 *
 * This is a resilience fallback for operational backfills and rare publish gaps:
 * jobs can exist in DB even when no recalculation-request event is currently in Kafka.
 */
@Singleton
@Requires(property = "kafka.enabled", value = "true")
@Requires(property = "banking.recalculation.pending-dispatcher.enabled", notEquals = "false", defaultValue = "true")
open class PendingRecalculationDispatchJob(
    private val portfolioRecalculationJobRepository: PortfolioRecalculationJobRepository,
    private val producer: PortfolioRecalculationRequestedProducer,
    @param:Value("\${banking.recalculation.pending-dispatcher.batch-size:100}")
    private val batchSize: Int,
) {
    private val logger = KotlinLogging.logger {}

    @Scheduled(fixedDelay = "\${banking.recalculation.pending-dispatcher.fixed-delay:PT1M}")
    fun dispatch() {
        val jobs = portfolioRecalculationJobRepository.findOldestPending(batchSize)
        if (jobs.isEmpty()) {
            logger.debug { "PendingRecalculationDispatch: no pending jobs found" }
            return
        }

        val requestedAt = Instant.now()
        jobs.forEach { job ->
            val jobId = requireNotNull(job.id) { "recalculation job id is required for dispatch" }
            producer.publish(
                PortfolioRecalculationRequestedEvent(
                    jobId = jobId,
                    accountId = job.accountId,
                    financialAccountId = job.financialAccountId,
                    recalculateFrom = job.recalculateFrom,
                    triggeredBy = job.triggeredBy,
                    requestedAt = requestedAt,
                ),
            )
        }
        logger.info { "PendingRecalculationDispatch: published ${jobs.size} pending job(s) to Kafka" }
    }
}
