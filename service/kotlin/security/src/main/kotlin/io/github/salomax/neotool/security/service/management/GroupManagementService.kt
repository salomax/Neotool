package io.github.salomax.neotool.security.service.management

import io.github.salomax.neotool.common.graphql.pagination.CompositeCursor
import io.github.salomax.neotool.common.graphql.pagination.Connection
import io.github.salomax.neotool.common.graphql.pagination.ConnectionBuilder
import io.github.salomax.neotool.common.graphql.pagination.CursorEncoder
import io.github.salomax.neotool.common.graphql.pagination.OrderDirection
import io.github.salomax.neotool.common.graphql.pagination.PaginationConstants
import io.github.salomax.neotool.security.domain.GroupManagement
import io.github.salomax.neotool.security.domain.rbac.Group
import io.github.salomax.neotool.security.domain.rbac.GroupMembership
import io.github.salomax.neotool.security.domain.rbac.GroupRoleAssignment
import io.github.salomax.neotool.security.domain.rbac.MembershipType
import io.github.salomax.neotool.security.model.GroupOrderBy
import io.github.salomax.neotool.security.model.GroupOrderField
import io.github.salomax.neotool.security.repo.GroupMembershipRepository
import io.github.salomax.neotool.security.repo.GroupRepository
import io.github.salomax.neotool.security.repo.GroupRepositoryCustom
import io.github.salomax.neotool.security.repo.GroupRoleAssignmentRepository
import io.github.salomax.neotool.security.repo.RoleRepository
import io.github.salomax.neotool.security.repo.UserRepository
import jakarta.inject.Singleton
import jakarta.transaction.Transactional
import mu.KotlinLogging
import java.time.Instant
import java.util.UUID

/**
 * Service for managing groups.
 * Provides operations for listing, searching, creating, updating, and deleting groups.
 */
