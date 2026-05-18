package io.github.salomax.neotool.banking.repo.pendingaction

import io.github.salomax.neotool.banking.domain.pendingaction.AccountPendingActionStatus
import io.github.salomax.neotool.banking.domain.pendingaction.PendingActionType
import io.github.salomax.neotool.banking.model.pendingaction.AccountPendingActionEntity
import io.micronaut.data.annotation.Query
import io.micronaut.data.annotation.Repository
import io.micronaut.data.jpa.repository.JpaRepository
import java.time.Instant
import java.util.UUID

@Repository
interface AccountPendingActionEntityRepository : JpaRepository<AccountPendingActionEntity, UUID> {
    @Query(
        """
        SELECT p FROM AccountPendingActionEntity p
        WHERE p.accountId = :accountId
          AND (:status IS NULL OR p.status = :status)
          AND p.visibleFrom <= :now
        ORDER BY p.createdAt DESC
        """,
    )
    fun findByAccountIdAndOptionalStatus(
        accountId: UUID,
        status: AccountPendingActionStatus?,
        now: Instant,
    ): List<AccountPendingActionEntity>

    fun findByIdAndAccountId(
        id: UUID,
        accountId: UUID,
    ): AccountPendingActionEntity?

    fun findFirstByEntityTypeAndEntityIdAndTypeAndStatusOrderByCreatedAtDesc(
        entityType: String,
        entityId: UUID,
        type: PendingActionType,
        status: AccountPendingActionStatus,
    ): AccountPendingActionEntity?

    @Query(
        """
        SELECT p FROM AccountPendingActionEntity p
        WHERE p.status = 'PENDING'
          AND p.visibleFrom <= :now
          AND p.notifiedAt IS NULL
        ORDER BY p.visibleFrom ASC
        """,
    )
    fun findDueForNotification(now: Instant): List<AccountPendingActionEntity>

    @Query(
        """
        SELECT COUNT(*) > 0
        FROM banking.account_pending_actions p
        WHERE p.entity_type = :entityType
          AND p.entity_id = :entityId
          AND p.type = :type
          AND p.status = 'PENDING'
          AND (p.expires_at IS NULL OR p.expires_at > NOW())
        """,
        nativeQuery = true,
    )
    fun existsOpenForEntity(
        entityType: String,
        entityId: UUID,
        type: String,
    ): Boolean

    @Query(
        """
        UPDATE banking.account_pending_actions
           SET status = 'DISMISSED',
               dismissed_at = :now,
               updated_at = :now
         WHERE entity_type = :entityType
           AND entity_id = :entityId
           AND type = :type
           AND status = 'PENDING'
        """,
        nativeQuery = true,
    )
    fun dismissOpenForEntity(
        entityType: String,
        entityId: UUID,
        type: String,
        now: Instant,
    ): Int
}
