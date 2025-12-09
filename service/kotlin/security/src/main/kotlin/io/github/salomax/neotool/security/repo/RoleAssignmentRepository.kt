package io.github.salomax.neotool.security.repo

import io.github.salomax.neotool.security.model.rbac.RoleAssignmentEntity
import io.micronaut.data.annotation.Query
import io.micronaut.data.annotation.Repository
import io.micronaut.data.jpa.repository.JpaRepository
import java.time.Instant
import java.util.UUID

@Repository
interface RoleAssignmentRepository : JpaRepository<RoleAssignmentEntity, UUID> {
    /**
     * Find all role assignments for a user that are currently valid (within date range).
     * Checks that:
     * - validFrom is null or <= now
     * - validUntil is null or >= now
     */
    @Query(
        """
        SELECT ra FROM RoleAssignmentEntity ra
        WHERE ra.userId = :userId
        AND (ra.validFrom IS NULL OR ra.validFrom <= :now)
        AND (ra.validUntil IS NULL OR ra.validUntil >= :now)
        """,
    )
    fun findValidAssignmentsByUserId(
        userId: UUID,
        now: Instant = Instant.now(),
    ): List<RoleAssignmentEntity>

    /**
     * Find all role assignments for a user (including expired and future-dated).
     */
    fun findByUserId(userId: UUID): List<RoleAssignmentEntity>

    /**
     * Find role assignments by role ID.
     */
    fun findByRoleId(roleId: Int): List<RoleAssignmentEntity>

    /**
     * Find role assignments by user ID and role ID.
     */
    fun findByUserIdAndRoleId(
        userId: UUID,
        roleId: Int,
    ): List<RoleAssignmentEntity>

    /**
     * Find all valid role assignments for multiple users (batch loading).
     * Checks that:
     * - validFrom is null or <= now
     * - validUntil is null or >= now
     * Optimized to avoid N+1 queries when loading roles for multiple users.
     */
    @Query(
        """
        SELECT ra FROM RoleAssignmentEntity ra
        WHERE ra.userId IN (:userIds)
        AND (ra.validFrom IS NULL OR ra.validFrom <= :now)
        AND (ra.validUntil IS NULL OR ra.validUntil >= :now)
        """,
    )
    fun findValidAssignmentsByUserIds(
        userIds: List<UUID>,
        now: Instant = Instant.now(),
    ): List<RoleAssignmentEntity>
}
