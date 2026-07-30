package io.github.salomax.neotool.banking.repo.investment

import io.github.salomax.neotool.banking.domain.investment.TaxRecalculationJob
import java.time.Instant
import java.util.UUID

interface TaxRecalculationJobRepository {
    fun save(job: TaxRecalculationJob): TaxRecalculationJob

    fun update(job: TaxRecalculationJob): TaxRecalculationJob

    fun findById(id: UUID): TaxRecalculationJob?

    fun tryClaimJob(
        jobId: UUID,
        nodeId: String,
    ): TaxRecalculationJob?

    fun releaseZombieJobs(staleThreshold: Instant): Int

    fun findOldestPending(limit: Int): List<TaxRecalculationJob>

    fun findPendingByHolding(holdingId: UUID): TaxRecalculationJob?

    fun findProcessingByHolding(holdingId: UUID): TaxRecalculationJob?

    fun findActiveByHolding(holdingId: UUID): TaxRecalculationJob?

    fun findLatestByHolding(holdingId: UUID): TaxRecalculationJob?
}
