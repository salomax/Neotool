package io.github.salomax.neotool.banking.model.investment

import io.github.salomax.neotool.banking.domain.investment.ComeCotasCheckpointJobStatus
import io.github.salomax.neotool.common.entity.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.Generated
import org.hibernate.generator.EventType
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@Entity
@Table(name = "come_cotas_checkpoint_job", schema = "banking")
open class ComeCotasCheckpointJobEntity(
    @Id
    @Generated(event = [EventType.INSERT])
    @Column(columnDefinition = "uuid", insertable = false, updatable = false)
    override val id: UUID? = null,
    @Column(name = "holding_id", nullable = false)
    open var holdingId: UUID,
    @Column(name = "financial_account_id", nullable = false)
    open var financialAccountId: UUID,
    @Column(name = "account_id", nullable = false)
    open var accountId: UUID,
    @Column(name = "checkpoint_date", nullable = false)
    open var checkpointDate: LocalDate,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    open var status: ComeCotasCheckpointJobStatus,
    @Column(name = "processing_node_id", length = 255)
    open var processingNodeId: String? = null,
    @Column(name = "last_heartbeat_at")
    open var lastHeartbeatAt: Instant? = null,
    @Column(name = "started_at")
    open var startedAt: Instant? = null,
    @Column(name = "completed_at")
    open var completedAt: Instant? = null,
    @Column(name = "next_attempt_at")
    open var nextAttemptAt: Instant? = null,
    @Column(name = "attempt_count", nullable = false)
    open var attemptCount: Int = 0,
    @Column(name = "error_code", length = 80)
    open var errorCode: String? = null,
    @Column(name = "error_details")
    open var errorDetails: String? = null,
    @Column(name = "created_at", nullable = false)
    open var createdAt: Instant = Instant.now(),
    @Column(name = "updated_at", nullable = false)
    open var updatedAt: Instant = Instant.now(),
) : BaseEntity<UUID?>(id)
