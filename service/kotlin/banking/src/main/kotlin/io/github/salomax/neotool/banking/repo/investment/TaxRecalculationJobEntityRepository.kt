package io.github.salomax.neotool.banking.repo.investment

import io.github.salomax.neotool.banking.domain.investment.TaxRecalculationStatus
import io.github.salomax.neotool.banking.model.investment.TaxRecalculationJobEntity
import io.micronaut.data.annotation.Query
import io.micronaut.data.annotation.Repository
import io.micronaut.data.jpa.repository.JpaRepository
import io.micronaut.data.model.Pageable
import java.time.Instant
import java.util.UUID

@Repository
interface TaxRecalculationJobEntityRepository : JpaRepository<TaxRecalculationJobEntity, UUID> {
    @Query(
        """
        SELECT j FROM TaxRecalculationJobEntity j
        WHERE j.id = :id
        """,
    )
    fun findByKey(id: UUID): TaxRecalculationJobEntity?

    @Query(
        """
        UPDATE banking.investment_tax_recalculation_job
        SET status = 'PROCESSING',
            processing_node_id = :nodeId,
            last_heartbeat_at = NOW(),
            started_at = COALESCE(started_at, NOW()),
            error_code = NULL,
            error_details = NULL,
            updated_at = NOW()
        WHERE id = :jobId
          AND status = 'PENDING'
          AND NOT EXISTS (
              SELECT 1
              FROM banking.investment_tax_recalculation_job other
              WHERE other.holding_id = banking.investment_tax_recalculation_job.holding_id
                AND other.status = 'PROCESSING'
                AND other.id <> :jobId
          )
        RETURNING *
        """,
        nativeQuery = true,
    )
    fun tryClaimJob(
        jobId: UUID,
        nodeId: String,
    ): TaxRecalculationJobEntity?

    @Query(
        """
        UPDATE banking.investment_tax_recalculation_job
        SET status = 'PENDING',
            processing_node_id = NULL,
            last_heartbeat_at = NULL,
            updated_at = NOW()
        WHERE status = 'PROCESSING'
          AND (last_heartbeat_at IS NULL OR last_heartbeat_at < :staleThreshold)
        """,
        nativeQuery = true,
    )
    fun releaseZombieJobs(staleThreshold: Instant): Int

    @Query(
        """
        SELECT j FROM TaxRecalculationJobEntity j
        WHERE j.holdingId = :holdingId
          AND j.status IN :statuses
        ORDER BY j.createdAt DESC
        """,
    )
    fun findByHoldingAndStatuses(
        holdingId: UUID,
        statuses: Collection<TaxRecalculationStatus>,
    ): List<TaxRecalculationJobEntity>

    @Query(
        """
        SELECT j FROM TaxRecalculationJobEntity j
        WHERE j.holdingId = :holdingId
        ORDER BY j.createdAt DESC
        """,
    )
    fun findLatestByHolding(
        holdingId: UUID,
        pageable: Pageable = Pageable.from(0, 1),
    ): List<TaxRecalculationJobEntity>

    @Query(
        """
        SELECT j FROM TaxRecalculationJobEntity j
        WHERE j.status = :status
        ORDER BY j.createdAt ASC
        """,
    )
    fun findByStatus(
        status: TaxRecalculationStatus,
        pageable: Pageable,
    ): List<TaxRecalculationJobEntity>
}
