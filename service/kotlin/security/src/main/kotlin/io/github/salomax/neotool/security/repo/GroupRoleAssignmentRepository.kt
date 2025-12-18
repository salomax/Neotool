package io.github.salomax.neotool.security.repo

import io.github.salomax.neotool.security.model.rbac.GroupRoleAssignmentEntity
import io.micronaut.data.annotation.Query
import io.micronaut.data.annotation.Repository
import io.micronaut.data.jpa.repository.JpaRepository
import java.time.Instant
import java.util.UUID

@Repository
interface GroupRoleAssignmentRepository : JpaRepository<GroupRoleAssignmentEntity, UUID> {
    /**
     * Find all valid role assignments for a group.
     * Checks that:
     * - validFrom is null or <= now
     * - validUntil is null or >= now
     */
    @Query(
        """
        SELECT gra FROM GroupRoleAssignmentEntity gra
        WHERE gra.groupId = :groupId
        AND (gra.validFrom IS NULL OR gra.validFrom <= :now)
        AND (gra.validUntil IS NULL OR gra.validUntil >= :now)
        """,
    )
    fun findValidAssignmentsByGroupId(
        groupId: UUID,
        now: Instant = Instant.now(),
    ): List<GroupRoleAssignmentEntity>

    /**
     * Find all role assignments for a group (including expired and future-dated).
     */
    fun findByGroupId(groupId: UUID): List<GroupRoleAssignmentEntity>

    /**
     * Find role assignments by role ID.
     */
    fun findByRoleId(roleId: UUID): List<GroupRoleAssignmentEntity>

    /**
     * Find all valid role assignments for multiple groups (batch loading).
     * Optimized to avoid N+1 queries when a user belongs to many groups.
     */
    @Query(
        """
        SELECT gra FROM GroupRoleAssignmentEntity gra
        WHERE gra.groupId IN (:groupIds)
        AND (gra.validFrom IS NULL OR gra.validFrom <= :now)
        AND (gra.validUntil IS NULL OR gra.validUntil >= :now)
        """,
    )
    fun findValidAssignmentsByGroupIds(
        groupIds: List<UUID>,
        now: Instant = Instant.now(),
    ): List<GroupRoleAssignmentEntity>
}
