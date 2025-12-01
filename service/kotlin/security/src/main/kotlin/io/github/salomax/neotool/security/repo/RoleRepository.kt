package io.github.salomax.neotool.security.repo

import io.github.salomax.neotool.security.model.RoleEntity
import io.micronaut.data.annotation.Query
import io.micronaut.data.annotation.Repository
import io.micronaut.data.jpa.repository.JpaRepository
import java.util.Optional

@Repository
interface RoleRepository : JpaRepository<RoleEntity, Int> {
    fun findByName(name: String): Optional<RoleEntity>

    /**
     * Find all roles by their IDs.
     */
    fun findByIdIn(ids: List<Int>): List<RoleEntity>

    /**
     * Find permission IDs for a role via the role_permissions join table.
     * Uses native query because role_permissions is a join table without an entity.
     */
    @Query(
        value = """
        SELECT permission_id FROM security.role_permissions
        WHERE role_id = :roleId
        """,
        nativeQuery = true,
    )
    fun findPermissionIdsByRoleId(roleId: Int): List<Int>

    /**
     * Find all unique permission IDs for multiple roles via the role_permissions join table.
     * Uses native query for batch loading to avoid N+1 queries.
     * Returns distinct permission IDs that belong to any of the given roles.
     */
    @Query(
        value = """
        SELECT DISTINCT permission_id FROM security.role_permissions
        WHERE role_id IN (:roleIds)
        """,
        nativeQuery = true,
    )
    fun findPermissionIdsByRoleIds(roleIds: List<Int>): List<Int>

    /**
     * Find all roles with cursor-based pagination, ordered alphabetically by name ascending.
     *
     * @param first Maximum number of results to return
     * @param after Cursor (integer ID) to start after (exclusive)
     * @return List of roles ordered by name ascending
     */
    @Query(
        value = """
        SELECT * FROM security.roles
        WHERE (:after IS NULL OR id > :after)
        ORDER BY name ASC, id ASC
        LIMIT :first
        """,
        nativeQuery = true,
    )
    fun findAll(
        first: Int,
        after: Int?,
    ): List<RoleEntity>

    /**
     * Search roles by name with cursor-based pagination.
     * Performs case-insensitive partial matching on name field.
     * Results are ordered alphabetically by name ascending.
     *
     * @param query Search query (partial match, case-insensitive)
     * @param first Maximum number of results to return
     * @param after Cursor (integer ID) to start after (exclusive)
     * @return List of matching roles ordered by name ascending
     */
    @Query(
        value = """
        SELECT * FROM security.roles
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
        after: Int?,
    ): List<RoleEntity>

    /**
     * Check if a role has any user assignments.
     * Queries the role_assignments table to determine if any users have this role assigned.
     *
     * @param roleId The role ID to check
     * @return true if the role has user assignments, false otherwise
     */
    @Query(
        value = """
        SELECT EXISTS(
            SELECT 1 FROM security.role_assignments
            WHERE role_id = :roleId
        )
        """,
        nativeQuery = true,
    )
    fun hasUserAssignments(roleId: Int): Boolean

    /**
     * Check if a role has any group assignments.
     * Queries the group_role_assignments table to determine if any groups have this role assigned.
     *
     * @param roleId The role ID to check
     * @return true if the role has group assignments, false otherwise
     */
    @Query(
        value = """
        SELECT EXISTS(
            SELECT 1 FROM security.group_role_assignments
            WHERE role_id = :roleId
        )
        """,
        nativeQuery = true,
    )
    fun hasGroupAssignments(roleId: Int): Boolean

    /**
     * Check if a role has any permissions assigned.
     * Queries the role_permissions join table to determine if any permissions are associated with this role.
     *
     * @param roleId The role ID to check
     * @return true if the role has permissions assigned, false otherwise
     */
    @Query(
        value = """
        SELECT EXISTS(
            SELECT 1 FROM security.role_permissions
            WHERE role_id = :roleId
        )
        """,
        nativeQuery = true,
    )
    fun hasPermissions(roleId: Int): Boolean

    /**
     * Assign a permission to a role by inserting into the role_permissions join table.
     *
     * @param roleId The role ID
     * @param permissionId The permission ID
     */
    @Query(
        value = """
        INSERT INTO security.role_permissions (role_id, permission_id)
        VALUES (:roleId, :permissionId)
        ON CONFLICT DO NOTHING
        """,
        nativeQuery = true,
    )
    fun assignPermissionToRole(
        roleId: Int,
        permissionId: Int,
    )

    /**
     * Remove a permission from a role by deleting from the role_permissions join table.
     *
     * @param roleId The role ID
     * @param permissionId The permission ID
     */
    @Query(
        value = """
        DELETE FROM security.role_permissions
        WHERE role_id = :roleId AND permission_id = :permissionId
        """,
        nativeQuery = true,
    )
    fun removePermissionFromRole(
        roleId: Int,
        permissionId: Int,
    )

    /**
     * Check if a specific permission is assigned to a role.
     *
     * @param roleId The role ID to check
     * @param permissionId The permission ID to check
     * @return true if the permission is assigned to the role, false otherwise
     */
    @Query(
        value = """
        SELECT EXISTS(
            SELECT 1 FROM security.role_permissions
            WHERE role_id = :roleId AND permission_id = :permissionId
        )
        """,
        nativeQuery = true,
    )
    fun isPermissionAssignedToRole(
        roleId: Int,
        permissionId: Int,
    ): Boolean

    /**
     * Find all role IDs that have a specific permission assigned.
     * Uses native query to query the role_permissions join table.
     *
     * @param permissionId The permission ID
     * @return List of role IDs that have this permission
     */
    @Query(
        value = """
        SELECT role_id FROM security.role_permissions
        WHERE permission_id = :permissionId
        """,
        nativeQuery = true,
    )
    fun findRoleIdsByPermissionId(permissionId: Int): List<Int>
}
