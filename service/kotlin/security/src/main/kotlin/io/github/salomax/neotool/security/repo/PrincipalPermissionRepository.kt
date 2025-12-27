package io.github.salomax.neotool.security.repo

import io.github.salomax.neotool.security.model.PrincipalPermissionEntity
import io.micronaut.data.annotation.Query
import io.micronaut.data.annotation.Repository
import io.micronaut.data.jpa.repository.JpaRepository
import java.util.UUID

@Repository
interface PrincipalPermissionRepository : JpaRepository<PrincipalPermissionEntity, UUID> {
    /**
     * Find all permissions assigned to a principal.
     */
    @Query(
        value = """
        SELECT * FROM security.principal_permissions
        WHERE principal_id = :principalId
        """,
        nativeQuery = true,
    )
    fun findByPrincipalId(principalId: UUID): List<PrincipalPermissionEntity>

    /**
     * Check if a principal has a specific permission with null resource pattern.
     */
    @Query(
        value = """
        SELECT EXISTS(
            SELECT 1 FROM security.principal_permissions
            WHERE principal_id = :principalId
            AND permission_id = :permissionId
            AND resource_pattern IS NULL
        )
        """,
        nativeQuery = true,
    )
    fun existsByPrincipalIdAndPermissionIdWithNullPattern(
        principalId: UUID,
        permissionId: UUID,
    ): Boolean

    /**
     * Check if a principal has a specific permission with a specific resource pattern.
     */
    @Query(
        value = """
        SELECT EXISTS(
            SELECT 1 FROM security.principal_permissions
            WHERE principal_id = :principalId
            AND permission_id = :permissionId
            AND resource_pattern = :resourcePattern
        )
        """,
        nativeQuery = true,
    )
    fun existsByPrincipalIdAndPermissionIdWithPattern(
        principalId: UUID,
        permissionId: UUID,
        resourcePattern: String,
    ): Boolean
}
