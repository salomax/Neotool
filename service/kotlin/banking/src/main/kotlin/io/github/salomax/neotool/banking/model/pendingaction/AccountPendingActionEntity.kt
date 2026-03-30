package io.github.salomax.neotool.banking.model.pendingaction

import io.github.salomax.neotool.banking.domain.pendingaction.AccountPendingActionStatus
import io.github.salomax.neotool.banking.domain.pendingaction.PendingActionSeverity
import io.github.salomax.neotool.banking.domain.pendingaction.PendingActionType
import io.github.salomax.neotool.common.entity.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.Generated
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.generator.EventType
import org.hibernate.type.SqlTypes
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "account_pending_actions", schema = "banking")
open class AccountPendingActionEntity(
    @Id
    @Generated(event = [EventType.INSERT])
    @Column(columnDefinition = "uuid", insertable = false, updatable = false)
    override val id: UUID? = null,
    @Column(name = "account_id", nullable = false)
    open var accountId: UUID,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    open var type: PendingActionType,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    open var severity: PendingActionSeverity,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    open var status: AccountPendingActionStatus = AccountPendingActionStatus.PENDING,
    /** Generic entity reference — replaces related_holding_id. */
    @Column(name = "entity_type", length = 40)
    open var entityType: String? = null,
    @Column(name = "entity_id")
    open var entityId: UUID? = null,
    @Column(name = "related_transaction_id")
    open var relatedTransactionId: UUID? = null,
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", columnDefinition = "jsonb", nullable = false)
    open var payloadJson: String = "{}",
    @Column(name = "visible_from", nullable = false)
    open var visibleFrom: Instant = Instant.now(),
    @Column(name = "notified_at")
    open var notifiedAt: Instant? = null,
    @Column(name = "dismissed_at")
    open var dismissedAt: Instant? = null,
    @Column(name = "completed_at")
    open var completedAt: Instant? = null,
    @Column(name = "cancelled_at")
    open var cancelledAt: Instant? = null,
    @Column(name = "expires_at")
    open var expiresAt: Instant? = null,
    @Column(name = "created_at", nullable = false)
    open var createdAt: Instant = Instant.now(),
    @Column(name = "updated_at", nullable = false)
    open var updatedAt: Instant = Instant.now(),
) : BaseEntity<UUID?>(id)
