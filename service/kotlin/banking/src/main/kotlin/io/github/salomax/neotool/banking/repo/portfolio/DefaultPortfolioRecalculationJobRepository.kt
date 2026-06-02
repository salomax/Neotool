package io.github.salomax.neotool.banking.repo.portfolio

import io.github.salomax.neotool.banking.domain.portfolio.PortfolioRecalculationJob
import io.github.salomax.neotool.banking.domain.portfolio.PortfolioRecalculationStatus
import io.github.salomax.neotool.banking.model.portfolio.InvestmentPortfolioRecalculationJobMapper
import io.github.salomax.neotool.common.error.CommonErrorCode
import io.github.salomax.neotool.common.error.NotFoundException
import io.micronaut.data.model.Pageable
import io.micronaut.transaction.annotation.Transactional
import jakarta.inject.Singleton
import java.time.Instant
import java.util.UUID

@Singleton
open class DefaultPortfolioRecalculationJobRepository(
    private val entityRepository: PortfolioRecalculationJobEntityRepository,
) : PortfolioRecalculationJobRepository {
    @Transactional
    override fun save(job: PortfolioRecalculationJob): PortfolioRecalculationJob =
        entityRepository.save(InvestmentPortfolioRecalculationJobMapper.toEntity(job))
            .let(InvestmentPortfolioRecalculationJobMapper::toDomain)

    @Transactional
    override fun update(job: PortfolioRecalculationJob): PortfolioRecalculationJob {
        val id = requireNotNull(job.id) { "Portfolio recalculation job id is required for update" }
        val entity =
            entityRepository.findByKey(id)
                ?: throw NotFoundException(
                    CommonErrorCode.RESOURCE_NOT_FOUND,
                    field = "portfolioRecalculationJobId",
                    parameters = mapOf("resourceType" to "PortfolioRecalculationJob", "resourceId" to id.toString()),
                )
        InvestmentPortfolioRecalculationJobMapper.updateEntity(entity, job)
        return entityRepository.update(entity)
            .let(InvestmentPortfolioRecalculationJobMapper::toDomain)
    }

    override fun findById(id: UUID): PortfolioRecalculationJob? =
        entityRepository.findByKey(id)
            ?.let(InvestmentPortfolioRecalculationJobMapper::toDomain)

    @Transactional
    override fun tryClaimJob(
        jobId: UUID,
        nodeId: String,
    ): PortfolioRecalculationJob? =
        entityRepository.tryClaimJob(jobId, nodeId)
            ?.let(InvestmentPortfolioRecalculationJobMapper::toDomain)

    @Transactional
    override fun releaseZombieJobs(staleThreshold: Instant): Int = entityRepository.releaseZombieJobs(staleThreshold)

    override fun findOldestPending(limit: Int): List<PortfolioRecalculationJob> {
        val safeLimit = limit.coerceAtLeast(1)
        return entityRepository.findByStatus(
            status = PortfolioRecalculationStatus.PENDING,
            pageable = Pageable.from(0, safeLimit),
        ).map(InvestmentPortfolioRecalculationJobMapper::toDomain)
    }

    override fun findPendingByAccount(accountId: UUID): PortfolioRecalculationJob? =
        entityRepository.findByAccountAndStatuses(
            accountId = accountId,
            statuses = listOf(PortfolioRecalculationStatus.PENDING),
        ).firstOrNull()
            ?.let(InvestmentPortfolioRecalculationJobMapper::toDomain)

    override fun findProcessingByAccount(accountId: UUID): PortfolioRecalculationJob? =
        entityRepository.findByAccountAndStatuses(
            accountId = accountId,
            statuses = listOf(PortfolioRecalculationStatus.PROCESSING),
        ).firstOrNull()
            ?.let(InvestmentPortfolioRecalculationJobMapper::toDomain)

    override fun findActiveByAccount(accountId: UUID): PortfolioRecalculationJob? =
        entityRepository.findByAccountAndStatuses(
            accountId = accountId,
            statuses =
                listOf(
                    PortfolioRecalculationStatus.PENDING,
                    PortfolioRecalculationStatus.PROCESSING,
                ),
        ).firstOrNull()
            ?.let(InvestmentPortfolioRecalculationJobMapper::toDomain)

    override fun findLatestByAccount(accountId: UUID): PortfolioRecalculationJob? =
        entityRepository.findLatestByAccount(accountId)
            .firstOrNull()
            ?.let(InvestmentPortfolioRecalculationJobMapper::toDomain)
}
