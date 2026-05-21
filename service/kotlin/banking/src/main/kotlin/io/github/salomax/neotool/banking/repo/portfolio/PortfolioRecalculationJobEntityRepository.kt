package io.github.salomax.neotool.banking.repo.portfolio

import io.github.salomax.neotool.banking.domain.portfolio.PortfolioRecalculationStatus
import io.github.salomax.neotool.banking.model.portfolio.InvestmentPortfolioRecalculationJobEntity
import io.micronaut.data.annotation.Query
import io.micronaut.data.annotation.Repository
import io.micronaut.data.jpa.repository.JpaRepository
import io.micronaut.data.model.Pageable
import java.time.Instant
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
        UPDATE banking.investment_portfolio_recalculation_job
        SET status              = 'PROCESSING',
            processing_node_id  = :nodeId,
            last_heartbeat_at   = NOW(),
            started_at          = COALESCE(started_at, NOW()),
            error_code          = NULL,
            error_details       = NULL,
            updated_at          = NOW()
        WHERE id = :jobId
          AND status = 'PENDING'
        RETURNING *
        """,
        nativeQuery = true,
    )
    fun tryClaimJob(
        jobId: UUID,
        nodeId: String,
    ): InvestmentPortfolioRecalculationJobEntity?

    @Query(
        """
        UPDATE banking.investment_portfolio_recalculation_job
        SET status              = 'PENDING',
            processing_node_id  = NULL,
            last_heartbeat_at   = NULL,
            updated_at          = NOW()
        WHERE status = 'PROCESSING'
          AND (last_heartbeat_at IS NULL OR last_heartbeat_at < :staleThreshold)
        """,
        nativeQuery = true,
    )
    fun releaseZombieJobs(staleThreshold: Instant): Int

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

    @Query(
        """
        SELECT j FROM InvestmentPortfolioRecalculationJobEntity j
        WHERE j.status = :status
        ORDER BY j.createdAt ASC
        """,
    )
    fun findByStatus(
        status: PortfolioRecalculationStatus,
        pageable: Pageable,
    ): List<InvestmentPortfolioRecalculationJobEntity>
}
