package io.github.salomax.neotool.security.domain

/**
 * Domain objects for permission management operations.
 * Provides command objects (DTOs) for permission management operations.
 *
 * Note: Search operations should be implemented at the repository layer using SQL queries,
 * not in-memory filtering.
 */
object PermissionManagement {
    /**
     * Search criteria for finding permissions.
     * Used as query parameters for repository search methods.
     * Actual filtering is done at the database level via SQL queries.
     */
    data class PermissionSearchCriteria(
        val query: String? = null,
    )
}
