package io.github.salomax.neotool.banking.repo.investment

import io.github.salomax.neotool.banking.domain.investment.ComeCotasCheckpointJob
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

interface ComeCotasCheckpointJobRepository {
    fun save(job: ComeCotasCheckpointJob): ComeCotasCheckpointJob

    fun saveIfAbsent(job: ComeCotasCheckpointJob): Boolean

    fun update(job: ComeCotasCheckpointJob): ComeCotasCheckpointJob

    fun findById(id: UUID): ComeCotasCheckpointJob?

    fun tryClaimJob(
        jobId: UUID,
        nodeId: String,
    ): ComeCotasCheckpointJob?

    fun releaseZombieJobs(staleThreshold: Instant): Int

    fun findOldestPending(
        limit: Int,
        readyAt: Instant = Instant.now(),
    ): List<ComeCotasCheckpointJob>

    fun findByHoldingAndCheckpointDate(
        holdingId: UUID,
        checkpointDate: LocalDate,
    ): ComeCotasCheckpointJob?

    fun findActiveByAccount(accountId: UUID): ComeCotasCheckpointJob?

    fun findLatestByAccount(accountId: UUID): ComeCotasCheckpointJob?
}
