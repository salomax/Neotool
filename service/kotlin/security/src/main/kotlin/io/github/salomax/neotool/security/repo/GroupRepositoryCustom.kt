package io.github.salomax.neotool.security.repo

import io.github.salomax.neotool.security.model.rbac.GroupEntity
import java.util.UUID

/**
 * Custom query contract for {@link GroupRepository}.
 * Declared separately so Micronaut Data can generate the default repository implementation
 * while these methods are provided via a manual bean.
 */
interface GroupRepositoryCustom {

    /**
     * Search groups by name with cursor-based pagination.
     * Performs case-insensitive partial matching on name field when query is provided.
     * When query is null or empty, returns all groups (list behavior).
     * Results are ordered by name ascending.
     *
     * @param query Search query (partial match, case-insensitive). If null or empty, returns all groups.
     * @param first Maximum number of results to return
     * @param after Cursor (UUID) to start after (exclusive)
     * @return List of matching groups ordered by name ascending
     */
    fun searchByName(
        query: String?,
        first: Int,
        after: UUID?,
    ): List<GroupEntity>

    /**
     * Count groups matching the search query by name.
     * Performs case-insensitive partial matching on name field when query is provided.
     * When query is null or empty, returns total count of all groups.
     * This count matches the same WHERE clause as searchByName but without pagination.
     *
     * @param query Search query (partial match, case-insensitive). If null or empty, counts all groups.
     * @return Total count of matching groups (or all groups if query is null/empty)
     */
    fun countByName(query: String?): Long
}

