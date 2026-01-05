package io.github.salomax.neotool.security.repo

import io.github.salomax.neotool.common.graphql.pagination.CompositeCursor
import io.github.salomax.neotool.security.model.RoleEntity
import io.github.salomax.neotool.security.model.RoleOrderBy

/**
 * Custom query contract for {@link RoleRepository}.
 * Declared separately so Micronaut Data can generate the default repository implementation
 * while these methods are provided via a manual bean.
 */
interface RoleRepositoryCustom {
    /**
     * Search roles by name with cursor-based pagination.
     * Performs case-insensitive partial matching on name field when query is provided.
     * When query is null or empty, returns all roles (list behavior).
     * Results are ordered according to the orderBy parameter.
     *
     * @param query Search query (partial match, case-insensitive). If null or empty, returns all roles.
     * @param first Maximum number of results to return
     * @param after Composite cursor to start after (exclusive), or null for first page
     * @param orderBy List of order by specifications (must include ID as final sort)
     * @return List of matching roles ordered according to orderBy
     */
    fun searchByName(
        query: String?,
        first: Int,
        after: CompositeCursor?,
        orderBy: List<RoleOrderBy>,
    ): List<RoleEntity>

    /**
     * Count roles matching the search query by name.
     * Performs case-insensitive partial matching on name field when query is provided.
     * When query is null or empty, returns total count of all roles.
     * This count matches the same WHERE clause as searchByName but without pagination.
     *
     * @param query Search query (partial match, case-insensitive). If null or empty, counts all roles.
     * @return Total count of matching roles (or all roles if query is null/empty)
     */
    fun countByName(query: String?): Long
}
