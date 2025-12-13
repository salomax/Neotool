package io.github.salomax.neotool.security.repo

import io.github.salomax.neotool.security.model.RoleEntity
import io.micronaut.data.annotation.Query
import io.micronaut.data.annotation.Repository
import io.micronaut.data.jpa.repository.JpaRepository
import java.util.Optional
import java.util.UUID

@Repository
interface RoleRepository : JpaRepository<RoleEntity, UUID> {
    fun findByName(name: String): Optional<RoleEntity>

    /**
     * Find all roles by their IDs.
     */
    fun findByIdIn(ids: List<UUID>): List<RoleEntity>

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
    fun findPermissionIdsByRoleId(roleId: UUID): List<UUID>

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
    fun findPermissionIdsByRoleIds(roleIds: List<UUID>): List<UUID>

    /**
     * Check if a role has any user assignments through groups.
     * Queries the group_role_assignments and group_memberships tables to determine if any users have this role assigned via groups.
     *
     * @param roleId The role ID to check
     * @return true if the role has user assignments through groups, false otherwise
     */
    @Query(
        value = """
        SELECT EXISTS(
            SELECT 1 
            FROM security.group_role_assignments gra
            INNER JOIN security.group_memberships gm ON gra.group_id = gm.group_id
            WHERE gra.role_id = :roleId
            AND (gra.valid_from IS NULL OR gra.valid_from <= NOW())
            AND (gra.valid_until IS NULL OR gra.valid_until >= NOW())
            AND (gm.valid_until IS NULL OR gm.valid_until >= NOW())
        )
        """,
        nativeQuery = true,
    )
    fun hasUserAssignments(roleId: UUID): Boolean

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
    fun hasGroupAssignments(roleId: UUID): Boolean

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
    fun hasPermissions(roleId: UUID): Boolean

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
        roleId: UUID,
        permissionId: UUID,
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
        roleId: UUID,
        permissionId: UUID,
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
        roleId: UUID,
        permissionId: UUID,
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
    fun findRoleIdsByPermissionId(permissionId: UUID): List<UUID>

    /**
     * Find all role IDs that have any of the given permissions assigned (batch loading).
     * Uses native query to query the role_permissions join table.
     * Returns distinct role IDs that have any of the given permissions.
     * Note: To get the mapping of permission to roles, use findRoleIdsByPermissionId for each permission
     * or process the results in the service layer.
     *
     * @param permissionIds List of permission IDs
     * @return List of distinct role IDs that have any of the given permissions
     */
    @Query(
        value = """
        SELECT DISTINCT role_id FROM security.role_permissions
        WHERE permission_id IN (:permissionIds)
        """,
        nativeQuery = true,
    )
    fun findRoleIdsByPermissionIds(permissionIds: List<UUID>): List<UUID>
}
