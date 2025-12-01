package io.github.salomax.neotool.security.service

import io.github.salomax.neotool.common.graphql.pagination.Connection
import io.github.salomax.neotool.common.graphql.pagination.ConnectionBuilder
import io.github.salomax.neotool.common.graphql.pagination.CursorEncoder
import io.github.salomax.neotool.common.graphql.pagination.PaginationConstants
import io.github.salomax.neotool.security.domain.UserManagement
import io.github.salomax.neotool.security.domain.rbac.User
import io.github.salomax.neotool.security.repo.UserRepository
import jakarta.inject.Singleton
import jakarta.transaction.Transactional
import mu.KotlinLogging
import java.util.UUID

/**
 * Service for managing users.
 * Provides operations for listing, searching, enabling, and disabling users.
 */
@Singleton
open class UserManagementService(
    private val userRepository: UserRepository,
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
}
