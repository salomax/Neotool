package io.github.salomax.neotool.security.service.management

import io.github.salomax.neotool.common.graphql.pagination.CompositeCursor
import io.github.salomax.neotool.common.graphql.pagination.Connection
import io.github.salomax.neotool.common.graphql.pagination.ConnectionBuilder
import io.github.salomax.neotool.common.graphql.pagination.CursorEncoder
import io.github.salomax.neotool.common.graphql.pagination.OrderDirection
import io.github.salomax.neotool.common.graphql.pagination.PaginationConstants
import io.github.salomax.neotool.common.security.principal.PrincipalType
import io.github.salomax.neotool.security.domain.UserManagement
import io.github.salomax.neotool.security.domain.rbac.GroupMembership
import io.github.salomax.neotool.security.domain.rbac.MembershipType
import io.github.salomax.neotool.security.domain.rbac.User
import io.github.salomax.neotool.security.model.PrincipalEntity
import io.github.salomax.neotool.security.model.UserOrderBy
import io.github.salomax.neotool.security.model.UserOrderField
import io.github.salomax.neotool.security.repo.GroupMembershipRepository
import io.github.salomax.neotool.security.repo.GroupRepository
import io.github.salomax.neotool.security.repo.PrincipalRepository
import io.github.salomax.neotool.security.repo.UserRepository
import io.github.salomax.neotool.security.repo.UserRepositoryCustom
import jakarta.inject.Singleton
import jakarta.transaction.Transactional
import mu.KotlinLogging
import java.time.Instant
import java.util.UUID

/**
 * Service for managing users.
 * Provides operations for listing, searching, enabling, and disabling users.
 */
