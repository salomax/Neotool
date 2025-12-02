package io.github.salomax.neotool.security.domain

import java.util.UUID

/**
 * Domain objects for group management operations.
 * Provides command objects (DTOs) for group management operations.
 *
 * Note: Search operations should be implemented at the repository layer using SQL queries,
 * not in-memory filtering. Database constraints handle dependency validation.
 */
object GroupManagement {
    /**
     * Command to create a new group.
     * Includes input validation for domain rules (name length, required fields).
     */
    data class CreateGroupCommand(
        val name: String,
        val description: String? = null,
        val userIds: List<UUID>? = null,
    ) {
        init {
            require(name.isNotBlank()) { "Group name is required and cannot be blank" }
            require(name.length <= 255) { "Group name must be 255 characters or less" }
        }
    }

    /**
     * Command to update an existing group.
     * Includes input validation for domain rules (name length, required fields).
     */
    data class UpdateGroupCommand(
        val groupId: UUID,
        val name: String,
        val description: String? = null,
        val userIds: List<UUID>? = null,
    ) {
        init {
            require(name.isNotBlank()) { "Group name is required and cannot be blank" }
            require(name.length <= 255) { "Group name must be 255 characters or less" }
        }
    }

    /**
     * Command to delete a group.
     * Note: Database foreign key constraints will prevent deletion if dependencies exist.
     */
    data class DeleteGroupCommand(
        val groupId: java.util.UUID,
    )

    /**
     * Search criteria for finding groups.
     * Used as query parameters for repository search methods.
     * Actual filtering is done at the database level via SQL queries.
     */
    data class GroupSearchCriteria(
        val query: String? = null,
    )
}
