package io.github.salomax.neotool.security.graphql.resolver

import io.github.salomax.neotool.common.graphql.pagination.PaginationConstants
import io.github.salomax.neotool.security.graphql.dto.CreateRoleInputDTO
import io.github.salomax.neotool.security.graphql.dto.RoleConnectionDTO
import io.github.salomax.neotool.security.graphql.dto.RoleDTO
import io.github.salomax.neotool.security.graphql.dto.UpdateRoleInputDTO
import io.github.salomax.neotool.security.graphql.mapper.RoleManagementMapper
import io.github.salomax.neotool.security.service.RoleManagementService
import jakarta.inject.Singleton
import mu.KotlinLogging

/**
 * GraphQL resolver for role management operations.
 * Provides queries for listing and searching roles, CRUD mutations,
 * permission assignment/removal, and relationship resolver for Role.permissions.
 */
@Singleton
class RoleManagementResolver(
    private val roleManagementService: RoleManagementService,
    private val mapper: RoleManagementMapper,
) {
    private val logger = KotlinLogging.logger {}

    /**
     * Get a single role by ID.
     *
     * @param id The role ID
     * @return RoleDTO or null if not found
     * @throws IllegalArgumentException if role ID is invalid
     * @throws Exception if service fails (propagated as GraphQL error)
     */
    fun role(id: String): RoleDTO? {
        // Validate and convert role ID - throw IllegalArgumentException for invalid input
        val roleIdInt =
            try {
                mapper.toRoleId(id)
            } catch (e: IllegalArgumentException) {
                throw IllegalArgumentException("Invalid role ID format: $id", e)
            }

        // Use service layer instead of direct repository access
        val role = roleManagementService.getRoleById(roleIdInt)
        return role?.let { mapper.toRoleDTO(it) }
    }

    /**
     * Unified query for roles with optional pagination and search.
     * When query is omitted or empty, returns all roles (list behavior).
     * When query is provided, returns filtered roles (search behavior).
     * totalCount is always calculated.
     *
     * @param first Maximum number of results (default: 20, min: 1, max: 100)
     * @param after Cursor for pagination
     * @param query Optional search query
     * @param orderBy Optional order by specification
     * @return RoleConnectionDTO
     * @throws IllegalArgumentException if first is invalid or orderBy contains invalid fields/directions
     * @throws Exception if service fails (propagated as GraphQL error)
     */
    fun roles(
        first: Int?,
        after: String?,
        query: String?,
        orderBy: List<Map<String, Any?>>?,
    ): RoleConnectionDTO {
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

        // Validate orderBy - mapper will throw IllegalArgumentException for invalid fields/directions
        val orderByList =
            try {
                mapper.toRoleOrderByList(orderBy)
            } catch (e: IllegalArgumentException) {
                throw IllegalArgumentException("Invalid orderBy parameter: ${e.message}", e)
            }

        // Call service - propagate any exceptions (will be converted to GraphQL errors)
        val connection = roleManagementService.searchRoles(query, pageSize, after, orderByList)
        return mapper.toRoleConnectionDTO(connection)
    }

    /**
     * Create a new role.
     */
    fun createRole(input: CreateRoleInputDTO): RoleDTO {
        return try {
            val command = mapper.toCreateRoleCommand(input)
            val role = roleManagementService.createRole(command)
            mapper.toRoleDTO(role)
        } catch (e: Exception) {
            logger.error(e) { "Error creating role" }
            throw e
        }
    }

    /**
     * Update an existing role.
     */
    fun updateRole(
        roleId: String,
        input: UpdateRoleInputDTO,
    ): RoleDTO {
        return try {
            val command = mapper.toUpdateRoleCommand(roleId, input)
            val role = roleManagementService.updateRole(command)
            mapper.toRoleDTO(role)
        } catch (e: IllegalArgumentException) {
            logger.warn { "Invalid role ID or role not found: $roleId" }
            throw e
        } catch (e: Exception) {
            logger.error(e) { "Error updating role: $roleId" }
            throw e
        }
    }

    /**
     * Delete a role.
     */
    fun deleteRole(roleId: String): Boolean {
        return try {
            val roleIdInt = mapper.toRoleId(roleId)
            roleManagementService.deleteRole(roleIdInt)
            true
        } catch (e: IllegalArgumentException) {
            logger.warn { "Invalid role ID or role not found: $roleId" }
            throw e
        } catch (e: Exception) {
            logger.error(e) { "Error deleting role: $roleId" }
            throw e
        }
    }

    /**
     * Assign a permission to a role.
     */
    fun assignPermissionToRole(
        roleId: String,
        permissionId: String,
    ): RoleDTO {
        return try {
            val command =
                io.github.salomax.neotool.security.domain.RoleManagement.AssignPermissionCommand(
                    roleId = mapper.toRoleId(roleId),
                    permissionId = mapper.toPermissionId(permissionId),
                )
            val role = roleManagementService.assignPermissionToRole(command)
            mapper.toRoleDTO(role)
        } catch (e: IllegalArgumentException) {
            logger.warn { "Invalid role ID or permission ID: roleId=$roleId, permissionId=$permissionId" }
            throw e
        } catch (e: Exception) {
            logger.error(e) { "Error assigning permission to role: roleId=$roleId, permissionId=$permissionId" }
            throw e
        }
    }

    /**
     * Remove a permission from a role.
     */
    fun removePermissionFromRole(
        roleId: String,
        permissionId: String,
    ): RoleDTO {
        return try {
            val command =
                io.github.salomax.neotool.security.domain.RoleManagement.RemovePermissionCommand(
                    roleId = mapper.toRoleId(roleId),
                    permissionId = mapper.toPermissionId(permissionId),
                )
            val role = roleManagementService.removePermissionFromRole(command)
            mapper.toRoleDTO(role)
        } catch (e: IllegalArgumentException) {
            logger.warn { "Invalid role ID or permission ID: roleId=$roleId, permissionId=$permissionId" }
            throw e
        } catch (e: Exception) {
            logger.error(e) { "Error removing permission from role: roleId=$roleId, permissionId=$permissionId" }
            throw e
        }
    }

    /**
     * Resolve Role.permissions relationship.
     * Returns all permissions assigned to the role.
     *
     * @param roleId The role ID
     * @return List of PermissionDTO
     * @throws IllegalArgumentException if role ID is invalid
     * @throws Exception if service fails (propagated as GraphQL error)
     */
    fun resolveRolePermissions(roleId: String): List<io.github.salomax.neotool.security.graphql.dto.PermissionDTO> {
        // Validate and convert role ID - throw IllegalArgumentException for invalid input
        val roleIdInt =
            try {
                mapper.toRoleId(roleId)
            } catch (e: IllegalArgumentException) {
                throw IllegalArgumentException("Invalid role ID format: $roleId", e)
            }

        // Call service - propagate any exceptions (will be converted to GraphQL errors)
        val permissions = roleManagementService.listRolePermissions(roleIdInt)
        return mapper.toPermissionDTOList(permissions)
    }

    /**
     * Batch resolve Role.permissions relationship for multiple roles.
     * Returns a map of role ID to list of permissions assigned to that role.
     * Optimized to avoid N+1 queries.
     * Invalid IDs are filtered out and not included in the result.
     *
     * @param roleIds List of role IDs
     * @return Map of role ID to list of PermissionDTO (only valid IDs included)
     * @throws Exception if service fails (propagated as GraphQL error)
     */
    fun resolveRolePermissionsBatch(
        roleIds: List<String>,
    ): Map<String, List<io.github.salomax.neotool.security.graphql.dto.PermissionDTO>> {
        if (roleIds.isEmpty()) {
            return emptyMap()
        }

        // Parse valid role IDs while preserving order and mapping original string to int
        // Invalid IDs are logged but included in result with empty list
        val validRoleIdMap = mutableMapOf<String, Int>()
        val roleIdInts =
            roleIds.mapNotNull { roleId ->
                try {
                    val roleIdInt = mapper.toRoleId(roleId)
                    validRoleIdMap[roleId] = roleIdInt
                    roleIdInt
                } catch (e: IllegalArgumentException) {
                    logger.warn { "Invalid role ID in batch request: $roleId" }
                    null
                }
            }

        // Use service layer to batch load role permissions (only for valid IDs)
        val rolePermissionsMap =
            if (roleIdInts.isNotEmpty()) {
                roleManagementService.listRolePermissionsBatch(roleIdInts)
            } else {
                emptyMap()
            }

        // Convert domain objects to DTOs and build result map preserving order of original input
        // Filter out invalid IDs - only include valid IDs in the result
        val result =
            linkedMapOf<String, List<io.github.salomax.neotool.security.graphql.dto.PermissionDTO>>()
        for (roleId in roleIds) {
            val roleIdInt = validRoleIdMap[roleId]
            if (roleIdInt != null) {
                val permissions = rolePermissionsMap[roleIdInt] ?: emptyList()
                val permissionDTOs = mapper.toPermissionDTOList(permissions)
                result[roleId] = permissionDTOs
            }
            // Invalid IDs are filtered out - not included in result
        }

        return result
    }
}
