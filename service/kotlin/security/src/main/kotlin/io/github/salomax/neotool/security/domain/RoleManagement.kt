package io.github.salomax.neotool.security.domain

import java.util.UUID

/**
 * Domain objects for role management operations.
 * Provides command objects (DTOs) for role management operations.
 *
 * Note: Search operations should be implemented at the repository layer using SQL queries,
 * not in-memory filtering. Database constraints handle dependency validation.
 */
object RoleManagement {
    /**
     * Command to create a new role.
     * Includes input validation for domain rules (name length, required fields).
     */
    data class CreateRoleCommand(
        val name: String,
    ) {
        init {
            require(name.isNotBlank()) { "Role name is required and cannot be blank" }
            require(name.length <= 64) { "Role name must be 64 characters or less" }
        }
    }

    /**
     * Command to update an existing role.
     * Includes input validation for domain rules (name length, required fields).
     */
    data class UpdateRoleCommand(
        val roleId: UUID,
        val name: String,
    ) {
        init {
            require(name.isNotBlank()) { "Role name is required and cannot be blank" }
            require(name.length <= 64) { "Role name must be 64 characters or less" }
        }
    }

    /**
     * Command to delete a role.
     * Note: Database foreign key constraints will prevent deletion if dependencies exist.
     */
    data class DeleteRoleCommand(
        val roleId: UUID,
    )

    /**
     * Command to assign a permission to a role.
     */
    data class AssignPermissionCommand(
        val roleId: UUID,
        val permissionId: UUID,
    )

    /**
     * Command to remove a permission from a role.
     */
    data class RemovePermissionCommand(
        val roleId: UUID,
        val permissionId: UUID,
    )

    /**
     * Search criteria for finding roles.
     * Used as query parameters for repository search methods.
     * Actual filtering is done at the database level via SQL queries.
     */
    data class RoleSearchCriteria(
        val query: String? = null,
    )
}
