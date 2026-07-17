package io.github.salomax.neotool.banking.job.investment

import io.github.salomax.neotool.banking.client.investment.messaging.ComeCotasCheckpointRequestedEvent
import io.github.salomax.neotool.banking.client.investment.messaging.ComeCotasCheckpointRequestedProducer
import io.github.salomax.neotool.banking.repo.investment.ComeCotasCheckpointJobRepository
import io.micronaut.context.annotation.Requires
import io.micronaut.context.annotation.Value
import io.micronaut.scheduling.annotation.Scheduled
import jakarta.inject.Singleton
import mu.KotlinLogging
import java.time.Instant

@Singleton
@Requires(property = "kafka.enabled", value = "true")
@Requires(property = "banking.come-cotas.pending-dispatcher.enabled", notEquals = "false", defaultValue = "true")
open class PendingComeCotasCheckpointDispatchJob(
    private val jobRepository: ComeCotasCheckpointJobRepository,
    private val producer: ComeCotasCheckpointRequestedProducer,
    @param:Value("\${banking.come-cotas.pending-dispatcher.batch-size:100}")
    private val batchSize: Int,
) {
    private val logger = KotlinLogging.logger {}

    @Scheduled(fixedDelay = "\${banking.come-cotas.pending-dispatcher.fixed-delay:PT1M}")
    fun dispatch() {
        val jobs = jobRepository.findOldestPending(batchSize, Instant.now())
        if (jobs.isEmpty()) return
        val requestedAt = Instant.now()
        jobs.forEach { job ->
            producer.publish(
                ComeCotasCheckpointRequestedEvent(
                    jobId = requireNotNull(job.id),
                    accountId = job.accountId,
                    financialAccountId = job.financialAccountId,
                    holdingId = job.holdingId,
                    checkpointDate = job.checkpointDate,
                    requestedAt = requestedAt,
                ),
            )
        }
        logger.info { "PendingComeCotasCheckpointDispatchJob published ${jobs.size} job(s)" }
    }
}
