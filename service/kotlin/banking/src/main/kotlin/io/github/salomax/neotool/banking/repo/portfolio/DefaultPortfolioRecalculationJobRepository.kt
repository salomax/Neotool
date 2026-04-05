package io.github.salomax.neotool.banking.repo.portfolio

import io.github.salomax.neotool.banking.domain.portfolio.PortfolioRecalculationJob
import io.github.salomax.neotool.banking.domain.portfolio.PortfolioRecalculationStatus
import io.github.salomax.neotool.banking.model.portfolio.InvestmentPortfolioRecalculationJobMapper
import io.micronaut.transaction.annotation.Transactional
import jakarta.inject.Singleton
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
                ?: throw NoSuchElementException("Portfolio recalculation job $id not found")
        InvestmentPortfolioRecalculationJobMapper.updateEntity(entity, job)
        return entityRepository.update(entity)
            .let(InvestmentPortfolioRecalculationJobMapper::toDomain)
    }

    override fun findById(id: UUID): PortfolioRecalculationJob? =
        entityRepository.findByKey(id)
            ?.let(InvestmentPortfolioRecalculationJobMapper::toDomain)

    override fun findPendingByAccount(accountId: UUID): PortfolioRecalculationJob? =
        entityRepository.findByAccountAndStatuses(
            accountId = accountId,
            statuses = listOf(PortfolioRecalculationStatus.PENDING),
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
