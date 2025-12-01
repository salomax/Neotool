package io.github.salomax.neotool.security.graphql.resolver

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
     * List all roles with optional pagination and search.
     */
    fun roles(
        first: Int?,
        after: String?,
        query: String?,
    ): RoleConnectionDTO {
        return try {
            val pageSize = first ?: 20
            val connection =
                if (query != null && query.isNotBlank()) {
                    roleManagementService.searchRoles(query, pageSize, after)
                } else {
                    roleManagementService.listRoles(pageSize, after)
                }
            mapper.toRoleConnectionDTO(connection)
        } catch (e: Exception) {
            logger.error(e) { "Error listing roles" }
            throw e
        }
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
     */
    fun resolveRolePermissions(roleId: String): List<io.github.salomax.neotool.security.graphql.dto.PermissionDTO> {
        return try {
            val roleIdInt = mapper.toRoleId(roleId)
            val permissions = roleManagementService.listRolePermissions(roleIdInt)
            mapper.toPermissionDTOList(permissions)
        } catch (e: Exception) {
            logger.error(e) { "Error resolving role permissions for role: $roleId" }
            emptyList()
        }
    }
}
