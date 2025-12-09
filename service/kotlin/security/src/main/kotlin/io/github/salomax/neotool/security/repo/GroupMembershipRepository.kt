package io.github.salomax.neotool.security.repo

import io.github.salomax.neotool.security.model.rbac.GroupMembershipEntity
import io.micronaut.data.annotation.Query
import io.micronaut.data.annotation.Repository
import io.micronaut.data.jpa.repository.JpaRepository
import java.time.Instant
import java.util.UUID

@Repository
interface GroupMembershipRepository : JpaRepository<GroupMembershipEntity, UUID> {
    /**
     * Find all active group memberships for a user.
     * A membership is active if validUntil is null or in the future.
     */
    @Query(
        """
        SELECT gm FROM GroupMembershipEntity gm
        WHERE gm.userId = :userId
        AND (gm.validUntil IS NULL OR gm.validUntil >= :now)
        """,
    )
    fun findActiveMembershipsByUserId(
        userId: UUID,
        now: Instant = Instant.now(),
    ): List<GroupMembershipEntity>

    /**
     * Find all group memberships for a user (including expired).
     */
    fun findByUserId(userId: UUID): List<GroupMembershipEntity>

    /**
     * Find all memberships for a group.
     */
    fun findByGroupId(groupId: UUID): List<GroupMembershipEntity>

    /**
     * Find a specific membership by user and group.
     */
    fun findByUserIdAndGroupId(
        userId: UUID,
        groupId: UUID,
    ): List<GroupMembershipEntity>

    /**
     * Find all memberships for multiple groups (batch loading).
     * Optimized to avoid N+1 queries when loading members for multiple groups.
     */
    fun findByGroupIdIn(groupIds: List<UUID>): List<GroupMembershipEntity>

    /**
     * Find all active group memberships for multiple users (batch loading).
     * A membership is active if validUntil is null or in the future.
     * Optimized to avoid N+1 queries when loading groups for multiple users.
     */
    @Query(
        """
        SELECT gm FROM GroupMembershipEntity gm
        WHERE gm.userId IN (:userIds)
        AND (gm.validUntil IS NULL OR gm.validUntil >= :now)
        """,
    )
    fun findActiveMembershipsByUserIds(
        userIds: List<UUID>,
        now: Instant = Instant.now(),
    ): List<GroupMembershipEntity>
}
