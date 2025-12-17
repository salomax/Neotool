package io.github.salomax.neotool.security.service

import io.github.salomax.neotool.common.graphql.pagination.Connection
import io.github.salomax.neotool.common.graphql.pagination.ConnectionBuilder
import io.github.salomax.neotool.common.graphql.pagination.CursorEncoder
import io.github.salomax.neotool.common.graphql.pagination.PaginationConstants
import io.github.salomax.neotool.security.domain.rbac.Permission
import io.github.salomax.neotool.security.domain.rbac.Role
import io.github.salomax.neotool.security.repo.PermissionRepository
import io.github.salomax.neotool.security.repo.RoleRepository
import jakarta.inject.Singleton
import mu.KotlinLogging
import java.util.UUID

/**
 * Service for managing permissions.
 * Provides read-only operations for listing and searching permissions.
 */
@Singleton
class PermissionManagementService(
    private val permissionRepository: PermissionRepository,
    private val roleRepository: RoleRepository,
) {
    private val logger = KotlinLogging.logger {}

    /**
     * List all permissions with cursor-based pagination.
     *
     * @param first Maximum number of results to return (default: 20, max: 100)
     * @param after Cursor string for pagination (base64-encoded UUID)
     * @return Connection containing paginated permissions
     */
    fun listPermissions(
        first: Int = PaginationConstants.DEFAULT_PAGE_SIZE,
        after: String?,
    ): Connection<Permission> {
        val pageSize =
            minOf(first, PaginationConstants.MAX_PAGE_SIZE)

        val afterCursor =
            try {
                after?.let { CursorEncoder.decodeCursorToUuid(it) }
            } catch (e: Exception) {
                throw IllegalArgumentException("Invalid cursor: $after", e)
            }

        // Query one extra to check for more results
        val entities =
            permissionRepository.findAll(pageSize + 1, afterCursor)
        val hasMore = entities.size > pageSize

        // Take only requested number and convert to domain
        val permissions =
            entities
                .take(pageSize)
                .map { it.toDomain() }

        return ConnectionBuilder.buildConnectionWithUuid(
            items = permissions,
            hasMore = hasMore,
            getId = { it.id },
        )
    }

    /**
     * Search permissions by name with cursor-based pagination.
     *
     * @param query Search query (partial match, case-insensitive)
     * @param first Maximum number of results to return (default: 20, max: 100)
     * @param after Cursor string for pagination (base64-encoded UUID)
     * @return Connection containing paginated matching permissions
     */
    fun searchPermissions(
        query: String,
        first: Int = PaginationConstants.DEFAULT_PAGE_SIZE,
        after: String?,
    ): Connection<Permission> {
        val pageSize =
            minOf(first, PaginationConstants.MAX_PAGE_SIZE)

        val afterCursor =
            try {
                after?.let { CursorEncoder.decodeCursorToUuid(it) }
            } catch (e: Exception) {
                throw IllegalArgumentException("Invalid cursor: $after", e)
            }

        // Query one extra to check for more results
        val entities =
            permissionRepository.searchByName(query, pageSize + 1, afterCursor)
        val hasMore = entities.size > pageSize

        // Take only requested number and convert to domain
        val permissions =
            entities
                .take(pageSize)
                .map { it.toDomain() }

        return ConnectionBuilder.buildConnectionWithUuid(
            items = permissions,
            hasMore = hasMore,
            getId = { it.id },
        )
    }

    /**
     * Get all roles that have a specific permission assigned.
     *
     * @param permissionId The ID of the permission
     * @return List of Role domain objects that have this permission assigned
     */
    fun getPermissionRoles(permissionId: UUID): List<Role> {
        val roleIds = roleRepository.findRoleIdsByPermissionId(permissionId)

        if (roleIds.isEmpty()) {
            return emptyList()
        }

        val roles = roleRepository.findByIdIn(roleIds)
        return roles.mapNotNull { it.toDomain() }
            .filter { it.id != null }
    }

    /**
     * Batch get all roles that have any of the given permissions assigned.
     * Optimized to avoid N+1 queries.
     *
     * @param permissionIds List of permission IDs
     * @return Map of permission ID to list of roles
     */
    fun getPermissionRolesBatch(permissionIds: List<UUID>): Map<UUID, List<Role>> {
        if (permissionIds.isEmpty()) {
            return emptyMap()
        }

        // For each permission, get its role IDs
        // Note: We query each permission individually, but at least we batch load the roles
        val permissionRoleIdsMap = mutableMapOf<UUID, List<UUID>>()
        for (permissionId in permissionIds) {
            val roleIds = roleRepository.findRoleIdsByPermissionId(permissionId)
            permissionRoleIdsMap[permissionId] = roleIds
        }

        // Collect all unique role IDs
        val allRoleIds = permissionRoleIdsMap.values.flatten().distinct()

        // Batch load all roles
        val allRoles =
            if (allRoleIds.isNotEmpty()) {
                roleRepository.findByIdIn(allRoleIds).mapNotNull { it.toDomain() }
                    .filter { it.id != null }
                    .associateBy { it.id!! }
            } else {
                emptyMap()
            }

        // Build result map
        val result = mutableMapOf<UUID, List<Role>>()
        for (permissionId in permissionIds) {
            val roleIds = permissionRoleIdsMap[permissionId] ?: emptyList()
            val roles = roleIds.mapNotNull { roleId -> allRoles[roleId] }
            result[permissionId] = roles
        }

        return result
    }
}
