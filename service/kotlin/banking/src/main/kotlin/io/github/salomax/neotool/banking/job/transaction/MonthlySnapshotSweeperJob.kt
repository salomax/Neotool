package io.github.salomax.neotool.banking.job.transaction

import io.github.salomax.neotool.banking.domain.transaction.MonthlyBalanceSnapshotRepository
import io.github.salomax.neotool.banking.service.transaction.SnapshotComputationService
import io.micronaut.context.annotation.Requires
import io.micronaut.scheduling.annotation.Scheduled
import jakarta.inject.Singleton
import mu.KotlinLogging

/**
 * Periodic job that heals dirty monthly balance snapshots.
 *
 * ## Why dirty snapshots exist
 * When [SnapshotComputationService.recomputeChain] throws partway through a chain recompute
 * (e.g., a transient DB error), it marks the remaining months as dirty so the read path can
 * serve the last-known values while signalling that recomputation is pending.
 *
 * ## What this job does
 * 1. Find all financial accounts with at least one dirty snapshot.
 * 2. For each, re-run the full chain from the earliest dirty month to the current month.
 *
 * ## Scheduling
 * Default: every 30 minutes. Override via `banking.snapshot.sweeper.fixed-delay` (ISO-8601 duration).
 *
 * Disable in environments that do not need background healing:
 *   banking.snapshot.sweeper.enabled: false
 */
@Singleton
@Requires(property = "banking.snapshot.sweeper.enabled", value = "true", defaultValue = "true")
open class MonthlySnapshotSweeperJob(
    private val snapshotRepository: MonthlyBalanceSnapshotRepository,
    private val snapshotComputationService: SnapshotComputationService,
) {
    private val logger = KotlinLogging.logger {}

    @Scheduled(fixedDelay = "\${banking.snapshot.sweeper.fixed-delay:PT30M}")
    fun sweep() {
        val dirtyAccountIds = snapshotRepository.findFinancialAccountIdsWithDirtySnapshots()
        if (dirtyAccountIds.isEmpty()) {
            logger.debug { "MonthlySnapshotSweeper: no dirty snapshots — nothing to do" }
            return
        }

        logger.info {
            "MonthlySnapshotSweeper: healing ${dirtyAccountIds.size} financial account(s) with dirty snapshots"
        }
        var healed = 0
        var failed = 0

        dirtyAccountIds.forEach { financialAccountId ->
            val dirtySnapshots = snapshotRepository.findAllDirty(financialAccountId)
            if (dirtySnapshots.isEmpty()) return@forEach

            val earliest = dirtySnapshots.first() // ordered by (year ASC, month ASC)
            runCatching {
                snapshotComputationService.recomputeChain(
                    financialAccountId = financialAccountId,
                    accountId = earliest.accountId,
                    fromYear = earliest.year,
                    fromMonth = earliest.month,
                )
                healed++
                logger.debug {
                    "MonthlySnapshotSweeper: healed $financialAccountId from ${earliest.year}-${earliest.month}"
                }
            }.onFailure { e ->
                failed++
                logger.warn {
                    "MonthlySnapshotSweeper: failed to heal $financialAccountId: ${e.message}"
                }
            }
        }

        logger.info { "MonthlySnapshotSweeper: done — $healed healed, $failed failed" }
    }
}
