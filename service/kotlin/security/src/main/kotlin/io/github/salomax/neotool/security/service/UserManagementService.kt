package io.github.salomax.neotool.security.service

import io.github.salomax.neotool.common.graphql.pagination.Connection
import io.github.salomax.neotool.common.graphql.pagination.ConnectionBuilder
import io.github.salomax.neotool.common.graphql.pagination.CursorEncoder
import io.github.salomax.neotool.common.graphql.pagination.PaginationConstants
import io.github.salomax.neotool.security.domain.UserManagement
import io.github.salomax.neotool.security.domain.rbac.GroupMembership
import io.github.salomax.neotool.security.domain.rbac.MembershipType
import io.github.salomax.neotool.security.domain.rbac.RoleAssignment
import io.github.salomax.neotool.security.domain.rbac.User
import io.github.salomax.neotool.security.repo.GroupMembershipRepository
import io.github.salomax.neotool.security.repo.GroupRepository
import io.github.salomax.neotool.security.repo.RoleAssignmentRepository
import io.github.salomax.neotool.security.repo.RoleRepository
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
    private val roleAssignmentRepository: RoleAssignmentRepository,
    private val roleRepository: RoleRepository,
    private val groupMembershipRepository: GroupMembershipRepository,
    private val groupRepository: GroupRepository,
) {
    private val logger = KotlinLogging.logger {}

    /**
     * Search users by name or email with cursor-based pagination.
     * Unified method that handles both list (no query) and search (with query) operations.
     * When query is null or empty, returns all users. When query is provided, returns filtered users.
     * totalCount is always calculated: total count of all users when query is empty, total count of filtered users when query is provided.
     *
     * @param query Optional search query (partial match, case-insensitive). If null or empty, returns all users.
     * @param first Maximum number of results to return (default: 20, max: 100)
     * @param after Cursor string for pagination (base64-encoded UUID)
     * @return Connection containing paginated users with totalCount
     */
    fun searchUsers(
        query: String?,
        first: Int = PaginationConstants.DEFAULT_PAGE_SIZE,
        after: String?,
    ): Connection<User> {
        val pageSize =
            minOf(first, PaginationConstants.MAX_PAGE_SIZE)

        val afterCursor =
            try {
                after?.let { CursorEncoder.decodeCursorToUuid(it) }
            } catch (e: Exception) {
                throw IllegalArgumentException("Invalid cursor: $after", e)
            }

        // Normalize query: convert empty string to null
        val normalizedQuery = if (query.isNullOrBlank()) null else query

        // Query one extra to check for more results
        val entities =
            userSearchRepository.searchByNameOrEmail(normalizedQuery, pageSize + 1, afterCursor)
        val hasMore = entities.size > pageSize

        // Always get total count (all users if query is null, filtered users if query is provided)
        val totalCount = userSearchRepository.countByNameOrEmail(normalizedQuery)

        // Take only requested number and convert to domain
        val users =
            entities
                .take(pageSize)
                .map { it.toDomain() }

        return ConnectionBuilder.buildConnectionWithUuid(
            items = users,
            hasMore = hasMore,
            getId = { it.id },
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

        val user = entity.toDomain()

        // Validate domain state
        UserManagement.Validator.validateUserNotAlreadyEnabled(user)

        // Update entity
        entity.enabled = true
        val saved = userRepository.update(entity)

        logger.info { "User enabled: ${saved.email} (ID: ${saved.id})" }

        return saved.toDomain()
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
            userRepository.findById(userId)
                .orElseThrow { IllegalArgumentException("User not found with ID: $userId") }

        val user = entity.toDomain()

        // Validate domain state
        UserManagement.Validator.validateUserNotAlreadyDisabled(user)

        // Update entity
        entity.enabled = false
        val saved = userRepository.update(entity)

        logger.info { "User disabled: ${saved.email} (ID: ${saved.id})" }

        return saved.toDomain()
    }

    /**
     * Assign a role to a user.
     *
     * @param command Assign role command with userId and roleId
     * @return The updated user
     * @throws IllegalArgumentException if user or role not found
     */
    @Transactional
    open fun assignRoleToUser(command: UserManagement.AssignRoleToUserCommand): User {
        // Validate user and role exist before assigning
        val user =
            userRepository
                .findById(command.userId)
                .orElseThrow {
                    IllegalArgumentException("User not found with ID: ${command.userId}")
                }
        val role =
            roleRepository
                .findById(command.roleId)
                .orElseThrow {
                    IllegalArgumentException("Role not found with ID: ${command.roleId}")
                }

        // Check for existing assignment
        val existingAssignments = roleAssignmentRepository.findByUserIdAndRoleId(command.userId, command.roleId)
        if (existingAssignments.isNotEmpty()) {
            logger.info {
                "Role '${role.name}' already assigned to user '${user.email}' " +
                    "(User ID: ${command.userId}, Role ID: ${command.roleId})"
            }
            return user.toDomain()
        }

        // Create new assignment
        val assignment =
            RoleAssignment(
                userId = command.userId,
                roleId = command.roleId,
                validFrom = null,
                validUntil = null,
                createdAt = Instant.now(),
                updatedAt = Instant.now(),
            )
        roleAssignmentRepository.save(assignment.toEntity())

        logger.info {
            "Role '${role.name}' assigned to user '${user.email}' " +
                "(User ID: ${command.userId}, Role ID: ${command.roleId})"
        }

        return user.toDomain()
    }

    /**
     * Remove a role from a user.
     *
     * @param command Remove role command with userId and roleId
     * @return The updated user
     * @throws IllegalArgumentException if user or role not found
     */
    @Transactional
    open fun removeRoleFromUser(command: UserManagement.RemoveRoleFromUserCommand): User {
        // Validate user and role exist before removing
        val user =
            userRepository
                .findById(command.userId)
                .orElseThrow {
                    IllegalArgumentException("User not found with ID: ${command.userId}")
                }
        val role =
            roleRepository
                .findById(command.roleId)
                .orElseThrow {
                    IllegalArgumentException("Role not found with ID: ${command.roleId}")
                }

        // Find and delete assignment
        val assignments = roleAssignmentRepository.findByUserIdAndRoleId(command.userId, command.roleId)
        if (assignments.isNotEmpty()) {
            roleAssignmentRepository.deleteAll(assignments)
            logger.info {
                "Role '${role.name}' removed from user '${user.email}' " +
                    "(User ID: ${command.userId}, Role ID: ${command.roleId})"
            }
        } else {
            logger.info {
                "Role '${role.name}' was not assigned to user '${user.email}' " +
                    "(User ID: ${command.userId}, Role ID: ${command.roleId})"
            }
        }

        return user.toDomain()
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
}
