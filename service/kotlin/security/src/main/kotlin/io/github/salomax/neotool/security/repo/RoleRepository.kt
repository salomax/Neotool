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
}
