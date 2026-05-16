package io.github.salomax.neotool.banking.domain.pendingaction

import java.time.Instant
import java.util.UUID

interface AccountPendingActionRepository {
    /** Returns visible actions (visibleFrom <= now) for the account, optionally filtered by status. */
    fun findByAccountAndStatus(
        accountId: UUID,
        status: AccountPendingActionStatus?,
        now: Instant = Instant.now(),
    ): List<AccountPendingAction>

    fun findById(
        id: UUID,
        accountId: UUID,
    ): AccountPendingAction?

    /** Finds a PENDING action for a specific entity + type combination. Used for idempotency. */
    fun findPendingByEntityAndType(
        entityType: String,
        entityId: UUID,
        type: PendingActionType,
    ): AccountPendingAction?

    /** True when there is at least one open (PENDING) action for (entityType, entityId, type). */
    fun existsOpenForEntity(
        entityType: String,
        entityId: UUID,
        type: PendingActionType,
    ): Boolean

    /** Returns actions ready to be sent via comms (visible, pending, not yet notified). */
    fun findDueForNotification(now: Instant): List<AccountPendingAction>

    /**
     * Cancels the PENDING action for a given entity + type, if one exists.
     * Called by the producer when the underlying event is no longer relevant
     * (e.g. early redemption before maturity, payment made before due date).
     * Returns true if an action was cancelled, false if nothing was pending.
     */
    fun cancelByEntityAndType(
        entityType: String,
        entityId: UUID,
        type: PendingActionType,
        now: Instant = Instant.now(),
    ): Boolean

    /** Dismisses all open (PENDING) actions for (entityType, entityId, type). */
    fun dismissOpenForEntity(
        entityType: String,
        entityId: UUID,
        type: PendingActionType,
        now: Instant = Instant.now(),
    ): Int

    fun save(action: AccountPendingAction): AccountPendingAction

    fun update(action: AccountPendingAction): AccountPendingAction
}
