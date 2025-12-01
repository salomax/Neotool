package io.github.salomax.neotool.security.service

import io.github.salomax.neotool.common.graphql.pagination.Connection
import io.github.salomax.neotool.common.graphql.pagination.ConnectionBuilder
import io.github.salomax.neotool.common.graphql.pagination.CursorEncoder
import io.github.salomax.neotool.common.graphql.pagination.PaginationConstants
import io.github.salomax.neotool.security.domain.RoleManagement
import io.github.salomax.neotool.security.domain.rbac.Permission
import io.github.salomax.neotool.security.domain.rbac.Role
import io.github.salomax.neotool.security.repo.PermissionRepository
import io.github.salomax.neotool.security.repo.RoleRepository
import jakarta.inject.Singleton
import jakarta.transaction.Transactional
import mu.KotlinLogging
import java.time.Instant

/**
 * Service for managing roles.
 * Provides operations for listing, searching, creating, updating, deleting roles,
 * and managing role-permission relationships.
 */
@Singleton
open class RoleManagementService(
    private val roleRepository: RoleRepository,
    private val permissionRepository: PermissionRepository,
) {
    private val logger = KotlinLogging.logger {}

    /**
     * List all roles with cursor-based pagination.
     *
     * @param first Maximum number of results to return (default: 20, max: 100)
     * @param after Cursor string for pagination (base64-encoded Int)
     * @return Connection containing paginated roles
     */
    fun listRoles(
        first: Int = PaginationConstants.DEFAULT_PAGE_SIZE,
        after: String?,
    ): Connection<Role> {
        val pageSize =
            minOf(first, PaginationConstants.MAX_PAGE_SIZE)

        val afterCursor =
            try {
                after?.let { CursorEncoder.decodeCursorToInt(it) }
            } catch (e: Exception) {
                throw IllegalArgumentException("Invalid cursor: $after", e)
            }

        // Query one extra to check for more results
        val entities =
            roleRepository.findAll(pageSize + 1, afterCursor)
        val hasMore = entities.size > pageSize

        // Take only requested number and convert to domain
        val roles =
            entities
                .take(pageSize)
                .map { it.toDomain() }

        return ConnectionBuilder.buildConnectionWithInt(
            items = roles,
            hasMore = hasMore,
            getId = { it.id },
        )
    }

    /**
     * Search roles by name with cursor-based pagination.
     *
     * @param query Search query (partial match, case-insensitive)
     * @param first Maximum number of results to return (default: 20, max: 100)
     * @param after Cursor string for pagination (base64-encoded Int)
     * @return Connection containing paginated matching roles
     */
    fun searchRoles(
        query: String,
        first: Int = PaginationConstants.DEFAULT_PAGE_SIZE,
        after: String?,
    ): Connection<Role> {
        val pageSize =
            minOf(first, PaginationConstants.MAX_PAGE_SIZE)

        val afterCursor =
            try {
                after?.let { CursorEncoder.decodeCursorToInt(it) }
            } catch (e: Exception) {
                throw IllegalArgumentException("Invalid cursor: $after", e)
            }

        // Query one extra to check for more results
        val entities =
            roleRepository.searchByName(query, pageSize + 1, afterCursor)
        val hasMore = entities.size > pageSize

        // Take only requested number and convert to domain
        val roles =
            entities
                .take(pageSize)
                .map { it.toDomain() }

        return ConnectionBuilder.buildConnectionWithInt(
            items = roles,
            hasMore = hasMore,
            getId = { it.id },
        )
    }

    /**
     * Create a new role.
     *
     * @param command Create role command with name
     * @return The created role
     * @throws DataAccessException if role name already exists (database unique constraint)
     */
    @Transactional
    open fun createRole(command: RoleManagement.CreateRoleCommand): Role {
        // Create domain object
        val role =
            Role(
                name = command.name,
                createdAt = Instant.now(),
                updatedAt = Instant.now(),
            )

        // Convert to entity and save
        val entity = role.toEntity()
        val saved = roleRepository.save(entity)

        logger.info { "Role created: ${saved.name} (ID: ${saved.id})" }

        return saved.toDomain()
    }

    /**
     * Update an existing role.
     *
     * @param command Update role command with roleId and name
     * @return The updated role
     * @throws IllegalArgumentException if role not found
     * @throws DataAccessException if role name already exists (database unique constraint)
     */
    @Transactional
    open fun updateRole(command: RoleManagement.UpdateRoleCommand): Role {
        val entity =
            roleRepository
                .findById(command.roleId)
                .orElseThrow {
                    IllegalArgumentException("Role not found with ID: ${command.roleId}")
                }

        // Update entity
        entity.name = command.name
        entity.updatedAt = Instant.now()

        val saved = roleRepository.update(entity)

        logger.info { "Role updated: ${saved.name} (ID: ${saved.id})" }

        return saved.toDomain()
    }

    /**
     * Delete a role.
     *
     * @param roleId The ID of the role to delete
     * @throws IllegalArgumentException if role not found
     * @throws DataAccessException if role has dependencies (database foreign key constraint)
     */
    @Transactional
    open fun deleteRole(roleId: Int) {
        roleRepository.deleteById(roleId)

        logger.info { "Role deleted (ID: $roleId)" }
    }

    /**
     * List all permissions assigned to a role.
     *
     * @param roleId The ID of the role
     * @return List of permissions assigned to the role (empty list if role doesn't exist or has no permissions)
     */
    fun listRolePermissions(roleId: Int): List<Permission> {
        // Load permissions for the role in a single query
        val permissions = permissionRepository.findByRoleId(roleId)

        return permissions.map { it.toDomain() }
    }

    /**
     * Assign a permission to a role.
     *
     * @param command Assign permission command with roleId and permissionId
     * @return The updated role
     * @throws IllegalArgumentException if role or permission not found
     * @throws DataAccessException if foreign key constraint violation (role or permission doesn't exist)
     */
    @Transactional
    open fun assignPermissionToRole(command: RoleManagement.AssignPermissionCommand): Role {
        // Validate role and permission exist before assigning
        val role =
            roleRepository
                .findById(command.roleId)
                .orElseThrow {
                    IllegalArgumentException("Role not found with ID: ${command.roleId}")
                }
        val permission =
            permissionRepository
                .findById(command.permissionId)
                .orElseThrow {
                    IllegalArgumentException(
                        "Permission not found with ID: ${command.permissionId}",
                    )
                }

        roleRepository.assignPermissionToRole(command.roleId, command.permissionId)

        logger.info {
            "Permission '${permission.name}' assigned to role '${role.name}' " +
                "(Role ID: ${command.roleId}, Permission ID: ${command.permissionId})"
        }

        return role.toDomain()
    }

    /**
     * Remove a permission from a role.
     *
     * @param command Remove permission command with roleId and permissionId
     * @return The updated role
     * @throws IllegalArgumentException if role or permission not found
     */
    @Transactional
    open fun removePermissionFromRole(command: RoleManagement.RemovePermissionCommand): Role {
        // Validate role and permission exist before removing
        val role =
            roleRepository
                .findById(command.roleId)
                .orElseThrow {
                    IllegalArgumentException("Role not found with ID: ${command.roleId}")
                }
        val permission =
            permissionRepository
                .findById(command.permissionId)
                .orElseThrow {
                    IllegalArgumentException(
                        "Permission not found with ID: ${command.permissionId}",
                    )
                }

        // Remove permission (DELETE will silently succeed even if not assigned)
        roleRepository.removePermissionFromRole(command.roleId, command.permissionId)

        logger.info {
            "Permission '${permission.name}' removed from role '${role.name}' " +
                "(Role ID: ${command.roleId}, Permission ID: ${command.permissionId})"
        }

        return role.toDomain()
    }
}
