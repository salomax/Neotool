package io.github.salomax.neotool.security.repo

import io.github.salomax.neotool.security.model.PermissionEntity
import io.micronaut.data.annotation.Query
import io.micronaut.data.annotation.Repository
import io.micronaut.data.jpa.repository.JpaRepository
import java.util.Optional

@Repository
interface PermissionRepository : JpaRepository<PermissionEntity, Int> {
    fun findByName(name: String): Optional<PermissionEntity>

    /**
     * Find all permissions by their IDs.
     */
    fun findByIdIn(ids: List<Int>): List<PermissionEntity>

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
        roleIds: List<Int>,
    ): Boolean
}
