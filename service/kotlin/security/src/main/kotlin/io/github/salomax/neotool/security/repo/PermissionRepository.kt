package io.github.salomax.neotool.security.repo

import io.github.salomax.neotool.security.model.PermissionEntity
import io.micronaut.data.annotation.Query
import io.micronaut.data.annotation.Repository
import io.micronaut.data.jpa.repository.JpaRepository
import java.util.Optional
import java.util.UUID

@Repository
interface PermissionRepository : JpaRepository<PermissionEntity, UUID> {
    fun findByName(name: String): Optional<PermissionEntity>

    /**
     * Find all permissions by their IDs.
     */
    fun findByIdIn(ids: List<UUID>): List<PermissionEntity>

    /**
     * Find all permissions assigned to a role via the role_permissions join table.
     * Uses a join query to efficiently load permissions in a single database query.
     *
     * @param roleId The role ID
     * @return List of permissions assigned to the role, ordered by name ascending
     */
    @Query(
        value = """
        SELECT p.* FROM security.permissions p
        INNER JOIN security.role_permissions rp ON p.id = rp.permission_id
        WHERE rp.role_id = :roleId
        ORDER BY p.name ASC, p.id ASC
        """,
        nativeQuery = true,
    )
    fun findByRoleId(roleId: UUID): List<PermissionEntity>

    /**
     * Check if a permission with the given name exists for any of the given role IDs.
     * This is optimized to check permission existence without loading all permissions.
     * Uses a join query to check if the permission exists in the role_permissions table.
     */
    @Query(
        value = """
        SELECT EXISTS(
            SELECT 1 FROM security.role_permissions rp
            INNER JOIN security.permissions p ON rp.permission_id = p.id
            WHERE rp.role_id IN (:roleIds)
            AND p.name = :permissionName
        )
        """,
        nativeQuery = true,
    )
    fun existsPermissionForRoles(
        permissionName: String,
        roleIds: List<UUID>,
    ): Boolean

    /**
     * Find all permissions with cursor-based pagination, ordered alphabetically by name ascending.
     *
     * @param first Maximum number of results to return
     * @param after Cursor (UUID ID) to start after (exclusive)
     * @return List of permissions ordered by name ascending
     */
    @Query(
        value = """
        SELECT * FROM security.permissions
        WHERE (CAST(:after AS UUID) IS NULL OR id > CAST(:after AS UUID))
        ORDER BY name ASC, id ASC
        LIMIT :first
        """,
        nativeQuery = true,
    )
    fun findAll(
        first: Int,
        after: UUID?,
    ): List<PermissionEntity>

    /**
     * Search permissions by name with cursor-based pagination.
     * Performs case-insensitive partial matching on name field.
     * Results are ordered alphabetically by name ascending.
     *
     * @param query Search query (partial match, case-insensitive)
     * @param first Maximum number of results to return
     * @param after Cursor (UUID ID) to start after (exclusive)
     * @return List of matching permissions ordered by name ascending
     */
    @Query(
        value = """
        SELECT * FROM security.permissions
        WHERE LOWER(name) LIKE LOWER(CONCAT('%', :query, '%'))
        AND (CAST(:after AS UUID) IS NULL OR id > CAST(:after AS UUID))
        ORDER BY name ASC, id ASC
        LIMIT :first
        """,
        nativeQuery = true,
    )
    fun searchByName(
        query: String,
        first: Int,
        after: UUID?,
    ): List<PermissionEntity>
}
