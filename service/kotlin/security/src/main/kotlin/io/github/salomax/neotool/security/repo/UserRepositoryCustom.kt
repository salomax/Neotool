package io.github.salomax.neotool.security.repo

import io.github.salomax.neotool.security.model.UserEntity
import java.util.UUID

/**
 * Custom query contract for {@link UserRepository}.
 * Declared separately so Micronaut Data can generate the default repository implementation
 * while these methods are provided via a manual bean.
 */
interface UserRepositoryCustom {

    /**
     * Search users by name or email with cursor-based pagination.
     * Performs case-insensitive partial matching on displayName and email fields when query is provided.
     * When query is null or empty, returns all users (list behavior).
     * Results are ordered by displayName (or email if displayName is null) ascending.
     *
     * @param query Search query (partial match, case-insensitive). If null or empty, returns all users.
     * @param first Maximum number of results to return
     * @param after Cursor (UUID) to start after (exclusive)
     * @return List of matching users ordered by name ascending
     */
    fun searchByNameOrEmail(
        query: String?,
        first: Int,
        after: UUID?,
    ): List<UserEntity>

    /**
     * Count users matching the search query by name or email.
     * Performs case-insensitive partial matching on displayName and email fields when query is provided.
     * When query is null or empty, returns total count of all users.
     * This count matches the same WHERE clause as searchByNameOrEmail but without pagination.
     *
     * @param query Search query (partial match, case-insensitive). If null or empty, counts all users.
     * @return Total count of matching users (or all users if query is null/empty)
     */
    fun countByNameOrEmail(query: String?): Long
}
