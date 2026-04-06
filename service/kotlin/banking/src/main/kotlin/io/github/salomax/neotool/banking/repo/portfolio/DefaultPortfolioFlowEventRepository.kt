package io.github.salomax.neotool.banking.repo.portfolio

import io.github.salomax.neotool.banking.domain.portfolio.InvestmentPortfolioFlowEvent
import io.github.salomax.neotool.banking.model.portfolio.InvestmentPortfolioFlowEventMapper
import io.micronaut.transaction.annotation.Transactional
import jakarta.inject.Singleton
import java.time.LocalDate
import java.util.UUID

@Singleton
open class DefaultPortfolioFlowEventRepository(
    private val entityRepository: PortfolioFlowEventEntityRepository,
) : PortfolioFlowEventRepository {
    @Transactional
    override fun save(event: InvestmentPortfolioFlowEvent): InvestmentPortfolioFlowEvent =
        entityRepository.save(InvestmentPortfolioFlowEventMapper.toEntity(event))
            .let(InvestmentPortfolioFlowEventMapper::toDomain)

    @Transactional
    override fun deleteBySourceReference(
        sourceReferenceId: UUID,
        sourceReferenceType: String,
    ): Int = entityRepository.deleteBySourceReference(sourceReferenceId, sourceReferenceType)

    @Transactional
    override fun deleteByFinancialAccountAndRelatedHoldingId(
        financialAccountId: UUID,
        relatedHoldingId: UUID,
    ): Int = entityRepository.deleteByFinancialAccountAndRelatedHoldingId(financialAccountId, relatedHoldingId)

    override fun findByAccountAndDateRange(
        financialAccountId: UUID,
        from: LocalDate,
        to: LocalDate,
    ): List<InvestmentPortfolioFlowEvent> =
        entityRepository.findByAccountAndDateRange(financialAccountId, from, to)
            .map(InvestmentPortfolioFlowEventMapper::toDomain)

    override fun findByAccountAndDate(
        financialAccountId: UUID,
        date: LocalDate,
    ): List<InvestmentPortfolioFlowEvent> =
        entityRepository.findByAccountAndDate(financialAccountId, date)
            .map(InvestmentPortfolioFlowEventMapper::toDomain)
}
