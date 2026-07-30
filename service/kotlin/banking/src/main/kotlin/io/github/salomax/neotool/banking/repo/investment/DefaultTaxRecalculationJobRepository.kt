package io.github.salomax.neotool.banking.repo.investment

import io.github.salomax.neotool.banking.domain.investment.TaxRecalculationJob
import io.github.salomax.neotool.banking.domain.investment.TaxRecalculationStatus
import io.github.salomax.neotool.banking.model.investment.TaxRecalculationJobMapper
import io.github.salomax.neotool.common.error.CommonErrorCode
import io.github.salomax.neotool.common.error.NotFoundException
import io.micronaut.data.model.Pageable
import io.micronaut.transaction.annotation.Transactional
import jakarta.inject.Singleton
import java.time.Instant
import java.util.UUID

@Singleton
open class DefaultTaxRecalculationJobRepository(
    private val entityRepository: TaxRecalculationJobEntityRepository,
) : TaxRecalculationJobRepository {
    @Transactional
    override fun save(job: TaxRecalculationJob): TaxRecalculationJob =
        entityRepository.save(TaxRecalculationJobMapper.toEntity(job))
            .let(TaxRecalculationJobMapper::toDomain)

    @Transactional
    override fun update(job: TaxRecalculationJob): TaxRecalculationJob {
        val id = requireNotNull(job.id) { "Tax recalculation job id is required for update" }
        val entity =
            entityRepository.findByKey(id)
                ?: throw NotFoundException(
                    CommonErrorCode.RESOURCE_NOT_FOUND,
                    field = "taxRecalculationJobId",
                    parameters = mapOf("resourceType" to "TaxRecalculationJob", "resourceId" to id.toString()),
                )
        TaxRecalculationJobMapper.updateEntity(entity, job)
        return entityRepository.update(entity)
            .let(TaxRecalculationJobMapper::toDomain)
    }

    override fun findById(id: UUID): TaxRecalculationJob? =
        entityRepository.findByKey(id)
            ?.let(TaxRecalculationJobMapper::toDomain)

    @Transactional
    override fun tryClaimJob(
        jobId: UUID,
        nodeId: String,
    ): TaxRecalculationJob? =
        entityRepository.tryClaimJob(jobId, nodeId)
            ?.let(TaxRecalculationJobMapper::toDomain)

    @Transactional
    override fun releaseZombieJobs(staleThreshold: Instant): Int = entityRepository.releaseZombieJobs(staleThreshold)

    override fun findOldestPending(limit: Int): List<TaxRecalculationJob> {
        val safeLimit = limit.coerceAtLeast(1)
        return entityRepository.findByStatus(
            status = TaxRecalculationStatus.PENDING,
            pageable = Pageable.from(0, safeLimit),
        ).map(TaxRecalculationJobMapper::toDomain)
    }

    override fun findPendingByHolding(holdingId: UUID): TaxRecalculationJob? =
        entityRepository.findByHoldingAndStatuses(
            holdingId = holdingId,
            statuses = listOf(TaxRecalculationStatus.PENDING),
        ).firstOrNull()
            ?.let(TaxRecalculationJobMapper::toDomain)

    override fun findProcessingByHolding(holdingId: UUID): TaxRecalculationJob? =
        entityRepository.findByHoldingAndStatuses(
            holdingId = holdingId,
            statuses = listOf(TaxRecalculationStatus.PROCESSING),
        ).firstOrNull()
            ?.let(TaxRecalculationJobMapper::toDomain)

    override fun findActiveByHolding(holdingId: UUID): TaxRecalculationJob? =
        entityRepository.findByHoldingAndStatuses(
            holdingId = holdingId,
            statuses = listOf(TaxRecalculationStatus.PENDING, TaxRecalculationStatus.PROCESSING),
        ).firstOrNull()
            ?.let(TaxRecalculationJobMapper::toDomain)

    override fun findLatestByHolding(holdingId: UUID): TaxRecalculationJob? =
        entityRepository.findLatestByHolding(holdingId)
            .firstOrNull()
            ?.let(TaxRecalculationJobMapper::toDomain)
}
