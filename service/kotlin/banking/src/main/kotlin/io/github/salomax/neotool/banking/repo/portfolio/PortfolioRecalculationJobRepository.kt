package io.github.salomax.neotool.banking.repo.portfolio

import io.github.salomax.neotool.banking.domain.portfolio.PortfolioRecalculationJob
import java.time.Instant
import java.util.UUID

interface PortfolioRecalculationJobRepository {
    fun save(job: PortfolioRecalculationJob): PortfolioRecalculationJob

    fun update(job: PortfolioRecalculationJob): PortfolioRecalculationJob

    fun findById(id: UUID): PortfolioRecalculationJob?

    /**
     * Atomically transitions a job from PENDING → PROCESSING and records [nodeId] as the owner.
     * Returns the claimed job, or null if another node already claimed it (status was not PENDING).
     * This is the single distributed lock: only one pod can win the UPDATE ... WHERE status='PENDING'.
     */
    fun tryClaimJob(jobId: UUID, nodeId: String): PortfolioRecalculationJob?

    /**
     * Resets zombie jobs (PROCESSING with [lastHeartbeatAt] older than [staleThreshold]) back to PENDING
     * so the Kafka consumer can reprocess them after a pod crash or eviction.
     * Returns the number of jobs released.
     */
    fun releaseZombieJobs(staleThreshold: Instant): Int

    fun findPendingByAccount(accountId: UUID): PortfolioRecalculationJob?

    fun findActiveByAccount(accountId: UUID): PortfolioRecalculationJob?

    fun findLatestByAccount(accountId: UUID): PortfolioRecalculationJob?
}
