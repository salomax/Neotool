package io.github.salomax.neotool.banking.repo.portfolio

import io.github.salomax.neotool.banking.domain.portfolio.PortfolioRecalculationStatus
import io.github.salomax.neotool.banking.model.portfolio.InvestmentPortfolioRecalculationJobEntity
import io.micronaut.data.annotation.Query
import io.micronaut.data.annotation.Repository
import io.micronaut.data.jpa.repository.JpaRepository
import java.util.UUID

@Repository
interface PortfolioRecalculationJobEntityRepository :
    JpaRepository<InvestmentPortfolioRecalculationJobEntity, UUID> {
    @Query(
        """
        SELECT j FROM InvestmentPortfolioRecalculationJobEntity j
        WHERE j.id = :id
        """,
    )
    fun findByKey(id: UUID): InvestmentPortfolioRecalculationJobEntity?

    @Query(
        """
        SELECT j FROM InvestmentPortfolioRecalculationJobEntity j
        WHERE j.accountId = :accountId
          AND j.status IN (:statuses)
        ORDER BY j.createdAt DESC
        """,
    )
    fun findByAccountAndStatuses(
        accountId: UUID,
        statuses: Collection<PortfolioRecalculationStatus>,
    ): List<InvestmentPortfolioRecalculationJobEntity>

    @Query(
        """
        SELECT j FROM InvestmentPortfolioRecalculationJobEntity j
        WHERE j.accountId = :accountId
        ORDER BY j.createdAt DESC
        """,
    )
    fun findLatestByAccount(accountId: UUID): List<InvestmentPortfolioRecalculationJobEntity>
}
