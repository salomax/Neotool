package io.github.salomax.neotool.banking.repo.portfolio

import io.github.salomax.neotool.banking.model.portfolio.MarketDataSyncControlEntity
import io.micronaut.data.annotation.Query
import io.micronaut.data.annotation.Repository
import io.micronaut.data.jpa.repository.JpaRepository
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@Repository
interface MarketDataSyncControlEntityRepository : JpaRepository<MarketDataSyncControlEntity, String> {
    @Query("SELECT c FROM MarketDataSyncControlEntity c WHERE c.jobName = :jobName")
    fun findByKey(jobName: String): MarketDataSyncControlEntity?

    @Query("SELECT c FROM MarketDataSyncControlEntity c ORDER BY c.jobName ASC")
    fun findAllOrdered(): List<MarketDataSyncControlEntity>

    @Query(
        """
        INSERT INTO banking.market_data_sync_control
            (job_name, source_last_data_date, source_last_run_at, last_synced_date, last_cursor,
             last_sync_started_at, last_sync_completed_at, status, last_event_id,
             last_error, updated_at)
        VALUES
            (:jobName, :sourceLastDataDate, :sourceLastRunAt, :lastSyncedDate, :lastCursor,
             :lastSyncStartedAt, :lastSyncCompletedAt, :status, :lastEventId,
             :lastError, NOW())
        ON CONFLICT (job_name)
        DO UPDATE SET
            source_last_data_date  = EXCLUDED.source_last_data_date,
            source_last_run_at     = EXCLUDED.source_last_run_at,
            last_synced_date       = EXCLUDED.last_synced_date,
            last_cursor            = EXCLUDED.last_cursor,
            last_sync_started_at   = EXCLUDED.last_sync_started_at,
            last_sync_completed_at = EXCLUDED.last_sync_completed_at,
            status                 = EXCLUDED.status,
            last_event_id          = EXCLUDED.last_event_id,
            last_error             = EXCLUDED.last_error,
            updated_at             = NOW()
        """,
        nativeQuery = true,
    )
    fun upsert(
        jobName: String,
        sourceLastDataDate: LocalDate?,
        sourceLastRunAt: Instant?,
        lastSyncedDate: LocalDate?,
        lastCursor: String?,
        lastSyncStartedAt: Instant?,
        lastSyncCompletedAt: Instant?,
        status: String,
        lastEventId: UUID?,
        lastError: String?,
    )
}
