package io.github.salomax.neotool.common.graphql.pagination

import io.micronaut.core.annotation.Introspected
import io.micronaut.serde.annotation.Serdeable
import java.util.Base64
import java.util.UUID

/**
 * Relay GraphQL pagination utilities for building consistent pagination responses.
 *
 * This module provides reusable types and functions for implementing Relay-style
 * cursor-based pagination across all services.
 *
 * @see [Pagination Pattern Documentation](../../../../../../docs/04-patterns/backend-patterns/pagination-pattern.md)
 */

/**
 * PageInfo contains metadata about the pagination state.
 * Following Relay GraphQL specification.
 */
@Introspected
@Serdeable
data class PageInfo(
    val hasNextPage: Boolean,
    val hasPreviousPage: Boolean,
    val startCursor: String? = null,
    val endCursor: String? = null,
)

/**
 * Edge represents a single item in a paginated connection.
 * Following Relay GraphQL specification.
 */
@Introspected
@Serdeable
data class Edge<T>(
    val node: T,
    val cursor: String,
)

/**
 * Connection represents a paginated list of items.
 * Following Relay GraphQL specification.
 */
@Introspected
@Serdeable
data class Connection<T>(
    val edges: List<Edge<T>>,
    val nodes: List<T>,
    val pageInfo: PageInfo,
)

/**
 * Cursor encoding/decoding utilities for UUID-based cursors.
 */
object CursorEncoder {
    /**
     * Encode a UUID to a base64 URL-safe cursor string.
     *
     * @param id The UUID to encode
     * @return Base64 URL-safe encoded cursor string
     */
    fun encodeCursor(id: UUID): String {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(
            id.toString().toByteArray(),
        )
    }

    /**
     * Decode a base64 URL-safe cursor string to a UUID.
     *
     * @param cursor The cursor string to decode
     * @return Decoded UUID
     * @throws IllegalArgumentException if the cursor is invalid
     */
    fun decodeCursorToUuid(cursor: String): UUID {
        return try {
            val decoded = Base64.getUrlDecoder().decode(cursor)
            UUID.fromString(String(decoded))
        } catch (e: Exception) {
            throw IllegalArgumentException("Invalid cursor format: $cursor", e)
        }
    }

    /**
     * Encode an Int to a base64 URL-safe cursor string.
     *
     * @param id The Int to encode
     * @return Base64 URL-safe encoded cursor string
     */
    fun encodeCursor(id: Int): String {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(
            id.toString().toByteArray(),
        )
    }

    /**
     * Decode a base64 URL-safe cursor string to an Int.
     *
     * @param cursor The cursor string to decode
     * @return Decoded Int
     * @throws IllegalArgumentException if the cursor is invalid
     */
    fun decodeCursorToInt(cursor: String): Int {
        return try {
            val decoded = Base64.getUrlDecoder().decode(cursor)
            String(decoded).toInt()
        } catch (e: Exception) {
            throw IllegalArgumentException("Invalid cursor format: $cursor", e)
        }
    }

    /**
     * Decode a base64 URL-safe cursor string.
     * Automatically detects whether to decode as UUID or Int based on context.
     * For UUID cursors, use decodeCursorToUuid() explicitly.
     * For Int cursors, use decodeCursorToInt() explicitly.
     *
     * @param cursor The cursor string to decode
     * @return Decoded UUID (for backward compatibility with existing code)
     * @throws IllegalArgumentException if the cursor is invalid
     * @deprecated Use decodeCursorToUuid() or decodeCursorToInt() explicitly
     */
    @Deprecated("Use decodeCursorToUuid() or decodeCursorToInt() explicitly", ReplaceWith("decodeCursorToUuid(cursor)"))
    fun decodeCursor(cursor: String): UUID {
        return decodeCursorToUuid(cursor)
    }
}

/**
 * Helper functions for building Relay pagination connections.
 */
object ConnectionBuilder {
    /**
     * Build a Connection from a list of items with cursor encoding.
     *
     * @param items The list of items to paginate
     * @param hasMore Whether there are more items available (for hasNextPage)
     * @param encodeCursor Function to encode item ID to cursor string
     * @return Connection with edges, nodes, and pageInfo
     */
    fun <T> buildConnection(
        items: List<T>,
        hasMore: Boolean,
        encodeCursor: (T) -> String,
    ): Connection<T> {
        if (items.isEmpty()) {
            return Connection(
                edges = emptyList(),
                nodes = emptyList(),
                pageInfo = PageInfo(
                    hasNextPage = false,
                    hasPreviousPage = false,
                ),
            )
        }

        val edges = items.map { item ->
            Edge(
                node = item,
                cursor = encodeCursor(item),
            )
        }

        val pageInfo = PageInfo(
            hasNextPage = hasMore,
            hasPreviousPage = false, // Forward-only pagination (can be extended for backward)
            startCursor = edges.firstOrNull()?.cursor,
            endCursor = edges.lastOrNull()?.cursor,
        )

        return Connection(
            edges = edges,
            nodes = items,
            pageInfo = pageInfo,
        )
    }

    /**
     * Build a Connection from a list of items with UUID-based cursors.
     * Convenience method for entities with UUID primary keys.
     *
     * @param items The list of items to paginate
     * @param hasMore Whether there are more items available
     * @param getId Function to extract UUID from item
     * @return Connection with edges, nodes, and pageInfo
     */
    fun <T> buildConnectionWithUuid(
        items: List<T>,
        hasMore: Boolean,
        getId: (T) -> UUID?,
    ): Connection<T> {
        return buildConnection(
            items = items,
            hasMore = hasMore,
            encodeCursor = { item ->
                val id = getId(item) ?: throw IllegalArgumentException("Item must have a non-null ID")
                CursorEncoder.encodeCursor(id)
            },
        )
    }

    /**
     * Build a Connection from a list of items with Int-based cursors.
     * Convenience method for entities with Int primary keys.
     *
     * @param items The list of items to paginate
     * @param hasMore Whether there are more items available
     * @param getId Function to extract Int from item
     * @return Connection with edges, nodes, and pageInfo
     */
    fun <T> buildConnectionWithInt(
        items: List<T>,
        hasMore: Boolean,
        getId: (T) -> Int?,
    ): Connection<T> {
        return buildConnection(
            items = items,
            hasMore = hasMore,
            encodeCursor = { item ->
                val id = getId(item) ?: throw IllegalArgumentException("Item must have a non-null ID")
                CursorEncoder.encodeCursor(id)
            },
        )
    }
}

