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

    /**
     * Validates and normalizes a page size parameter for GraphQL resolvers.
     * Handles null by defaulting to DEFAULT_PAGE_SIZE, and validates bounds.
     *
     * @param first Requested page size (nullable)
     * @return Validated page size
     * @throws IllegalArgumentException if page size is invalid (< 1 or > MAX_PAGE_SIZE)
     */
    fun validatePageSize(first: Int?): Int {
        return when {
            first == null -> DEFAULT_PAGE_SIZE
            first < 1 -> throw IllegalArgumentException("Parameter 'first' must be at least 1, got: $first")
            first > MAX_PAGE_SIZE -> throw IllegalArgumentException(
                "Parameter 'first' must be at most $MAX_PAGE_SIZE, got: $first",
            )
            else -> first
        }
    }

    /**
     * Validates a non-null page size parameter (for services that already handle null/default).
     * Throws IllegalArgumentException if page size is invalid.
     *
     * @param first Requested page size (non-null)
     * @return Validated page size
     * @throws IllegalArgumentException if page size is invalid (< 1 or > MAX_PAGE_SIZE)
     */
    fun validatePageSize(first: Int): Int {
        require(first in 1..MAX_PAGE_SIZE) {
            "Parameter 'first' must be between 1 and $MAX_PAGE_SIZE, got: $first"
        }
        return first
    }
}
