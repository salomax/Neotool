package io.github.salomax.neotool.security.service

import io.github.salomax.neotool.common.graphql.pagination.CompositeCursor
import io.github.salomax.neotool.common.graphql.pagination.Connection
import io.github.salomax.neotool.common.graphql.pagination.ConnectionBuilder
import io.github.salomax.neotool.common.graphql.pagination.CursorEncoder
import io.github.salomax.neotool.common.graphql.pagination.OrderDirection
import io.github.salomax.neotool.common.graphql.pagination.PaginationConstants
import io.github.salomax.neotool.security.domain.RoleManagement
import io.github.salomax.neotool.security.domain.rbac.Permission
import io.github.salomax.neotool.security.domain.rbac.Role
import io.github.salomax.neotool.security.repo.PermissionRepository
import io.github.salomax.neotool.security.repo.RoleRepository
import io.github.salomax.neotool.security.repo.RoleRepositoryCustom
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
    private val roleSearchRepository: RoleRepositoryCustom,
    private val permissionRepository: PermissionRepository,
) {
    private val logger = KotlinLogging.logger {}

    /**
     * Get a role by ID.
     *
     * @param roleId The ID of the role
     * @return The role domain object, or null if not found
     */
    fun getRoleById(roleId: Int): Role? {
        return roleRepository.findById(roleId)
            .map { it.toDomain() }
            .orElse(null)
    }

    /**
     * Search roles by name with cursor-based pagination.
     * Unified method that handles both list (no query) and search (with query) operations.
     * When query is null or empty, returns all roles. When query is provided, returns filtered roles.
     * totalCount is always calculated: total count of all roles when query is empty, total count of filtered roles when query is provided.
     *
     * @param query Optional search query (partial match, case-insensitive). If null or empty, returns all roles.
     * @param first Maximum number of results to return (default: 20, max: 100)
     * @param after Cursor string for pagination (base64-encoded Int or composite cursor)
     * @param orderBy Optional list of order by specifications. If null or empty, defaults to NAME ASC, ID ASC. If empty array, defaults to ID ASC.
     * @return Connection containing paginated roles with totalCount
     */
    fun searchRoles(
        query: String?,
        first: Int = PaginationConstants.DEFAULT_PAGE_SIZE,
        after: String?,
        orderBy: List<RoleOrderBy>? = null,
    ): Connection<Role> {
        val pageSize =
            minOf(first, PaginationConstants.MAX_PAGE_SIZE)

        // Determine effective orderBy: default to current sort if null/empty, fallback to ID ASC if empty array
        val effectiveOrderBy =
            when {
                orderBy == null ->
                    listOf(
                        RoleOrderBy(RoleOrderField.NAME, OrderDirection.ASC),
                        RoleOrderBy(RoleOrderField.ID, OrderDirection.ASC),
                    )
                orderBy.isEmpty() -> listOf(RoleOrderBy(RoleOrderField.ID, OrderDirection.ASC))
                else -> {
                    // Ensure ID is always last for deterministic ordering
                    val withoutId = orderBy.filter { it.field != RoleOrderField.ID }
                    withoutId + RoleOrderBy(RoleOrderField.ID, OrderDirection.ASC)
                }
            }

        // Decode cursor - try composite first, fallback to Int for backward compatibility
        val afterCompositeCursor: CompositeCursor? =
            after?.let {
                try {
                    CursorEncoder.decodeCompositeCursorToInt(it)
                } catch (e: Exception) {
                    // Try legacy Int cursor for backward compatibility
                    try {
                        val intId = CursorEncoder.decodeCursorToInt(it)
                        // Convert legacy cursor to composite cursor with id only
                        CompositeCursor(
                            fieldValues = emptyMap(),
                            id = intId.toString(),
                        )
                    } catch (e2: Exception) {
                        throw IllegalArgumentException("Invalid cursor: $after", e2)
                    }
                }
            }

        // Normalize query: convert empty string to null
        val normalizedQuery = if (query.isNullOrBlank()) null else query

        // Query one extra to check for more results
        val entities =
            roleSearchRepository.searchByName(normalizedQuery, pageSize + 1, afterCompositeCursor, effectiveOrderBy)
        val hasMore = entities.size > pageSize

        // Always get total count (all roles if query is null, filtered roles if query is provided)
        val totalCount = roleSearchRepository.countByName(normalizedQuery)

        // Take only requested number and convert to domain
        val roles =
            entities
                .take(pageSize)
                .map { it.toDomain() }

        // Build connection with composite cursor encoding
        return ConnectionBuilder.buildConnection(
            items = roles,
            hasMore = hasMore,
            encodeCursor = { role ->
                val id = role.id ?: throw IllegalArgumentException("Role must have a non-null ID")
                // Build field values map from orderBy
                val fieldValues =
                    effectiveOrderBy
                        .filter { it.field != RoleOrderField.ID }
                        .associate { orderBy ->
                            val fieldName =
                                when (orderBy.field) {
                                    RoleOrderField.NAME -> "name"
                                    RoleOrderField.ID -> throw IllegalStateException("ID should not be in fieldValues")
                                }
                            val fieldValue =
                                when (orderBy.field) {
                                    RoleOrderField.NAME -> role.name
                                    RoleOrderField.ID -> throw IllegalStateException("ID should not be in fieldValues")
                                }
                            fieldName to fieldValue
                        }
                CursorEncoder.encodeCompositeCursor(fieldValues, id)
            },
            totalCount = totalCount,
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
     * Batch list all permissions assigned to multiple roles.
     * Optimized to avoid N+1 queries.
     *
     * @param roleIds List of role IDs
     * @return Map of role ID to list of permissions
     */
    fun listRolePermissionsBatch(roleIds: List<Int>): Map<Int, List<Permission>> {
        if (roleIds.isEmpty()) {
            return emptyMap()
        }

        // Get all permission IDs for all roles
        val allPermissionIds = roleRepository.findPermissionIdsByRoleIds(roleIds)

        // Batch load all permissions
        val allPermissions =
            if (allPermissionIds.isNotEmpty()) {
                permissionRepository.findByIdIn(allPermissionIds.distinct()).map { it.toDomain() }
                    .associateBy { it.id!! }
            } else {
                emptyMap()
            }

        // For each role, get its permission IDs and map to Permission objects
        val result = mutableMapOf<Int, List<Permission>>()
        for (roleId in roleIds) {
            val permissionIds = roleRepository.findPermissionIdsByRoleId(roleId)
            val permissions = permissionIds.mapNotNull { permissionId -> allPermissions[permissionId] }
            result[roleId] = permissions
        }

        return result
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
