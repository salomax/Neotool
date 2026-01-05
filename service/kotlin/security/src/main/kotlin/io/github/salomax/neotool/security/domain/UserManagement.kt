package io.github.salomax.neotool.security.domain

import java.util.UUID

/**
 * Domain objects for user management operations.
 * Provides command objects (DTOs) for user management operations.
 *
 * Note: Search operations should be implemented at the repository layer using SQL queries,
 * not in-memory filtering. Database constraints handle dependency validation.
 */
object UserManagement {
    /**
     * Command to enable a user account.
     */
    data class EnableUserCommand(
        val userId: UUID,
    )

    /**
     * Command to disable a user account.
     */
    data class DisableUserCommand(
        val userId: UUID,
    )

    /**
     * Command to assign a group to a user.
     */
    data class AssignGroupToUserCommand(
        val userId: UUID,
        val groupId: UUID,
    )

    /**
     * Command to remove a group from a user.
     */
    data class RemoveGroupFromUserCommand(
        val userId: UUID,
        val groupId: UUID,
    )

    /**
     * Command to update basic user profile fields.
     * Currently only supports updating displayName; email is immutable.
     */
    data class UpdateUserCommand(
        val userId: UUID,
        val displayName: String?,
    ) {
        init {
            require(displayName == null || displayName.isNotBlank()) {
                "Display name cannot be blank if provided"
            }
        }
    }

    /**
     * Search criteria for finding users.
     * Used as query parameters for repository search methods.
     * Actual filtering is done at the database level via SQL queries.
     */
    data class UserSearchCriteria(
        val query: String? = null,
        val enabled: Boolean? = null,
    )

    /**
     * Domain validation for user state transitions.
     * These validate domain state, not database constraints.
     */
    object Validator {
        /**
         * Validates that a principal is not already enabled before enabling.
         * This is domain logic for state transitions.
         */
        fun validatePrincipalNotAlreadyEnabled(principal: io.github.salomax.neotool.security.model.Principal) {
            if (principal.enabled) {
                throw IllegalStateException("User is already enabled")
            }
        }

        /**
         * Validates that a principal is not already disabled before disabling.
         * This is domain logic for state transitions.
         */
        fun validatePrincipalNotAlreadyDisabled(principal: io.github.salomax.neotool.security.model.Principal) {
            if (!principal.enabled) {
                throw IllegalStateException("User is already disabled")
            }
        }
    }
}
