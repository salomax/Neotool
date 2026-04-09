package io.github.salomax.neotool.banking.model.portfolio

import io.github.salomax.neotool.banking.domain.portfolio.MarketDataSyncStatus
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@Entity
@Table(name = "market_data_sync_control", schema = "banking")
open class MarketDataSyncControlEntity(
    @Id
    @Column(name = "job_name", nullable = false, length = 50)
    open var jobName: String,
    @Column(name = "source_last_data_date")
    open var sourceLastDataDate: LocalDate? = null,
    @Column(name = "source_last_run_at")
    open var sourceLastRunAt: Instant? = null,
    @Column(name = "last_synced_date")
    open var lastSyncedDate: LocalDate? = null,
    @Column(name = "last_cursor")
    open var lastCursor: String? = null,
    @Column(name = "last_sync_started_at")
    open var lastSyncStartedAt: Instant? = null,
    @Column(name = "last_sync_completed_at")
    open var lastSyncCompletedAt: Instant? = null,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    open var status: MarketDataSyncStatus,
    @Column(name = "last_event_id")
    open var lastEventId: UUID? = null,
    @Column(name = "last_error")
    open var lastError: String? = null,
    @Column(name = "updated_at", nullable = false)
    open var updatedAt: Instant = Instant.now(),
)
