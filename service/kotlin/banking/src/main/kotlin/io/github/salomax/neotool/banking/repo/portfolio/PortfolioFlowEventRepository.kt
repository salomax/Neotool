package io.github.salomax.neotool.banking.repo.portfolio

import io.github.salomax.neotool.banking.domain.portfolio.ContributionTotals
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

    /**
     * Aggregates holding-linked DEPOSIT and WITHDRAWAL events for the period in one query.
     *
     * Cash-only events (relatedHoldingId = null) are excluded so dashboard investment KPIs
     * stay aligned with holdings-based portfolio metrics.
     */
    fun sumContributionsByRange(
        accountId: UUID,
        financialAccountId: UUID?,
        startYear: Int,
        startMonth: Int,
        endYear: Int,
        endMonth: Int,
    ): ContributionTotals
}
