package io.github.salomax.neotool.security.graphql.resolver

import io.github.salomax.neotool.common.graphql.pagination.PaginationConstants
import io.github.salomax.neotool.security.graphql.dto.CreateGroupInputDTO
import io.github.salomax.neotool.security.graphql.dto.GroupConnectionDTO
import io.github.salomax.neotool.security.graphql.dto.GroupDTO
import io.github.salomax.neotool.security.graphql.dto.UpdateGroupInputDTO
import io.github.salomax.neotool.security.graphql.mapper.GroupManagementMapper
import io.github.salomax.neotool.security.graphql.mapper.UserManagementMapper
import io.github.salomax.neotool.security.service.management.GroupManagementService
import jakarta.inject.Singleton
import mu.KotlinLogging
import java.util.UUID

/**
 * GraphQL resolver for group management operations.
 * Provides queries for listing and searching groups, CRUD mutations,
 * and relationship resolvers for Group.roles and Group.members.
 */
@Singleton
class GroupManagementResolver(
    private val groupManagementService: GroupManagementService,
    private val mapper: GroupManagementMapper,
    private val userManagementMapper: UserManagementMapper,
) {
    private val logger = KotlinLogging.logger {}

    /**
     * Get a single group by ID.
     *
     * @param id The group ID
     * @return GroupDTO or null if not found
     * @throws IllegalArgumentException if group ID is invalid
     * @throws Exception if service fails (propagated as GraphQL error)
     */
    fun group(id: String): GroupDTO? {
        // Validate and convert group ID - throw IllegalArgumentException for invalid input
        val groupIdUuid =
            try {
                mapper.toGroupId(id)
            } catch (e: IllegalArgumentException) {
                throw IllegalArgumentException("Invalid group ID format: $id", e)
            }

        // Use service layer instead of direct repository access
        val group = groupManagementService.getGroupById(groupIdUuid)
        return group?.let { mapper.toGroupDTO(it) }
    }

    /**
     * Unified query for groups with optional pagination and search.
     * When query is omitted or empty, returns all groups (list behavior).
     * When query is provided, returns filtered groups (search behavior).
     * totalCount is always calculated.
     *
     * @param first Maximum number of results (default: 20, min: 1, max: 100)
     * @param after Cursor for pagination
     * @param query Optional search query
     * @param orderBy Optional order by specification
     * @return GroupConnectionDTO
     * @throws IllegalArgumentException if first is invalid or orderBy contains invalid fields/directions
     * @throws Exception if service fails (propagated as GraphQL error)
     */
    fun groups(
        first: Int?,
        after: String?,
        query: String?,
        orderBy: List<Map<String, Any?>>?,
    ): GroupConnectionDTO {
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
                mapper.toGroupOrderByList(orderBy)
            } catch (e: IllegalArgumentException) {
                throw IllegalArgumentException("Invalid orderBy parameter: ${e.message}", e)
            }

        // Call service - propagate any exceptions (will be converted to GraphQL errors)
        val connection = groupManagementService.searchGroups(query, pageSize, after, orderByList)
        return mapper.toGroupConnectionDTO(connection)
    }

    /**
     * Create a new group.
     */
    fun createGroup(input: CreateGroupInputDTO): GroupDTO {
        return try {
            val command = mapper.toCreateGroupCommand(input)
            val group = groupManagementService.createGroup(command)
            mapper.toGroupDTO(group)
        } catch (e: Exception) {
            logger.error(e) { "Error creating group" }
            throw e
        }
    }

    /**
     * Update an existing group.
     */
    fun updateGroup(
        groupId: String,
        input: UpdateGroupInputDTO,
    ): GroupDTO {
        return try {
            val command = mapper.toUpdateGroupCommand(groupId, input)
            val group = groupManagementService.updateGroup(command)
            mapper.toGroupDTO(group)
        } catch (e: IllegalArgumentException) {
            logger.warn { "Invalid group ID or group not found: $groupId" }
            throw e
        } catch (e: Exception) {
            logger.error(e) { "Error updating group: $groupId" }
            throw e
        }
    }

    /**
     * Delete a group.
     */
    fun deleteGroup(groupId: String): Boolean {
        return try {
            val groupIdUuid = mapper.toGroupId(groupId)
            groupManagementService.deleteGroup(groupIdUuid)
            true
        } catch (e: IllegalArgumentException) {
            logger.warn { "Invalid group ID or group not found: $groupId" }
            throw e
        } catch (e: Exception) {
            logger.error(e) { "Error deleting group: $groupId" }
            throw e
        }
    }

    /**
     * Resolve Group.roles relationship.
     * Returns all roles assigned to the group.
     *
     * @param groupId The group ID
     * @return List of RoleDTO
     * @throws IllegalArgumentException if group ID is invalid
     * @throws Exception if service fails (propagated as GraphQL error)
     */
    fun resolveGroupRoles(groupId: String): List<io.github.salomax.neotool.security.graphql.dto.RoleDTO> {
        // Validate and convert group ID - throw IllegalArgumentException for invalid input
        val groupIdUuid =
            try {
                UUID.fromString(groupId)
            } catch (e: IllegalArgumentException) {
                throw IllegalArgumentException("Invalid group ID format: $groupId", e)
            }

        // Use service layer instead of direct repository access
        val roles = groupManagementService.getGroupRoles(groupIdUuid)

        // Convert domain objects to DTOs
        return roles.map { role ->
            io.github.salomax.neotool.security.graphql.dto.RoleDTO(
                id = role.id?.toString() ?: throw IllegalArgumentException("Role must have an ID"),
                name = role.name,
            )
        }
    }

    /**
     * Resolve Group.members relationship.
     * Returns all users who are members of the group.
     *
     * @param groupId The group ID
     * @return List of UserDTO
     * @throws IllegalArgumentException if group ID is invalid
     * @throws Exception if service fails (propagated as GraphQL error)
     */
    fun resolveGroupMembers(groupId: String): List<io.github.salomax.neotool.security.graphql.dto.UserDTO> {
        // Validate and convert group ID - throw IllegalArgumentException for invalid input
        val groupIdUuid =
            try {
                UUID.fromString(groupId)
            } catch (e: IllegalArgumentException) {
                throw IllegalArgumentException("Invalid group ID format: $groupId", e)
            }

        // Call service - propagate any exceptions (will be converted to GraphQL errors)
        val users = groupManagementService.getGroupMembers(groupIdUuid)
        return users.map { user -> userManagementMapper.toUserDTO(user) }
    }

    /**
     * Batch resolve Group.members relationship for multiple groups.
     * Returns a map of group ID to list of users who are members of that group.
     * Optimized to avoid N+1 queries.
     * Guarantees one entry in the result map for each requested group ID, even if invalid.
     *
     * @param groupIds List of group IDs
     * @return Map of group ID to list of UserDTO (empty list for invalid IDs)
     * @throws Exception if service fails (propagated as GraphQL error)
     */
    fun resolveGroupMembersBatch(
        groupIds: List<String>,
    ): Map<String, List<io.github.salomax.neotool.security.graphql.dto.UserDTO>> {
        if (groupIds.isEmpty()) {
            return emptyMap()
        }

        // Parse valid UUIDs while preserving order and mapping original string to UUID
        // Invalid IDs are logged but included in result with empty list
        val groupIdToUuidMap = mutableMapOf<String, UUID>()
        val groupIdUuids =
            groupIds.mapNotNull { groupId ->
                try {
                    val uuid = UUID.fromString(groupId)
                    groupIdToUuidMap[groupId] = uuid
                    uuid
                } catch (e: IllegalArgumentException) {
                    logger.warn { "Invalid group ID in batch request: $groupId" }
                    null
                }
            }

        // Use service layer to batch load group members (only for valid IDs)
        val groupMembersMap =
            if (groupIdUuids.isNotEmpty()) {
                groupManagementService.getGroupMembersBatch(groupIdUuids)
            } else {
                emptyMap()
            }

        // Convert domain objects to DTOs and build result map preserving order of original input
        // Ensure every requested group ID has an entry (empty list for invalid IDs)
        val result =
            linkedMapOf<String, List<io.github.salomax.neotool.security.graphql.dto.UserDTO>>()
        for (groupId in groupIds) {
            val groupIdUuid = groupIdToUuidMap[groupId]
            if (groupIdUuid != null) {
                val users = groupMembersMap[groupIdUuid] ?: emptyList()
                val userDTOs = users.map { user -> userManagementMapper.toUserDTO(user) }
                result[groupId] = userDTOs
            } else {
                // Invalid ID: add entry with empty list to maintain DataLoader contract
                result[groupId] = emptyList()
            }
        }

        return result
    }

    /**
     * Assign a role to a group.
     */
    fun assignRoleToGroup(
        groupId: String,
        roleId: String,
    ): GroupDTO {
        return try {
            val command =
                io.github.salomax.neotool.security.domain.GroupManagement.AssignRoleToGroupCommand(
                    groupId = mapper.toGroupId(groupId),
                    roleId = mapper.toRoleId(roleId),
                )
            val group = groupManagementService.assignRoleToGroup(command)
            mapper.toGroupDTO(group)
        } catch (e: IllegalArgumentException) {
            logger.warn { "Invalid group ID or role ID: groupId=$groupId, roleId=$roleId" }
            throw e
        } catch (e: Exception) {
            logger.error(e) { "Error assigning role to group: groupId=$groupId, roleId=$roleId" }
            throw e
        }
    }

    /**
     * Remove a role from a group.
     */
    fun removeRoleFromGroup(
        groupId: String,
        roleId: String,
    ): GroupDTO {
        return try {
            val command =
                io.github.salomax.neotool.security.domain.GroupManagement.RemoveRoleFromGroupCommand(
                    groupId = mapper.toGroupId(groupId),
                    roleId = mapper.toRoleId(roleId),
                )
            val group = groupManagementService.removeRoleFromGroup(command)
            mapper.toGroupDTO(group)
        } catch (e: IllegalArgumentException) {
            logger.warn { "Invalid group ID or role ID: groupId=$groupId, roleId=$roleId" }
            throw e
        } catch (e: Exception) {
            logger.error(e) { "Error removing role from group: groupId=$groupId, roleId=$roleId" }
            throw e
        }
    }

    /**
     * Batch resolve Group.roles relationship for multiple groups.
     * Returns a map of group ID to list of roles assigned to that group.
     * Optimized to avoid N+1 queries.
     * Invalid IDs are filtered out and not included in the result.
     *
     * @param groupIds List of group IDs
     * @return Map of group ID to list of RoleDTO (only valid IDs included)
     * @throws Exception if service fails (propagated as GraphQL error)
     */
    fun resolveGroupRolesBatch(
        groupIds: List<String>,
    ): Map<String, List<io.github.salomax.neotool.security.graphql.dto.RoleDTO>> {
        if (groupIds.isEmpty()) {
            return emptyMap()
        }

        // Parse valid UUIDs while preserving order and mapping original string to UUID
        // Invalid IDs are logged but included in result with empty list
        val groupIdToUuidMap = mutableMapOf<String, UUID>()
        val groupIdUuids =
            groupIds.mapNotNull { groupId ->
                try {
                    val uuid = UUID.fromString(groupId)
                    groupIdToUuidMap[groupId] = uuid
                    uuid
                } catch (e: IllegalArgumentException) {
                    logger.warn { "Invalid group ID in batch request: $groupId" }
                    null
                }
            }

        // Use service layer to batch load group roles (only for valid IDs)
        val groupRolesMap =
            if (groupIdUuids.isNotEmpty()) {
                groupManagementService.getGroupRolesBatch(groupIdUuids)
            } else {
                emptyMap()
            }

        // Convert domain objects to DTOs and build result map preserving order of original input
        // Filter out invalid IDs - only include valid IDs in the result
        val result =
            linkedMapOf<String, List<io.github.salomax.neotool.security.graphql.dto.RoleDTO>>()
        for (groupId in groupIds) {
            val groupIdUuid = groupIdToUuidMap[groupId]
            if (groupIdUuid != null) {
                val roles = groupRolesMap[groupIdUuid] ?: emptyList()
                val roleDTOs =
                    roles.map { role ->
                        io.github.salomax.neotool.security.graphql.dto.RoleDTO(
                            id = role.id?.toString() ?: throw IllegalArgumentException("Role must have an ID"),
                            name = role.name,
                        )
                    }
                result[groupId] = roleDTOs
            }
            // Invalid IDs are filtered out - not included in result
        }

        return result
    }
}
