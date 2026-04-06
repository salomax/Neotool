package io.github.salomax.neotool.banking.job.investment

import io.github.salomax.neotool.banking.repo.portfolio.PortfolioRecalculationJobRepository
import io.micronaut.context.annotation.Requires
import io.micronaut.context.annotation.Value
import io.micronaut.scheduling.annotation.Scheduled
import jakarta.inject.Singleton
import mu.KotlinLogging
import java.time.Duration
import java.time.Instant

/**
 * Periodically releases zombie portfolio recalculation jobs back to PENDING.
 *
 * ## What is a zombie job
 * A job in PROCESSING whose [last_heartbeat_at] has not been updated for longer than
 * [zombieThresholdMinutes]. This happens when a pod crashes, is evicted by k8s, or hits OOM
 * mid-loop — the heartbeat stops but the job stays PROCESSING forever, blocking re-delivery.
 *
 * ## What this job does
 * Calls [PortfolioRecalculationJobRepository.releaseZombieJobs], which resets status → PENDING
 * and clears processing_node_id. The Kafka consumer will redeliver the message and
 * [PortfolioRecalculationService.tryClaimJob] will re-claim it atomically.
 *
 * ## Scheduling
 * Default: every 5 minutes. Override via `banking.recalculation.zombie-recovery.fixed-delay`.
 * Zombie threshold default: 10 minutes. Override via `banking.recalculation.zombie-recovery.threshold-minutes`.
 *
 * Disable per-environment:
 *   banking.recalculation.zombie-recovery.enabled: false
 */
@Singleton
@Requires(property = "banking.recalculation.zombie-recovery.enabled", notEquals = "false", defaultValue = "true")
open class ZombieJobRecoveryJob(
    private val portfolioRecalculationJobRepository: PortfolioRecalculationJobRepository,
    @Value("\${banking.recalculation.zombie-recovery.threshold-minutes:10}")
    private val zombieThresholdMinutes: Long,
) {
    private val logger = KotlinLogging.logger {}

    @Scheduled(fixedDelay = "\${banking.recalculation.zombie-recovery.fixed-delay:PT5M}")
    fun recover() {
        val staleThreshold = Instant.now().minus(Duration.ofMinutes(zombieThresholdMinutes))
        val released = portfolioRecalculationJobRepository.releaseZombieJobs(staleThreshold)
        if (released > 0) {
            logger.warn {
                "ZombieJobRecovery: released $released zombie recalculation job(s) " +
                    "(heartbeat stale for >${zombieThresholdMinutes}m) — they will be reprocessed via Kafka redelivery"
            }
        } else {
            logger.debug { "ZombieJobRecovery: no zombie jobs found" }
        }
    }
}
