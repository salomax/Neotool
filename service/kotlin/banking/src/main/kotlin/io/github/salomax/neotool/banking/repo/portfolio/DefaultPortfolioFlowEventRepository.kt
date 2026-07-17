package io.github.salomax.neotool.banking.repo.portfolio

import io.github.salomax.neotool.banking.domain.portfolio.ContributionTotals
import io.github.salomax.neotool.banking.domain.portfolio.InvestmentPortfolioFlowEvent
import io.github.salomax.neotool.banking.domain.toBigDecimalOrZero
import io.github.salomax.neotool.banking.model.portfolio.InvestmentPortfolioFlowEventMapper
import io.micronaut.transaction.annotation.Transactional
import jakarta.inject.Singleton
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import java.time.LocalDate
import java.util.UUID

@Singleton
open class DefaultPortfolioFlowEventRepository(
    private val entityRepository: PortfolioFlowEventEntityRepository,
    @PersistenceContext private val entityManager: EntityManager,
) : PortfolioFlowEventRepository {
    @Transactional
    override fun save(event: InvestmentPortfolioFlowEvent): InvestmentPortfolioFlowEvent =
        entityRepository.save(InvestmentPortfolioFlowEventMapper.toEntity(event))
            .let(InvestmentPortfolioFlowEventMapper::toDomain)

    @Transactional
    override fun deleteBySourceReference(
        sourceReferenceId: UUID,
        sourceReferenceType: String,
    ): Int =
        entityManager
            .createNativeQuery(
                """
                DELETE FROM banking.investment_portfolio_flow_event
                WHERE source_reference_id = :sourceReferenceId
                  AND source_reference_type = :sourceReferenceType
                """.trimIndent(),
            ).setParameter("sourceReferenceId", sourceReferenceId)
            .setParameter("sourceReferenceType", sourceReferenceType)
            .executeUpdate()

    @Transactional
    override fun deleteByFinancialAccountAndRelatedHoldingId(
        financialAccountId: UUID,
        relatedHoldingId: UUID,
    ): Int =
        entityManager
            .createNativeQuery(
                """
                DELETE FROM banking.investment_portfolio_flow_event
                WHERE financial_account_id = :financialAccountId
                  AND related_holding_id = :relatedHoldingId
                """.trimIndent(),
            ).setParameter("financialAccountId", financialAccountId)
            .setParameter("relatedHoldingId", relatedHoldingId)
            .executeUpdate()

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

    override fun sumContributionsByRange(
        accountId: UUID,
        financialAccountId: UUID?,
        startYear: Int,
        startMonth: Int,
        endYear: Int,
        endMonth: Int,
    ): ContributionTotals {
        val rangeStart = LocalDate.of(startYear, startMonth, 1)
        val rangeEnd = LocalDate.of(endYear, endMonth, 1).plusMonths(1).minusDays(1)

        val accountScopeFilter =
            if (financialAccountId != null) {
                """
                e.financial_account_id IN (
                    SELECT id
                    FROM banking.financial_accounts
                    WHERE id = CAST(:financialAccountId AS uuid)
                      AND account_id = CAST(:accountId AS uuid)
                      AND type = 'INVESTMENT'
                      AND deleted_at IS NULL
                )
                """.trimIndent()
            } else {
                """
                e.financial_account_id IN (
                    SELECT id
                    FROM banking.financial_accounts
                    WHERE account_id = CAST(:accountId AS uuid)
                      AND type = 'INVESTMENT'
                      AND deleted_at IS NULL
                )
                """.trimIndent()
            }

        val sql =
            """
            SELECT
                COALESCE(SUM(e.gross_amount) FILTER (WHERE e.event_type = 'DEPOSIT'), 0)    AS total_invested,
                COALESCE(SUM(e.gross_amount) FILTER (WHERE e.event_type = 'WITHDRAWAL'), 0) AS total_withdrawn
            FROM banking.investment_portfolio_flow_event e
            WHERE $accountScopeFilter
              AND e.related_holding_id IS NOT NULL
              AND e.event_date >= :rangeStart
              AND e.event_date <= :rangeEnd
            """.trimIndent()

        val query = entityManager.createNativeQuery(sql)
        query.setParameter("accountId", accountId.toString())
        if (financialAccountId != null) {
            query.setParameter("financialAccountId", financialAccountId.toString())
        }
        query.setParameter("rangeStart", rangeStart)
        query.setParameter("rangeEnd", rangeEnd)

        @Suppress("UNCHECKED_CAST")
        val row = query.singleResult as Array<Any?>
        return ContributionTotals(
            totalInvested = row[0].toBigDecimalOrZero(),
            totalWithdrawn = row[1].toBigDecimalOrZero(),
        )
    }
}
