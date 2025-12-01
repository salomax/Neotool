package io.github.salomax.neotool.security.repo

import io.github.salomax.neotool.security.model.rbac.GroupEntity
import io.micronaut.data.annotation.Query
import io.micronaut.data.annotation.Repository
import io.micronaut.data.jpa.repository.JpaRepository
import java.util.Optional
import java.util.UUID

@Repository
interface GroupRepository : JpaRepository<GroupEntity, UUID> {
    fun findByName(name: String): Optional<GroupEntity>

    /**
     * Find all groups by their IDs.
     */
    fun findByIdIn(ids: List<UUID>): List<GroupEntity>

    /**
     * Find all groups with cursor-based pagination, ordered alphabetically by name ascending.
     *
     * @param first Maximum number of results to return
     * @param after Cursor (UUID) to start after (exclusive)
     * @return List of groups ordered by name ascending
     */
    @Query(
        value = """
        SELECT * FROM security.groups
        WHERE (:after IS NULL OR id > :after)
        ORDER BY name ASC, id ASC
        LIMIT :first
        """,
        nativeQuery = true,
    )
    fun findAll(
        first: Int,
        after: UUID?,
    ): List<GroupEntity>

    /**
     * Search groups by name with cursor-based pagination.
     * Performs case-insensitive partial matching on name field.
     * Results are ordered alphabetically by name ascending.
     *
     * @param query Search query (partial match, case-insensitive)
     * @param first Maximum number of results to return
     * @param after Cursor (UUID) to start after (exclusive)
     * @return List of matching groups ordered by name ascending
     */
    @Query(
        value = """
        SELECT * FROM security.groups
        WHERE LOWER(name) LIKE LOWER(CONCAT('%', :query, '%'))
        AND (:after IS NULL OR id > :after)
        ORDER BY name ASC, id ASC
        LIMIT :first
        """,
        nativeQuery = true,
    )
    fun searchByName(
        query: String,
        first: Int,
        after: UUID?,
    ): List<GroupEntity>

    /**
     * Check if a group has any user members.
     * Queries the group_memberships table to determine if any users are assigned to this group.
     *
     * @param groupId The group ID to check
     * @return true if the group has user members, false otherwise
     */
    @Query(
        value = """
        SELECT EXISTS(
            SELECT 1 FROM security.group_memberships
            WHERE group_id = :groupId
        )
        """,
        nativeQuery = true,
    )
    fun hasUserMembers(groupId: UUID): Boolean

    /**
     * Check if a group has any role assignments.
     * Queries the group_role_assignments table to determine if any roles are assigned to this group.
     *
     * @param groupId The group ID to check
     * @return true if the group has role assignments, false otherwise
     */
    @Query(
        value = """
        SELECT EXISTS(
            SELECT 1 FROM security.group_role_assignments
            WHERE group_id = :groupId
        )
        """,
        nativeQuery = true,
    )
    fun hasRoleAssignments(groupId: UUID): Boolean
}
