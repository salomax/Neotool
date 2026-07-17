package io.github.salomax.neotool.banking.repo.investment

import io.github.salomax.neotool.banking.domain.investment.ComeCotasCheckpointJobStatus
import io.github.salomax.neotool.banking.model.investment.ComeCotasCheckpointJobEntity
import io.micronaut.data.annotation.Query
import io.micronaut.data.annotation.Repository
import io.micronaut.data.jpa.repository.JpaRepository
import io.micronaut.data.model.Pageable
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@Repository
interface ComeCotasCheckpointJobEntityRepository : JpaRepository<ComeCotasCheckpointJobEntity, UUID> {
    @Query(
        """
        SELECT j FROM ComeCotasCheckpointJobEntity j
        WHERE j.id = :id
        """,
    )
    fun findByKey(id: UUID): ComeCotasCheckpointJobEntity?

    @Query(
        """
        INSERT INTO banking.come_cotas_checkpoint_job (
            holding_id,
            financial_account_id,
            account_id,
            checkpoint_date,
            status,
            next_attempt_at,
            attempt_count,
            created_at,
            updated_at
        ) VALUES (
            :holdingId,
            :financialAccountId,
            :accountId,
            :checkpointDate,
            :status,
            NULL,
            0,
            NOW(),
            NOW()
        )
        ON CONFLICT (holding_id, checkpoint_date) DO NOTHING
        """,
        nativeQuery = true,
    )
    fun insertIfAbsent(
        holdingId: UUID,
        financialAccountId: UUID,
        accountId: UUID,
        checkpointDate: LocalDate,
        status: String,
    ): Int

    @Query(
        """
        UPDATE banking.come_cotas_checkpoint_job
        SET status = 'PROCESSING',
            processing_node_id = :nodeId,
            last_heartbeat_at = NOW(),
            started_at = COALESCE(started_at, NOW()),
            error_code = NULL,
            error_details = NULL,
            attempt_count = attempt_count + 1,
            updated_at = NOW()
        WHERE id = :jobId
          AND status = 'PENDING'
          AND (next_attempt_at IS NULL OR next_attempt_at <= NOW())
        RETURNING *
        """,
        nativeQuery = true,
    )
    fun tryClaimJob(
        jobId: UUID,
        nodeId: String,
    ): ComeCotasCheckpointJobEntity?

    @Query(
        """
        UPDATE banking.come_cotas_checkpoint_job
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
        SELECT j FROM ComeCotasCheckpointJobEntity j
        WHERE j.status = :status
          AND (j.nextAttemptAt IS NULL OR j.nextAttemptAt <= :readyAt)
        ORDER BY j.checkpointDate ASC, j.createdAt ASC
        """,
    )
    fun findByStatus(
        status: ComeCotasCheckpointJobStatus,
        readyAt: Instant,
        pageable: Pageable,
    ): List<ComeCotasCheckpointJobEntity>

    @Query(
        """
        SELECT j FROM ComeCotasCheckpointJobEntity j
        WHERE j.holdingId = :holdingId
          AND j.checkpointDate = :checkpointDate
        """,
    )
    fun findByHoldingAndCheckpointDate(
        holdingId: UUID,
        checkpointDate: LocalDate,
    ): ComeCotasCheckpointJobEntity?

    @Query(
        """
        SELECT j FROM ComeCotasCheckpointJobEntity j
        WHERE j.accountId = :accountId
          AND j.status IN ('PENDING', 'PROCESSING')
        ORDER BY j.checkpointDate ASC, j.createdAt ASC
        """,
    )
    fun findActiveByAccount(
        accountId: UUID,
        pageable: Pageable = Pageable.from(0, 1),
    ): List<ComeCotasCheckpointJobEntity>

    @Query(
        """
        SELECT j FROM ComeCotasCheckpointJobEntity j
        WHERE j.accountId = :accountId
        ORDER BY j.updatedAt DESC, j.createdAt DESC
        """,
    )
    fun findLatestByAccount(
        accountId: UUID,
        pageable: Pageable = Pageable.from(0, 1),
    ): List<ComeCotasCheckpointJobEntity>
}
