package io.github.salomax.neotool.banking.repo.portfolio

import io.github.salomax.neotool.banking.model.portfolio.InvestmentPortfolioFlowEventEntity
import io.micronaut.data.annotation.Query
import io.micronaut.data.annotation.Repository
import io.micronaut.data.jpa.repository.JpaRepository
import java.time.LocalDate
import java.util.UUID

@Repository
interface PortfolioFlowEventEntityRepository : JpaRepository<InvestmentPortfolioFlowEventEntity, UUID> {
    @Query(
        """
        SELECT e FROM InvestmentPortfolioFlowEventEntity e
        WHERE e.financialAccountId = :financialAccountId
          AND e.eventDate BETWEEN :from AND :to
        ORDER BY e.eventDate ASC, e.createdAt ASC
        """,
    )
    fun findByAccountAndDateRange(
        financialAccountId: UUID,
        from: LocalDate,
        to: LocalDate,
    ): List<InvestmentPortfolioFlowEventEntity>

    @Query(
        """
        SELECT e FROM InvestmentPortfolioFlowEventEntity e
        WHERE e.financialAccountId = :financialAccountId
          AND e.eventDate = :date
        ORDER BY e.createdAt ASC
        """,
    )
    fun findByAccountAndDate(
        financialAccountId: UUID,
        date: LocalDate,
    ): List<InvestmentPortfolioFlowEventEntity>

    @Query(
        """
        DELETE FROM banking.investment_portfolio_flow_event
        WHERE source_reference_id = :sourceReferenceId
          AND source_reference_type = :sourceReferenceType
        """,
        nativeQuery = true,
    )
    fun deleteBySourceReference(
        sourceReferenceId: UUID,
        sourceReferenceType: String,
    ): Int

    @Query(
        """
        DELETE FROM banking.investment_portfolio_flow_event
        WHERE financial_account_id = :financialAccountId
          AND related_holding_id = :relatedHoldingId
        """,
        nativeQuery = true,
    )
    fun deleteByFinancialAccountAndRelatedHoldingId(
        financialAccountId: UUID,
        relatedHoldingId: UUID,
    ): Int
}
