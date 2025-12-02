package io.github.salomax.neotool.security.service

import io.github.salomax.neotool.common.graphql.pagination.Connection
import io.github.salomax.neotool.common.graphql.pagination.ConnectionBuilder
import io.github.salomax.neotool.common.graphql.pagination.CursorEncoder
import io.github.salomax.neotool.common.graphql.pagination.PaginationConstants
import io.github.salomax.neotool.security.domain.UserManagement
import io.github.salomax.neotool.security.domain.rbac.RoleAssignment
import io.github.salomax.neotool.security.domain.rbac.User
import io.github.salomax.neotool.security.repo.RoleAssignmentRepository
import io.github.salomax.neotool.security.repo.RoleRepository
import io.github.salomax.neotool.security.repo.UserRepository
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
    private val roleAssignmentRepository: RoleAssignmentRepository,
    private val roleRepository: RoleRepository,
) {
    private val logger = KotlinLogging.logger {}

    /**
     * List all users with cursor-based pagination.
     *
     * @param first Maximum number of results to return (default: 20, max: 100)
     * @param after Cursor string for pagination (base64-encoded UUID)
     * @return Connection containing paginated users
     */
    fun listUsers(
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

        // Query one extra to check for more results
        val entities =
            userRepository.findAll(pageSize + 1, afterCursor)
        val hasMore = entities.size > pageSize

        // Take only requested number and convert to domain
        val users =
            entities
                .take(pageSize)
                .map { it.toDomain() }

        return ConnectionBuilder.buildConnectionWithUuid(
            items = users,
            hasMore = hasMore,
            getId = { it.id },
        )
    }

    /**
     * Search users by name or email with cursor-based pagination.
     *
     * @param query Search query (partial match, case-insensitive)
     * @param first Maximum number of results to return (default: 20, max: 100)
     * @param after Cursor string for pagination (base64-encoded UUID)
     * @return Connection containing paginated matching users
     */
    fun searchUsers(
        query: String,
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

        // Query one extra to check for more results
        val entities =
            userRepository.searchByNameOrEmail(query, pageSize + 1, afterCursor)
        val hasMore = entities.size > pageSize

        // Take only requested number and convert to domain
        val users =
            entities
                .take(pageSize)
                .map { it.toDomain() }

        return ConnectionBuilder.buildConnectionWithUuid(
            items = users,
            hasMore = hasMore,
            getId = { it.id },
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
}