@Singleton
open class UserManagementService(
    private val userRepository: UserRepository,
    private val userSearchRepository: UserRepositoryCustom,
    private val groupMembershipRepository: GroupMembershipRepository,
    private val groupRepository: GroupRepository,
    private val principalRepository: PrincipalRepository,
) {
    private val logger = KotlinLogging.logger {}

    /**
     * Get a user by ID.
     *
     * @param userId The UUID of the user
     * @return The user domain object, or null if not found
     */
    fun getUserById(userId: UUID): User? =
        userRepository
            .findById(userId)
            .map { it.toDomain() }
            .orElse(null)

    /**
     * Search users by name or email with cursor-based pagination.
     * Unified method that handles both list (no query) and search (with query) operations.
     * When query is null or empty, returns all users. When query is provided, returns filtered users.
     * totalCount is always calculated: total count of all users when query is empty, total count of filtered users when query is provided.
     *
     * @param query Optional search query (partial match, case-insensitive). If null or empty, returns all users.
     * @param first Maximum number of results to return (default: 20, max: 100)
     * @param after Cursor string for pagination (base64-encoded UUID or composite cursor)
     * @param orderBy Optional list of order by specifications. If null or empty, defaults to DISPLAY_NAME ASC, ID ASC. If empty array, defaults to ID ASC.
     * @return Connection containing paginated users with totalCount
     */
    fun searchUsers(
        query: String?,
        first: Int = PaginationConstants.DEFAULT_PAGE_SIZE,
        after: String?,
        orderBy: List<UserOrderBy>? = null,
    ): Connection<User> {
        val pageSize =
            minOf(first, PaginationConstants.MAX_PAGE_SIZE)

        // Determine effective orderBy: default to current sort if null/empty, fallback to ID ASC if empty array
        val effectiveOrderBy =
            when {
                orderBy == null -> {
                    listOf(
                        UserOrderBy(UserOrderField.DISPLAY_NAME, OrderDirection.ASC),
                        UserOrderBy(UserOrderField.ID, OrderDirection.ASC),
                    )
                }

                orderBy.isEmpty() -> {
                    listOf(UserOrderBy(UserOrderField.ID, OrderDirection.ASC))
                }

                else -> {
                    // Ensure ID is always last for deterministic ordering
                    val withoutId = orderBy.filter { it.field != UserOrderField.ID }
                    withoutId + UserOrderBy(UserOrderField.ID, OrderDirection.ASC)
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
            userSearchRepository.searchByNameOrEmail(
                normalizedQuery,
                pageSize + 1,
                afterCompositeCursor,
                effectiveOrderBy,
            )
        val hasMore = entities.size > pageSize

        // Always get total count (all users if query is null, filtered users if query is provided)
        val totalCount = userSearchRepository.countByNameOrEmail(normalizedQuery)

        // Take only requested number and convert to domain
        val users =
            entities
                .take(pageSize)
                .map { it.toDomain() }

        // Build connection with composite cursor encoding
        return ConnectionBuilder.buildConnection(
            items = users,
            hasMore = hasMore,
            encodeCursor = { user ->
                val id = user.id ?: throw IllegalArgumentException("User must have a non-null ID")
                // Build field values map from orderBy, skipping null values
                val fieldValues =
                    effectiveOrderBy
                        .filter { it.field != UserOrderField.ID }
                        .mapNotNull { orderBy ->
                            val fieldName =
                                when (orderBy.field) {
                                    UserOrderField.DISPLAY_NAME -> "displayName"
                                    UserOrderField.EMAIL -> "email"
                                    UserOrderField.ID -> throw IllegalStateException("ID should not be in fieldValues")
                                }
                            val fieldValue: Any? =
                                when (orderBy.field) {
                                    UserOrderField.DISPLAY_NAME -> {
                                        // For DISPLAY_NAME, use COALESCE logic: displayName if not null, else email
                                        // This matches the sorting logic in SortingHelpers.buildUserOrderBy
                                        user.displayName ?: user.email
                                    }

                                    UserOrderField.EMAIL -> {
                                        user.email
                                    }

                                    UserOrderField.ID -> {
                                        throw IllegalStateException("ID should not be in fieldValues")
                                    }
                                }
                            // Only include non-null field values in cursor
                            when {
                                fieldValue != null -> fieldName to fieldValue
                                else -> null
                            }
                        }.toMap()
                CursorEncoder.encodeCompositeCursor(fieldValues, id)
            },
            totalCount = totalCount,
        )
    }

    /**
     * Enable a user account.
     *
     * @param userId The UUID of the user to enable
     * @return The enabled user
     * @throws IllegalArgumentException if user not found
     * @throws IllegalStateException if user is already enabled
     */
    @Transactional
    open fun enableUser(userId: UUID): User {
        val entity =
            userRepository
                .findById(userId)
                .orElseThrow {
                    IllegalArgumentException("User not found with ID: $userId")
                }

        // Get or create principal
        val principal = getOrCreatePrincipal(userId)

        // Validate domain state
        UserManagement.Validator.validatePrincipalNotAlreadyEnabled(principal)

        // Update principal (source of truth for authentication)
        syncUserPrincipalEnabled(userId, enabled = true)

        logger.info { "User enabled: ${entity.email} (ID: ${entity.id})" }

        return entity.toDomain()
    }

    /**
     * Disable a user account.
     *
     * @param userId The UUID of the user to disable
     * @return The disabled user
     * @throws IllegalArgumentException if user not found
     * @throws IllegalStateException if user is already disabled
     */
    @Transactional
    open fun disableUser(userId: UUID): User {
        val entity =
            userRepository
                .findById(userId)
                .orElseThrow { IllegalArgumentException("User not found with ID: $userId") }

        // Get or create principal
        val principal = getOrCreatePrincipal(userId)

        // Validate domain state
        UserManagement.Validator.validatePrincipalNotAlreadyDisabled(principal)

        // Update principal (source of truth for authentication)
        syncUserPrincipalEnabled(userId, enabled = false)

        logger.info { "User disabled: ${entity.email} (ID: ${entity.id})" }

        return entity.toDomain()
    }

    /**
     * Get or create a principal for the given user ID.
     *
     * @param userId The user ID
     * @return The principal domain object
     */
    private fun getOrCreatePrincipal(userId: UUID): io.github.salomax.neotool.security.model.Principal {
        val principalEntity =
            principalRepository
                .findByPrincipalTypeAndExternalId(
                    PrincipalType.USER,
                    userId.toString(),
                ).orElseGet {
                    // Create principal if it doesn't exist
                    val newPrincipal =
                        PrincipalEntity(
                            id = null,
                            principalType = PrincipalType.USER,
                            externalId = userId.toString(),
                            enabled = true,
                        )
                    principalRepository.save(newPrincipal)
                    logger.debug { "Created principal for user ID: $userId" }
                    newPrincipal
                }
        return principalEntity.toDomain()
    }

    /**
     * Sync user.enabled to principal.enabled.
     * Principals.enabled is the source of truth for authentication/authorization.
     * This method ensures the principal's enabled flag matches the user's enabled flag.
     * Note: This method should be called from within a transaction.
     *
     * @param userId The user ID
     * @param enabled Whether the principal should be enabled
     */
    private fun syncUserPrincipalEnabled(
        userId: UUID,
        enabled: Boolean,
    ) {
        val principal =
            principalRepository
                .findByPrincipalTypeAndExternalId(
                    PrincipalType.USER,
                    userId.toString(),
                ).orElse(null)

        if (principal != null) {
            // Update existing principal
            principal.enabled = enabled
            principal.updatedAt = Instant.now()
            principalRepository.update(principal)
            logger.debug { "Synced principal enabled status for user ID: $userId to $enabled" }
        } else {
            // Create principal if it doesn't exist (shouldn't happen, but handle gracefully)
            val newPrincipal =
                PrincipalEntity(
                    id = null,
                    principalType = PrincipalType.USER,
                    externalId = userId.toString(),
                    enabled = enabled,
                )
            principalRepository.save(newPrincipal)
            logger.debug { "Created principal for user ID: $userId with enabled=$enabled" }
        }
    }

    /**
     * Update basic user profile fields.
     *
     * Currently only supports updating displayName; email is immutable.
     *
     * @param command Update user command with userId and new displayName
     * @return The updated user
     * @throws IllegalArgumentException if user not found
     */
    @Transactional
    open fun updateUser(command: UserManagement.UpdateUserCommand): User {
        val entity =
            userRepository
                .findById(command.userId)
                .orElseThrow {
                    IllegalArgumentException("User not found with ID: ${command.userId}")
                }

        // Update displayName (email is immutable)
        entity.displayName = command.displayName
        entity.updatedAt = Instant.now()

        val saved = userRepository.update(entity)

        logger.info { "User updated: ${saved.email} (ID: ${saved.id}), displayName: ${saved.displayName}" }

        return saved.toDomain()
    }

    /**
     * Assign a group to a user.
     *
     * @param command Assign group command with userId and groupId
     * @return The updated user
     * @throws IllegalArgumentException if user or group not found
     */
    @Transactional
    open fun assignGroupToUser(command: UserManagement.AssignGroupToUserCommand): User {
        // Validate user and group exist before assigning
        val user =
            userRepository
                .findById(command.userId)
                .orElseThrow {
                    IllegalArgumentException("User not found with ID: ${command.userId}")
                }
        val group =
            groupRepository
                .findById(command.groupId)
                .orElseThrow {
                    IllegalArgumentException("Group not found with ID: ${command.groupId}")
                }

        // Check for existing membership
        val existingMemberships = groupMembershipRepository.findByUserIdAndGroupId(command.userId, command.groupId)
        if (existingMemberships.isNotEmpty()) {
            logger.info {
                "User '${user.email}' already member of group '${group.name}' " +
                    "(User ID: ${command.userId}, Group ID: ${command.groupId})"
            }
            return user.toDomain()
        }

        // Create new membership
        val membership =
            GroupMembership(
                userId = command.userId,
                groupId = command.groupId,
                membershipType = MembershipType.MEMBER,
                validUntil = null,
                createdAt = Instant.now(),
                updatedAt = Instant.now(),
            )
        groupMembershipRepository.save(membership.toEntity())

        logger.info {
            "User '${user.email}' assigned to group '${group.name}' " +
                "(User ID: ${command.userId}, Group ID: ${command.groupId})"
        }

        return user.toDomain()
    }

    /**
     * Remove a group from a user.
     *
     * @param command Remove group command with userId and groupId
     * @return The updated user
     * @throws IllegalArgumentException if user or group not found
     */
    @Transactional
    open fun removeGroupFromUser(command: UserManagement.RemoveGroupFromUserCommand): User {
        // Validate user and group exist before removing
        val user =
            userRepository
                .findById(command.userId)
                .orElseThrow {
                    IllegalArgumentException("User not found with ID: ${command.userId}")
                }
        val group =
            groupRepository
                .findById(command.groupId)
                .orElseThrow {
                    IllegalArgumentException("Group not found with ID: ${command.groupId}")
                }

        // Find and delete membership
        val memberships = groupMembershipRepository.findByUserIdAndGroupId(command.userId, command.groupId)
        if (memberships.isNotEmpty()) {
            groupMembershipRepository.deleteAll(memberships)
            logger.info {
                "User '${user.email}' removed from group '${group.name}' " +
                    "(User ID: ${command.userId}, Group ID: ${command.groupId})"
            }
        } else {
            logger.info {
                "User '${user.email}' was not a member of group '${group.name}' " +
                    "(User ID: ${command.userId}, Group ID: ${command.groupId})"
            }
        }

        return user.toDomain()
    }

    /**
     * Batch get all groups for multiple users.
     * Optimized to avoid N+1 queries.
     *
     * @param userIds List of user IDs
     * @param now Current timestamp for validity checks
     * @return Map of user ID to list of groups
     */
    fun getUserGroupsBatch(
        userIds: List<UUID>,
        now: Instant = Instant.now(),
    ): Map<UUID, List<io.github.salomax.neotool.security.domain.rbac.Group>> {
        if (userIds.isEmpty()) {
            return emptyMap()
        }

        // Batch load all group memberships for all users
        val allMemberships = groupMembershipRepository.findActiveMembershipsByUserIds(userIds, now)

        // Collect all unique group IDs
        val allGroupIds = allMemberships.map { it.groupId }.distinct()

        // Batch load all groups
        val allGroups =
            if (allGroupIds.isNotEmpty()) {
                groupRepository
                    .findByIdIn(allGroupIds)
                    .map { it.toDomain() }
                    .filter { it.id != null }
                    .associateBy { it.id!! }
            } else {
                emptyMap()
            }

        // Group memberships by user ID
        val userMembershipsMap = allMemberships.groupBy { it.userId }

        // Build result map
        val result = mutableMapOf<UUID, List<io.github.salomax.neotool.security.domain.rbac.Group>>()
        for (userId in userIds) {
            val memberships = userMembershipsMap[userId] ?: emptyList()
            val groupIds = memberships.map { it.groupId }.distinct()
            val groups = groupIds.mapNotNull { groupId -> allGroups[groupId] }
            result[userId] = groups
        }

        return result
    }
}
