package io.github.salomax.neotool.banking.job.investment

import io.github.salomax.neotool.banking.repo.investment.ComeCotasCheckpointJobRepository
import io.micronaut.context.annotation.Requires
import io.micronaut.context.annotation.Value
import io.micronaut.scheduling.annotation.Scheduled
import jakarta.inject.Singleton
import mu.KotlinLogging
import java.time.Duration
import java.time.Instant

@Singleton
@Requires(property = "banking.come-cotas.zombie-recovery.enabled", notEquals = "false", defaultValue = "true")
open class ComeCotasZombieRecoveryJob(
    private val comeCotasCheckpointJobRepository: ComeCotasCheckpointJobRepository,
    @Value("\${banking.come-cotas.zombie-recovery.threshold-minutes:10}")
    private val zombieThresholdMinutes: Long,
) {
    private val logger = KotlinLogging.logger {}

    @Scheduled(fixedDelay = "\${banking.come-cotas.zombie-recovery.fixed-delay:PT5M}")
    fun recover() {
        val staleThreshold = Instant.now().minus(Duration.ofMinutes(zombieThresholdMinutes))
        val released = comeCotasCheckpointJobRepository.releaseZombieJobs(staleThreshold)
        if (released > 0) {
            logger.warn {
                "ComeCotasZombieRecovery: released $released zombie checkpoint job(s) " +
                    "(heartbeat stale for >${zombieThresholdMinutes}m) — they will be reprocessed"
            }
        } else {
            logger.debug { "ComeCotasZombieRecovery: no zombie checkpoint jobs found" }
        }
    }
}