@Singleton
open class GroupManagementService(
    private val groupRepository: GroupRepository,
    private val groupSearchRepository: GroupRepositoryCustom,
    private val groupMembershipRepository: GroupMembershipRepository,
    private val groupRoleAssignmentRepository: GroupRoleAssignmentRepository,
    private val roleRepository: RoleRepository,
    private val userRepository: UserRepository,
) {
    private val logger = KotlinLogging.logger {}

    /**
     * Get a group by ID.
     *
     * @param groupId The UUID of the group
     * @return The group domain object, or null if not found
     */
    fun getGroupById(groupId: UUID): Group? =
        groupRepository
            .findById(groupId)
            .map { it.toDomain() }
            .orElse(null)

    /**
     * Search groups by name with cursor-based pagination.
     * Unified method that handles both list (no query) and search (with query) operations.
     * When query is null or empty, returns all groups. When query is provided, returns filtered groups.
     * totalCount is always calculated: total count of all groups when query is empty, total count of filtered groups when query is provided.
     *
     * @param query Optional search query (partial match, case-insensitive). If null or empty, returns all groups.
     * @param first Maximum number of results to return (default: 20, max: 100)
     * @param after Cursor string for pagination (base64-encoded UUID or composite cursor)
     * @param orderBy Optional list of order by specifications. If null or empty, defaults to NAME ASC, ID ASC. If empty array, defaults to ID ASC.
     * @return Connection containing paginated groups with totalCount
     */
    fun searchGroups(
        query: String?,
        first: Int = PaginationConstants.DEFAULT_PAGE_SIZE,
        after: String?,
        orderBy: List<GroupOrderBy>? = null,
    ): Connection<Group> {
        val pageSize =
            minOf(first, PaginationConstants.MAX_PAGE_SIZE)

        // Determine effective orderBy: default to current sort if null/empty, fallback to ID ASC if empty array
        val effectiveOrderBy =
            when {
                orderBy == null -> {
                    listOf(
                        GroupOrderBy(GroupOrderField.NAME, OrderDirection.ASC),
                        GroupOrderBy(GroupOrderField.ID, OrderDirection.ASC),
                    )
                }

                orderBy.isEmpty() -> {
                    listOf(GroupOrderBy(GroupOrderField.ID, OrderDirection.ASC))
                }

                else -> {
                    // Ensure ID is always last for deterministic ordering
                    val withoutId = orderBy.filter { it.field != GroupOrderField.ID }
                    withoutId + GroupOrderBy(GroupOrderField.ID, OrderDirection.ASC)
                }
            }

        // Decode cursor - try composite first, fallback to UUID for backward compatibility
        val afterCompositeCursor: CompositeCursor? =
            after?.let {
                try {
                    CursorEncoder.decodeCompositeCursorToUuid(it)
                } catch (e: Exception) {
                    // Try legacy UUID cursor for backward compatibility
                    try {
                        val uuid = CursorEncoder.decodeCursorToUuid(it)
                        // Convert legacy cursor to composite cursor with id only
                        CompositeCursor(
                            fieldValues = emptyMap(),
                            id = uuid.toString(),
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
            groupSearchRepository.searchByName(normalizedQuery, pageSize + 1, afterCompositeCursor, effectiveOrderBy)
        val hasMore = entities.size > pageSize

        // Always get total count (all groups if query is null, filtered groups if query is provided)
        val totalCount = groupSearchRepository.countByName(normalizedQuery)

        // Take only requested number and convert to domain
        val groups =
            entities
                .take(pageSize)
                .map { it.toDomain() }

        // Build connection with composite cursor encoding
        return ConnectionBuilder.buildConnection(
            items = groups,
            hasMore = hasMore,
            encodeCursor = { group ->
                val id = group.id ?: throw IllegalArgumentException("Group must have a non-null ID")
                // Build field values map from orderBy
                val fieldValues =
                    effectiveOrderBy
                        .filter { it.field != GroupOrderField.ID }
                        .associate { orderBy ->
                            val fieldName =
                                when (orderBy.field) {
                                    GroupOrderField.NAME -> "name"
                                    GroupOrderField.ID -> throw IllegalStateException("ID should not be in fieldValues")
                                }
                            val fieldValue =
                                when (orderBy.field) {
                                    GroupOrderField.NAME -> group.name
                                    GroupOrderField.ID -> throw IllegalStateException("ID should not be in fieldValues")
                                }
                            fieldName to fieldValue
                        }
                CursorEncoder.encodeCompositeCursor(fieldValues, id)
            },
            totalCount = totalCount,
        )
    }

    /**
     * Create a new group.
     *
     * @param command Create group command with name, optional description, and optional user IDs
     * @return The created group
     * @throws IllegalArgumentException if any user ID doesn't exist
     * @throws DataAccessException if group name already exists (database unique constraint)
     */
    @Transactional
    open fun createGroup(command: GroupManagement.CreateGroupCommand): Group {
        // Create domain object
        val group =
            Group(
                name = command.name,
                description = command.description,
                createdAt = Instant.now(),
                updatedAt = Instant.now(),
            )

        // Convert to entity and save
        val entity = group.toEntity()
        val saved = groupRepository.save(entity)

        logger.info { "Group created: ${saved.name} (ID: ${saved.id})" }

        // Handle user assignments if provided
        if (!command.userIds.isNullOrEmpty()) {
            validateUserIds(command.userIds)
            createGroupMemberships(saved.id, command.userIds)
            logger.info {
                "Group memberships created for group ${saved.name} (ID: ${saved.id}): ${command.userIds.size} users"
            }
        }

        return saved.toDomain()
    }

    /**
     * Update an existing group.
     *
     * @param command Update group command with groupId, name, optional description, and optional user IDs
     * @return The updated group
     * @throws IllegalArgumentException if group not found or any user ID doesn't exist
     * @throws DataAccessException if group name already exists (database unique constraint)
     */
    @Transactional
    open fun updateGroup(command: GroupManagement.UpdateGroupCommand): Group {
        val entity =
            groupRepository
                .findById(command.groupId)
                .orElseThrow {
                    IllegalArgumentException("Group not found with ID: ${command.groupId}")
                }

        // Update entity
        entity.name = command.name
        entity.description = command.description
        entity.updatedAt = Instant.now()

        val saved = groupRepository.update(entity)

        logger.info { "Group updated: ${saved.name} (ID: ${saved.id})" }

        // Handle user membership synchronization if userIds is provided
        if (command.userIds != null) {
            if (command.userIds.isEmpty()) {
                // Empty list means remove all memberships
                removeAllGroupMemberships(saved.id)
                logger.info { "All group memberships removed for group ${saved.name} (ID: ${saved.id})" }
            } else {
                // Non-empty list means synchronize memberships
                validateUserIds(command.userIds)
                synchronizeGroupMemberships(saved.id, command.userIds)
                logger.info {
                    "Group memberships synchronized for group ${saved.name} " +
                        "(ID: ${saved.id}): ${command.userIds.size} users"
                }
            }
        }
        // If userIds is null, no change to memberships

        return saved.toDomain()
    }

    /**
     * Delete a group.
     *
     * @param groupId The UUID of the group to delete
     * @throws IllegalArgumentException if group not found
     * @throws DataAccessException if group has dependencies (database foreign key constraint)
     */
    @Transactional
    open fun deleteGroup(groupId: UUID) {
        groupRepository.deleteById(groupId)

        logger.info { "Group deleted (ID: $groupId)" }
    }

    /**
     * Validate that all user IDs exist in the system.
     *
     * @param userIds List of user IDs to validate
     * @throws IllegalArgumentException if any user ID doesn't exist
     */
    private fun validateUserIds(userIds: List<UUID>) {
        val foundUsers = userRepository.findByIdIn(userIds)
        val foundUserIds = foundUsers.map { it.id }.toSet()
        val missingUserIds = userIds.filter { it !in foundUserIds }

        if (missingUserIds.isNotEmpty()) {
            throw IllegalArgumentException(
                "One or more users not found: ${missingUserIds.joinToString(", ")}",
            )
        }
    }

    /**
     * Create group memberships for a list of user IDs.
     *
     * @param groupId The group ID
     * @param userIds List of user IDs to add as members
     */
    private fun createGroupMemberships(
        groupId: UUID,
        userIds: List<UUID>,
    ) {
        val now = Instant.now()
        val memberships =
            userIds.map { userId ->
                GroupMembership(
                    userId = userId,
                    groupId = groupId,
                    membershipType = MembershipType.MEMBER,
                    validUntil = null,
                    createdAt = now,
                    updatedAt = now,
                )
            }

        val entities = memberships.map { it.toEntity() }
        groupMembershipRepository.saveAll(entities)
    }

    /**
     * Synchronize group memberships by adding new users and removing users not in the list.
     *
     * @param groupId The group ID
     * @param newUserIds List of user IDs that should be members (replaces current memberships)
     */
    private fun synchronizeGroupMemberships(
        groupId: UUID,
        newUserIds: List<UUID>,
    ) {
        val currentMemberships = groupMembershipRepository.findByGroupId(groupId)
        val currentUserIds = currentMemberships.map { it.userId }.toSet()
        val newUserIdsSet = newUserIds.toSet()

        // Calculate users to add and remove
        val userIdsToAdd = newUserIdsSet - currentUserIds
        val userIdsToRemove = currentUserIds - newUserIdsSet

        // Add new memberships
        if (userIdsToAdd.isNotEmpty()) {
            createGroupMemberships(groupId, userIdsToAdd.toList())
            logger.debug { "Added ${userIdsToAdd.size} users to group $groupId" }
        }

        // Remove old memberships
        if (userIdsToRemove.isNotEmpty()) {
            val membershipsToRemove =
                currentMemberships.filter { it.userId in userIdsToRemove }
            groupMembershipRepository.deleteAll(membershipsToRemove)
            logger.debug { "Removed ${userIdsToRemove.size} users from group $groupId" }
        }
    }

    /**
     * Remove all group memberships for a group.
     *
     * @param groupId The group ID
     */
    private fun removeAllGroupMemberships(groupId: UUID) {
        val memberships = groupMembershipRepository.findByGroupId(groupId)
        if (memberships.isNotEmpty()) {
            groupMembershipRepository.deleteAll(memberships)
        }
    }

    /**
     * Assign a role to a group.
     *
     * @param command Assign role command with groupId and roleId
     * @return The updated group
     * @throws IllegalArgumentException if group or role not found
     */
    @Transactional
    open fun assignRoleToGroup(command: GroupManagement.AssignRoleToGroupCommand): Group {
        // Validate group and role exist before assigning
        val group =
            groupRepository
                .findById(command.groupId)
                .orElseThrow {
                    IllegalArgumentException("Group not found with ID: ${command.groupId}")
                }
        val role =
            roleRepository
                .findById(command.roleId)
                .orElseThrow {
                    IllegalArgumentException("Role not found with ID: ${command.roleId}")
                }

        // Check for existing assignment
        val existingAssignments = groupRoleAssignmentRepository.findByGroupId(command.groupId)
        val existingAssignment = existingAssignments.firstOrNull { it.roleId == command.roleId }
        if (existingAssignment != null) {
            logger.info {
                "Role '${role.name}' already assigned to group '${group.name}' " +
                    "(Group ID: ${command.groupId}, Role ID: ${command.roleId})"
            }
            return group.toDomain()
        }

        // Create new assignment
        val assignment =
            GroupRoleAssignment(
                groupId = command.groupId,
                roleId = command.roleId,
                validFrom = null,
                validUntil = null,
                createdAt = Instant.now(),
                updatedAt = Instant.now(),
            )
        groupRoleAssignmentRepository.save(assignment.toEntity())

        logger.info {
            "Role '${role.name}' assigned to group '${group.name}' " +
                "(Group ID: ${command.groupId}, Role ID: ${command.roleId})"
        }

        return group.toDomain()
    }

    /**
     * Remove a role from a group.
     *
     * @param command Remove role command with groupId and roleId
     * @return The updated group
     * @throws IllegalArgumentException if group or role not found
     */
    @Transactional
    open fun removeRoleFromGroup(command: GroupManagement.RemoveRoleFromGroupCommand): Group {
        // Validate group and role exist before removing
        val group =
            groupRepository
                .findById(command.groupId)
                .orElseThrow {
                    IllegalArgumentException("Group not found with ID: ${command.groupId}")
                }
        val role =
            roleRepository
                .findById(command.roleId)
                .orElseThrow {
                    IllegalArgumentException("Role not found with ID: ${command.roleId}")
                }

        // Find and delete assignment
        val assignments = groupRoleAssignmentRepository.findByGroupId(command.groupId)
        val assignmentToRemove = assignments.firstOrNull { it.roleId == command.roleId }
        if (assignmentToRemove != null) {
            groupRoleAssignmentRepository.delete(assignmentToRemove)
            logger.info {
                "Role '${role.name}' removed from group '${group.name}' " +
                    "(Group ID: ${command.groupId}, Role ID: ${command.roleId})"
            }
        } else {
            logger.info {
                "Role '${role.name}' was not assigned to group '${group.name}' " +
                    "(Group ID: ${command.groupId}, Role ID: ${command.roleId})"
            }
        }

        return group.toDomain()
    }

    /**
     * Get all roles assigned to a group.
     *
     * @param groupId The UUID of the group
     * @param now Current timestamp for validity checks
     * @return List of Role domain objects assigned to the group
     */
    fun getGroupRoles(
        groupId: UUID,
        now: Instant = Instant.now(),
    ): List<io.github.salomax.neotool.security.domain.rbac.Role> {
        val assignments = groupRoleAssignmentRepository.findValidAssignmentsByGroupId(groupId, now)
        val roleIds = assignments.map { it.roleId }.distinct()

        if (roleIds.isEmpty()) {
            return emptyList()
        }

        val roles = roleRepository.findByIdIn(roleIds)
        return roles
            .mapNotNull { it.toDomain() }
            .filter { it.id != null }
    }

    /**
     * Get all users who are members of a group.
     *
     * @param groupId The UUID of the group
     * @return List of User domain objects who are members of the group
     */
    fun getGroupMembers(groupId: UUID): List<io.github.salomax.neotool.security.domain.rbac.User> {
        val memberships = groupMembershipRepository.findByGroupId(groupId)
        val userIds = memberships.map { it.userId }.distinct()

        if (userIds.isEmpty()) {
            return emptyList()
        }

        val users = userRepository.findByIdIn(userIds)
        return users.map { it.toDomain() }
    }

    /**
     * Batch get all users who are members of multiple groups.
     * Optimized to avoid N+1 queries.
     *
     * @param groupIds List of group UUIDs
     * @return Map of group ID to list of User domain objects who are members of that group
     */
    fun getGroupMembersBatch(
        groupIds: List<UUID>,
    ): Map<UUID, List<io.github.salomax.neotool.security.domain.rbac.User>> {
        if (groupIds.isEmpty()) {
            return emptyMap()
        }

        // Batch load all memberships for all groups
        val allMemberships = groupMembershipRepository.findByGroupIdIn(groupIds)

        // Collect all unique user IDs
        val allUserIds = allMemberships.map { it.userId }.distinct()

        // Batch load all users
        val users =
            if (allUserIds.isEmpty()) {
                emptyList()
            } else {
                userRepository.findByIdIn(allUserIds)
            }

        // Create a map of user ID to User domain object (filter out users without IDs)
        val userMap =
            users
                .map { it.toDomain() }
                .filter { it.id != null }
                .associateBy { it.id!! }

        // Group memberships by group ID and map to Users
        val result = mutableMapOf<UUID, List<io.github.salomax.neotool.security.domain.rbac.User>>()
        for (groupId in groupIds) {
            val groupMemberships = allMemberships.filter { it.groupId == groupId }
            val groupUserIds = groupMemberships.map { it.userId }.distinct()
            val groupUsers = groupUserIds.mapNotNull { userId -> userMap[userId] }
            result[groupId] = groupUsers
        }

        return result
    }

    /**
     * Batch get all roles assigned to multiple groups.
     * Optimized to avoid N+1 queries.
     *
     * @param groupIds List of group UUIDs
     * @param now Current timestamp for validity checks
     * @return Map of group ID to list of roles
     */
    fun getGroupRolesBatch(
        groupIds: List<UUID>,
        now: Instant = Instant.now(),
    ): Map<UUID, List<io.github.salomax.neotool.security.domain.rbac.Role>> {
        if (groupIds.isEmpty()) {
            return emptyMap()
        }

        // Batch load all group role assignments
        val allAssignments = groupRoleAssignmentRepository.findValidAssignmentsByGroupIds(groupIds, now)

        // Collect all unique role IDs
        val allRoleIds = allAssignments.map { it.roleId }.distinct()

        // Batch load all roles
        val allRoles =
            if (allRoleIds.isNotEmpty()) {
                roleRepository
                    .findByIdIn(allRoleIds)
                    .mapNotNull { it.toDomain() }
                    .filter { it.id != null }
                    .associateBy { it.id!! }
            } else {
                emptyMap()
            }

        // Group assignments by group ID
        val groupAssignmentsMap = allAssignments.groupBy { it.groupId }

        // Build result map
        val result = mutableMapOf<UUID, List<io.github.salomax.neotool.security.domain.rbac.Role>>()
        for (groupId in groupIds) {
            val assignments = groupAssignmentsMap[groupId] ?: emptyList()
            val roleIds = assignments.map { it.roleId }.distinct()
            val roles = roleIds.mapNotNull { roleId -> allRoles[roleId] }
            result[groupId] = roles
        }

        return result
    }
}
