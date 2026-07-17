package io.github.salomax.neotool.banking.repo.investment

import io.github.salomax.neotool.banking.domain.investment.ComeCotasCheckpointJob
import io.github.salomax.neotool.banking.domain.investment.ComeCotasCheckpointJobStatus
import io.github.salomax.neotool.banking.model.investment.ComeCotasCheckpointJobMapper
import io.github.salomax.neotool.common.error.CommonErrorCode
import io.github.salomax.neotool.common.error.NotFoundException
import io.micronaut.data.model.Pageable
import io.micronaut.transaction.annotation.Transactional
import jakarta.inject.Singleton
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@Singleton
open class DefaultComeCotasCheckpointJobRepository(
    private val entityRepository: ComeCotasCheckpointJobEntityRepository,
) : ComeCotasCheckpointJobRepository {
    @Transactional
    override fun save(job: ComeCotasCheckpointJob): ComeCotasCheckpointJob =
        entityRepository.save(ComeCotasCheckpointJobMapper.toEntity(job))
            .let(ComeCotasCheckpointJobMapper::toDomain)

    @Transactional
    override fun saveIfAbsent(job: ComeCotasCheckpointJob): Boolean =
        entityRepository.insertIfAbsent(
            holdingId = job.holdingId,
            financialAccountId = job.financialAccountId,
            accountId = job.accountId,
            checkpointDate = job.checkpointDate,
            status = job.status.name,
        ) > 0

    @Transactional
    override fun update(job: ComeCotasCheckpointJob): ComeCotasCheckpointJob {
        val id = requireNotNull(job.id) { "Come-cotas checkpoint job id is required for update" }
        val entity =
            entityRepository.findByKey(id)
                ?: throw NotFoundException(
                    CommonErrorCode.RESOURCE_NOT_FOUND,
                    field = "comeCotasCheckpointJobId",
                    parameters = mapOf("resourceType" to "ComeCotasCheckpointJob", "resourceId" to id.toString()),
                )
        ComeCotasCheckpointJobMapper.updateEntity(entity, job)
        return entityRepository.update(entity).let(ComeCotasCheckpointJobMapper::toDomain)
    }

    override fun findById(id: UUID): ComeCotasCheckpointJob? =
        entityRepository.findByKey(id)?.let(ComeCotasCheckpointJobMapper::toDomain)

    @Transactional
    override fun tryClaimJob(
        jobId: UUID,
        nodeId: String,
    ): ComeCotasCheckpointJob? =
        entityRepository.tryClaimJob(jobId, nodeId)?.let(ComeCotasCheckpointJobMapper::toDomain)

    @Transactional
    override fun releaseZombieJobs(staleThreshold: Instant): Int = entityRepository.releaseZombieJobs(staleThreshold)

    override fun findOldestPending(
        limit: Int,
        readyAt: Instant,
    ): List<ComeCotasCheckpointJob> =
        entityRepository.findByStatus(
            ComeCotasCheckpointJobStatus.PENDING,
            readyAt,
            Pageable.from(0, limit.coerceAtLeast(1)),
        )
            .map(ComeCotasCheckpointJobMapper::toDomain)

    override fun findByHoldingAndCheckpointDate(
        holdingId: UUID,
        checkpointDate: LocalDate,
    ): ComeCotasCheckpointJob? =
        entityRepository.findByHoldingAndCheckpointDate(
            holdingId,
            checkpointDate,
        )?.let(ComeCotasCheckpointJobMapper::toDomain)

    override fun findActiveByAccount(accountId: UUID): ComeCotasCheckpointJob? =
        entityRepository.findActiveByAccount(accountId).firstOrNull()?.let(ComeCotasCheckpointJobMapper::toDomain)

    override fun findLatestByAccount(accountId: UUID): ComeCotasCheckpointJob? =
        entityRepository.findLatestByAccount(accountId).firstOrNull()?.let(ComeCotasCheckpointJobMapper::toDomain)
}
