package io.github.salomax.neotool.common.graphql.pagination

/**
 * Common pagination constants for Relay GraphQL pagination.
 * Services can override these values if needed by defining their own constants.
 */
object PaginationConstants {
    /**
     * Default page size for paginated queries.
     */
    const val DEFAULT_PAGE_SIZE = 20

    /**
     * Maximum page size to prevent performance issues.
     */
    const val MAX_PAGE_SIZE = 100
}

