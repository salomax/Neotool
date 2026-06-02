package io.github.salomax.neotool.banking.domain.pendingaction

import java.time.Instant
import java.util.UUID

data class AccountPendingAction(
    val id: UUID? = null,
    val accountId: UUID,
    val type: PendingActionType,
    val severity: PendingActionSeverity,
    val status: AccountPendingActionStatus,
    /** Generic entity reference — no FK; entityType drives resolution. */
    val entityType: String?,
    val entityId: UUID?,
    val relatedTransactionId: UUID?,
    val payload: Map<String, Any?>,
    /** Action is only shown to the user (and eligible for comms) at or after this instant. */
    val visibleFrom: Instant,
    /** Set by PendingActionNotifierJob after publishing to comms-service. */
    val notifiedAt: Instant?,
    val dismissedAt: Instant?,
    val completedAt: Instant?,
    val cancelledAt: Instant?,
    val expiresAt: Instant?,
    val createdAt: Instant,
    val updatedAt: Instant,
)
