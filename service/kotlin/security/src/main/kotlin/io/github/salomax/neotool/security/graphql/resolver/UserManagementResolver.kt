package io.github.salomax.neotool.security.graphql.resolver

import io.github.salomax.neotool.common.graphql.pagination.PaginationConstants
import io.github.salomax.neotool.security.graphql.dto.UpdateUserInputDTO
import io.github.salomax.neotool.security.graphql.dto.UserConnectionDTO
import io.github.salomax.neotool.security.graphql.dto.UserDTO
import io.github.salomax.neotool.security.graphql.mapper.UserManagementMapper
import io.github.salomax.neotool.security.service.AuthorizationService
import io.github.salomax.neotool.security.service.UserManagementService
import jakarta.inject.Singleton
import mu.KotlinLogging
import java.util.UUID

/**
 * GraphQL resolver for user management operations.
 * Provides queries for listing and searching users, mutations for enabling/disabling users,
 * and batch relationship resolvers for User.roles, User.groups, and User.permissions.
 * Note: Single-user relationship resolvers are handled via DataLoader in SecurityWiringFactory
 * to prevent N+1 query problems.
 */
@Singleton
class UserManagementResolver(
    private val userManagementService: UserManagementService,
    private val authorizationService: AuthorizationService,
    private val mapper: UserManagementMapper,
) {
    private val logger = KotlinLogging.logger {}

    /**
     * Get a single user by ID.
     *
     * @param id The user ID
     * @return UserDTO or null if not found
     * @throws IllegalArgumentException if user ID is invalid
     * @throws Exception if service fails (propagated as GraphQL error)
     */
    fun user(id: String): UserDTO? {
        // Validate and convert user ID - throw IllegalArgumentException for invalid input
        val userIdUuid =
            try {
                mapper.toUserId(id)
            } catch (e: IllegalArgumentException) {
                throw IllegalArgumentException("Invalid user ID format: $id", e)
            }

        // Use service layer instead of direct repository access
        val user = userManagementService.getUserById(userIdUuid)
        return user?.let { mapper.toUserDTO(it) }
    }

    /**
     * Unified query for users with optional pagination and search.
     * When query is omitted or empty, returns all users (list behavior).
     * When query is provided, returns filtered users (search behavior).
     * totalCount is always calculated.
     *
     * @param first Maximum number of results (default: 20, min: 1, max: 100)
     * @param after Cursor for pagination
     * @param query Optional search query
     * @param orderBy Optional order by specification
     * @return UserConnectionDTO
     * @throws IllegalArgumentException if first is invalid or orderBy contains invalid fields/directions
     * @throws Exception if service fails (propagated as GraphQL error)
     */
    fun users(
        first: Int?,
        after: String?,
        query: String?,
        orderBy: List<Map<String, Any?>>?,
    ): UserConnectionDTO {
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
                mapper.toUserOrderByList(orderBy)
            } catch (e: IllegalArgumentException) {
                throw IllegalArgumentException("Invalid orderBy parameter: ${e.message}", e)
            }

        // Call service - propagate any exceptions (will be converted to GraphQL errors)
        val connection = userManagementService.searchUsers(query, pageSize, after, orderByList)
        return mapper.toUserConnectionDTO(connection)
    }

    /**
     * Enable a user account.
     */
    fun enableUser(userId: String): UserDTO {
        return try {
            val userIdUuid = mapper.toUserId(userId)
            val user = userManagementService.enableUser(userIdUuid)
            mapper.toUserDTO(user)
        } catch (e: IllegalArgumentException) {
            logger.warn { "Invalid user ID or user not found: $userId" }
            throw e
        } catch (e: Exception) {
            logger.error(e) { "Error enabling user: $userId" }
            throw e
        }
    }

    /**
     * Disable a user account.
     */
    fun disableUser(userId: String): UserDTO {
        return try {
            val userIdUuid = mapper.toUserId(userId)
            val user = userManagementService.disableUser(userIdUuid)
            mapper.toUserDTO(user)
        } catch (e: IllegalArgumentException) {
            logger.warn { "Invalid user ID or user not found: $userId" }
            throw e
        } catch (e: Exception) {
            logger.error(e) { "Error disabling user: $userId" }
            throw e
        }
    }

    /**
     * Resolve User.roles relationship.
     * Returns all roles assigned to the user (direct and group-inherited).
     *
     * @param userId The user ID
     * @return List of RoleDTO
     * @throws IllegalArgumentException if user ID is invalid
     * @throws Exception if service fails (propagated as GraphQL error)
     */
    fun resolveUserRoles(userId: String): List<io.github.salomax.neotool.security.graphql.dto.RoleDTO> {
        // Validate and convert user ID - throw IllegalArgumentException for invalid input
        val userIdUuid =
            try {
                UUID.fromString(userId)
            } catch (e: IllegalArgumentException) {
                throw IllegalArgumentException("Invalid user ID format: $userId", e)
            }

        // Call service - propagate any exceptions (will be converted to GraphQL errors)
        val roles = authorizationService.getUserRoles(userIdUuid)
        return roles.map { role ->
            io.github.salomax.neotool.security.graphql.dto.RoleDTO(
                id = role.id?.toString() ?: throw IllegalArgumentException("Role must have an ID"),
                name = role.name,
            )
        }
    }

    /**
     * Resolve User.permissions relationship.
     * Returns all effective permissions for the user (direct and group-inherited).
     *
     * @param userId The user ID
     * @return List of PermissionDTO
     * @throws IllegalArgumentException if user ID is invalid
     * @throws Exception if service fails (propagated as GraphQL error)
     */
    fun resolveUserPermissions(userId: String): List<io.github.salomax.neotool.security.graphql.dto.PermissionDTO> {
        // Validate and convert user ID - throw IllegalArgumentException for invalid input
        val userIdUuid =
            try {
                UUID.fromString(userId)
            } catch (e: IllegalArgumentException) {
                throw IllegalArgumentException("Invalid user ID format: $userId", e)
            }

        // Call service - propagate any exceptions (will be converted to GraphQL errors)
        val permissions = authorizationService.getUserPermissions(userIdUuid)
        return permissions.map { permission ->
            io.github.salomax.neotool.security.graphql.dto.PermissionDTO(
                id = permission.id?.toString() ?: throw IllegalArgumentException("Permission must have an ID"),
                name = permission.name,
            )
        }
    }

    /**
     * Assign a group to a user.
     */
    fun assignGroupToUser(
        userId: String,
        groupId: String,
    ): UserDTO {
        return try {
            val command =
                io.github.salomax.neotool.security.domain.UserManagement.AssignGroupToUserCommand(
                    userId = mapper.toUserId(userId),
                    groupId = mapper.toGroupId(groupId),
                )
            val user = userManagementService.assignGroupToUser(command)
            mapper.toUserDTO(user)
        } catch (e: IllegalArgumentException) {
            logger.warn { "Invalid user ID or group ID: userId=$userId, groupId=$groupId" }
            throw e
        } catch (e: Exception) {
            logger.error(e) { "Error assigning group to user: userId=$userId, groupId=$groupId" }
            throw e
        }
    }

    /**
     * Remove a group from a user.
     */
    fun removeGroupFromUser(
        userId: String,
        groupId: String,
    ): UserDTO {
        return try {
            val command =
                io.github.salomax.neotool.security.domain.UserManagement.RemoveGroupFromUserCommand(
                    userId = mapper.toUserId(userId),
                    groupId = mapper.toGroupId(groupId),
                )
            val user = userManagementService.removeGroupFromUser(command)
            mapper.toUserDTO(user)
        } catch (e: IllegalArgumentException) {
            logger.warn { "Invalid user ID or group ID: userId=$userId, groupId=$groupId" }
            throw e
        } catch (e: Exception) {
            logger.error(e) { "Error removing group from user: userId=$userId, groupId=$groupId" }
            throw e
        }
    }

    /**
     * Update a user's profile fields.
     * Currently only supports updating displayName; email is immutable.
     */
    fun updateUser(
        userId: String,
        input: UpdateUserInputDTO,
    ): UserDTO {
        return try {
            val command = mapper.toUpdateUserCommand(userId, input)
            val user = userManagementService.updateUser(command)
            mapper.toUserDTO(user)
        } catch (e: IllegalArgumentException) {
            logger.warn { "Invalid user ID or user not found: $userId" }
            throw e
        } catch (e: Exception) {
            logger.error(e) { "Error updating user: $userId" }
            throw e
        }
    }

    /**
     * Batch resolve User.roles relationship for multiple users.
     * Returns a map of user ID to list of roles assigned to that user.
     * Optimized to avoid N+1 queries.
     * Invalid IDs are filtered out and not included in the result.
     *
     * @param userIds List of user IDs
     * @return Map of user ID to list of RoleDTO (only valid IDs included)
     * @throws Exception if service fails (propagated as GraphQL error)
     */
    fun resolveUserRolesBatch(
        userIds: List<String>,
    ): Map<String, List<io.github.salomax.neotool.security.graphql.dto.RoleDTO>> {
        if (userIds.isEmpty()) {
            return emptyMap()
        }

        // Parse valid user IDs while preserving order and mapping original string to UUID
        // Invalid IDs are logged but included in result with empty list
        val validUserIdMap = mutableMapOf<String, UUID>()
        val userIdUuids =
            userIds.mapNotNull { userId ->
                try {
                    val userIdUuid = UUID.fromString(userId)
                    validUserIdMap[userId] = userIdUuid
                    userIdUuid
                } catch (e: IllegalArgumentException) {
                    logger.warn { "Invalid user ID in batch request: $userId" }
                    null
                }
            }

        // Use service layer to batch load user roles (only for valid IDs)
        val userRolesMap =
            if (userIdUuids.isNotEmpty()) {
                authorizationService.getUserRolesBatch(userIdUuids)
            } else {
                emptyMap()
            }

        // Convert domain objects to DTOs and build result map preserving order of original input
        // Filter out invalid IDs - only include valid IDs in the result
        val result =
            linkedMapOf<String, List<io.github.salomax.neotool.security.graphql.dto.RoleDTO>>()
        for (userId in userIds) {
            val userIdUuid = validUserIdMap[userId]
            if (userIdUuid != null) {
                val roles = userRolesMap[userIdUuid] ?: emptyList()
                val roleDTOs =
                    roles.map { role ->
                        io.github.salomax.neotool.security.graphql.dto.RoleDTO(
                            id = role.id?.toString() ?: throw IllegalArgumentException("Role must have an ID"),
                            name = role.name,
                        )
                    }
                result[userId] = roleDTOs
            }
            // Invalid IDs are filtered out - not included in result
        }

        return result
    }

    /**
     * Batch resolve User.groups relationship for multiple users.
     * Returns a map of user ID to list of groups the user belongs to.
     * Optimized to avoid N+1 queries.
     * Invalid IDs are filtered out and not included in the result.
     *
     * @param userIds List of user IDs
     * @return Map of user ID to list of GroupDTO (only valid IDs included)
     * @throws Exception if service fails (propagated as GraphQL error)
     */
    fun resolveUserGroupsBatch(
        userIds: List<String>,
    ): Map<String, List<io.github.salomax.neotool.security.graphql.dto.GroupDTO>> {
        if (userIds.isEmpty()) {
            return emptyMap()
        }

        // Parse valid user IDs while preserving order and mapping original string to UUID
        // Invalid IDs are logged but included in result with empty list
        val validUserIdMap = mutableMapOf<String, UUID>()
        val userIdUuids =
            userIds.mapNotNull { userId ->
                try {
                    val userIdUuid = UUID.fromString(userId)
                    validUserIdMap[userId] = userIdUuid
                    userIdUuid
                } catch (e: IllegalArgumentException) {
                    logger.warn { "Invalid user ID in batch request: $userId" }
                    null
                }
            }

        // Use service layer to batch load user groups (only for valid IDs)
        val userGroupsMap =
            if (userIdUuids.isNotEmpty()) {
                userManagementService.getUserGroupsBatch(userIdUuids)
            } else {
                emptyMap()
            }

        // Convert domain objects to DTOs and build result map preserving order of original input
        // Filter out invalid IDs - only include valid IDs in the result
        val result =
            linkedMapOf<String, List<io.github.salomax.neotool.security.graphql.dto.GroupDTO>>()
        for (userId in userIds) {
            val userIdUuid = validUserIdMap[userId]
            if (userIdUuid != null) {
                val groups = userGroupsMap[userIdUuid] ?: emptyList()
                val groupDTOs =
                    groups.map { group ->
                        io.github.salomax.neotool.security.graphql.dto.GroupDTO(
                            id = group.id?.toString() ?: throw IllegalArgumentException("Group must have an ID"),
                            name = group.name,
                            description = group.description,
                        )
                    }
                result[userId] = groupDTOs
            }
            // Invalid IDs are filtered out - not included in result
        }

        return result
    }

    /**
     * Batch resolve User.permissions relationship for multiple users.
     * Returns a map of user ID to list of effective permissions for that user.
     * Optimized to avoid N+1 queries.
     * Invalid IDs are filtered out and not included in the result.
     *
     * @param userIds List of user IDs
     * @return Map of user ID to list of PermissionDTO (only valid IDs included)
     * @throws Exception if service fails (propagated as GraphQL error)
     */
    fun resolveUserPermissionsBatch(
        userIds: List<String>,
    ): Map<String, List<io.github.salomax.neotool.security.graphql.dto.PermissionDTO>> {
        if (userIds.isEmpty()) {
            return emptyMap()
        }

        // Parse valid user IDs while preserving order and mapping original string to UUID
        // Invalid IDs are logged but included in result with empty list
        val validUserIdMap = mutableMapOf<String, UUID>()
        val userIdUuids =
            userIds.mapNotNull { userId ->
                try {
                    val userIdUuid = UUID.fromString(userId)
                    validUserIdMap[userId] = userIdUuid
                    userIdUuid
                } catch (e: IllegalArgumentException) {
                    logger.warn { "Invalid user ID in batch request: $userId" }
                    null
                }
            }

        // Use service layer to batch load user permissions (only for valid IDs)
        val userPermissionsMap =
            if (userIdUuids.isNotEmpty()) {
                authorizationService.getUserPermissionsBatch(userIdUuids)
            } else {
                emptyMap()
            }

        // Convert domain objects to DTOs and build result map preserving order of original input
        // Filter out invalid IDs - only include valid IDs in the result
        val result =
            linkedMapOf<String, List<io.github.salomax.neotool.security.graphql.dto.PermissionDTO>>()
        for (userId in userIds) {
            val userIdUuid = validUserIdMap[userId]
            if (userIdUuid != null) {
                val permissions = userPermissionsMap[userIdUuid] ?: emptyList()
                val permissionDTOs =
                    permissions.map { permission ->
                        io.github.salomax.neotool.security.graphql.dto.PermissionDTO(
                            id =
                                permission.id?.toString() ?: throw IllegalArgumentException(
                                    "Permission must have an ID",
                                ),
                            name = permission.name,
                        )
                    }
                result[userId] = permissionDTOs
            }
            // Invalid IDs are filtered out - not included in result
        }

        return result
    }
}
