package io.github.salomax.neotool.banking.repo.portfolio

import io.github.salomax.neotool.banking.domain.portfolio.InvestmentPortfolioFlowEvent
import java.time.LocalDate
import java.util.UUID

interface PortfolioFlowEventRepository {
    fun save(event: InvestmentPortfolioFlowEvent): InvestmentPortfolioFlowEvent

    fun deleteBySourceReference(
        sourceReferenceId: UUID,
        sourceReferenceType: String,
    ): Int

    fun deleteByFinancialAccountAndRelatedHoldingId(
        financialAccountId: UUID,
        relatedHoldingId: UUID,
    ): Int

    fun findByAccountAndDateRange(
        financialAccountId: UUID,
        from: LocalDate,
        to: LocalDate,
    ): List<InvestmentPortfolioFlowEvent>

    fun findByAccountAndDate(
        financialAccountId: UUID,
        date: LocalDate,
    ): List<InvestmentPortfolioFlowEvent>
}
