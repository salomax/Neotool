package io.github.salomax.neotool.security.graphql.resolver

import io.github.salomax.neotool.common.graphql.pagination.PaginationConstants
import io.github.salomax.neotool.security.graphql.dto.PermissionConnectionDTO
import io.github.salomax.neotool.security.graphql.mapper.PermissionManagementMapper
import io.github.salomax.neotool.security.service.PermissionManagementService
import jakarta.inject.Singleton
import mu.KotlinLogging

/**
 * GraphQL resolver for permission management operations.
 * Provides queries for listing and searching permissions,
 * and relationship resolver for Permission.roles.
 */
@Singleton
class PermissionManagementResolver(
    private val permissionManagementService: PermissionManagementService,
    private val mapper: PermissionManagementMapper,
) {
    private val logger = KotlinLogging.logger {}

    /**
     * List all permissions with optional pagination and search.
     *
     * @param first Maximum number of results (default: 20, min: 1, max: 100)
     * @param after Cursor for pagination
     * @param query Optional search query
     * @return PermissionConnectionDTO
     * @throws IllegalArgumentException if first is invalid (< 1 or > 100)
     * @throws Exception if service fails (propagated as GraphQL error)
     */
    fun permissions(
        first: Int?,
        after: String?,
        query: String?,
    ): PermissionConnectionDTO {
        // Validate pagination parameter
        val pageSize =
            when {
                first == null -> PaginationConstants.DEFAULT_PAGE_SIZE
                first < 1 -> throw IllegalArgumentException("Parameter 'first' must be at least 1, got: $first")
                first > PaginationConstants.MAX_PAGE_SIZE ->
                    throw IllegalArgumentException(
                        "Parameter 'first' must be at most ${PaginationConstants.MAX_PAGE_SIZE}, got: $first",
                    )
                else -> first
            }

        // Call service - propagate any exceptions (will be converted to GraphQL errors)
        val connection =
            if (query != null && query.isNotBlank()) {
                permissionManagementService
                    .searchPermissions(query, pageSize, after)
            } else {
                permissionManagementService
                    .listPermissions(pageSize, after)
            }
        return mapper.toPermissionConnectionDTO(connection)
    }

    /**
     * Resolve Permission.roles relationship.
     * Returns all roles that have this permission assigned.
     *
     * @param permissionId The permission ID
     * @return List of RoleDTO
     * @throws IllegalArgumentException if permission ID is invalid
     * @throws Exception if service fails (propagated as GraphQL error)
     */
    fun resolvePermissionRoles(permissionId: String): List<io.github.salomax.neotool.security.graphql.dto.RoleDTO> {
        // Validate and convert permission ID - throw IllegalArgumentException for invalid input
        val permissionIdInt =
            try {
                mapper.toPermissionId(permissionId)
            } catch (e: IllegalArgumentException) {
                throw IllegalArgumentException("Invalid permission ID format: $permissionId", e)
            }

        // Use service layer instead of direct repository access
        val roles = permissionManagementService.getPermissionRoles(permissionIdInt)

        // Convert domain objects to DTOs
        return roles.map { role ->
            io.github.salomax.neotool.security.graphql.dto.RoleDTO(
                id = role.id?.toString() ?: throw IllegalArgumentException("Role must have an ID"),
                name = role.name,
            )
        }
    }

    /**
     * Batch resolve Permission.roles relationship for multiple permissions.
     * Returns a map of permission ID to list of roles that have that permission assigned.
     * Optimized to avoid N+1 queries.
     * Invalid IDs are filtered out and not included in the result.
     *
     * @param permissionIds List of permission IDs
     * @return Map of permission ID to list of RoleDTO (only valid IDs included)
     * @throws Exception if service fails (propagated as GraphQL error)
     */
    fun resolvePermissionRolesBatch(
        permissionIds: List<String>,
    ): Map<String, List<io.github.salomax.neotool.security.graphql.dto.RoleDTO>> {
        if (permissionIds.isEmpty()) {
            return emptyMap()
        }

        // Parse valid permission IDs while preserving order and mapping original string to int
        // Invalid IDs are logged but included in result with empty list
        val validPermissionIdMap = mutableMapOf<String, Int>()
        val permissionIdInts =
            permissionIds.mapNotNull { permissionId ->
                try {
                    val permissionIdInt = mapper.toPermissionId(permissionId)
                    validPermissionIdMap[permissionId] = permissionIdInt
                    permissionIdInt
                } catch (e: IllegalArgumentException) {
                    logger.warn { "Invalid permission ID in batch request: $permissionId" }
                    null
                }
            }

        // Use service layer to batch load permission roles (only for valid IDs)
        val permissionRolesMap =
            if (permissionIdInts.isNotEmpty()) {
                permissionManagementService.getPermissionRolesBatch(permissionIdInts)
            } else {
                emptyMap()
            }

        // Convert domain objects to DTOs and build result map preserving order of original input
        // Filter out invalid IDs - only include valid IDs in the result
        val result =
            linkedMapOf<String, List<io.github.salomax.neotool.security.graphql.dto.RoleDTO>>()
        for (permissionId in permissionIds) {
            val permissionIdInt = validPermissionIdMap[permissionId]
            if (permissionIdInt != null) {
                val roles = permissionRolesMap[permissionIdInt] ?: emptyList()
                val roleDTOs =
                    roles.map { role ->
                        io.github.salomax.neotool.security.graphql.dto.RoleDTO(
                            id = role.id?.toString() ?: throw IllegalArgumentException("Role must have an ID"),
                            name = role.name,
                        )
                    }
                result[permissionId] = roleDTOs
            }
            // Invalid IDs are filtered out - not included in result
        }

        return result
    }
}
