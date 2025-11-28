package io.github.salomax.neotool.security.repo

import io.github.salomax.neotool.security.domain.rbac.ScopeType
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
     * Find role assignments for a user with specific scope.
     */
    @Query(
        """
        SELECT ra FROM RoleAssignmentEntity ra
        WHERE ra.userId = :userId
        AND ra.scopeType = :scopeType
        AND (ra.scopeId IS NULL OR ra.scopeId = :scopeId)
        AND (ra.validFrom IS NULL OR ra.validFrom <= :now)
        AND (ra.validUntil IS NULL OR ra.validUntil >= :now)
        """,
    )
    fun findValidAssignmentsByUserIdAndScope(
        userId: UUID,
        scopeType: ScopeType,
        scopeId: UUID?,
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
}
