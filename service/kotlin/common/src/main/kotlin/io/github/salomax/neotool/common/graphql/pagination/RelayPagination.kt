package io.github.salomax.neotool.common.graphql.pagination

import com.fasterxml.jackson.databind.ObjectMapper
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
 * 
 * @param totalCount Optional total count of items matching the query (typically used for search operations).
 *                   This is separate from the paginated results and represents the total number of items
 *                   that match the search criteria, regardless of pagination.
 */
@Introspected
@Serdeable
data class Connection<T>(
    val edges: List<Edge<T>>,
    val nodes: List<T>,
    val pageInfo: PageInfo,
    val totalCount: Long? = null,
)

/**
 * Composite cursor data class for keyset pagination with multiple sort fields.
 * Encodes field values and id for proper cursor-based pagination.
 */
@Introspected
@Serdeable
data class CompositeCursor(
    val fieldValues: Map<String, Any?>,
    val id: String,
)

/**
 * Cursor encoding/decoding utilities for UUID-based cursors and composite cursors.
 */
object CursorEncoder {
    private val objectMapper = ObjectMapper()
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

    /**
     * Encode a composite cursor (field values + UUID id) to a base64 URL-safe cursor string.
     * Uses JSON encoding for the composite cursor structure.
     *
     * @param fieldValues Map of field names to their values from the ordered fields
     * @param id The UUID id of the entity
     * @return Base64 URL-safe encoded cursor string
     */
    fun encodeCompositeCursor(fieldValues: Map<String, Any?>, id: UUID): String {
        val composite = CompositeCursor(
            fieldValues = fieldValues,
            id = id.toString(),
        )
        val json = objectMapper.writeValueAsString(composite)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(json.toByteArray())
    }

    /**
     * Encode a composite cursor (field values + Int id) to a base64 URL-safe cursor string.
     * Uses JSON encoding for the composite cursor structure.
     *
     * @param fieldValues Map of field names to their values from the ordered fields
     * @param id The Int id of the entity
     * @return Base64 URL-safe encoded cursor string
     */
    fun encodeCompositeCursor(fieldValues: Map<String, Any?>, id: Int): String {
        val composite = CompositeCursor(
            fieldValues = fieldValues,
            id = id.toString(),
        )
        val json = objectMapper.writeValueAsString(composite)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(json.toByteArray())
    }

    /**
     * Decode a base64 URL-safe cursor string to a composite cursor with UUID id.
     *
     * @param cursor The cursor string to decode
     * @return Decoded CompositeCursor with UUID id
     * @throws IllegalArgumentException if the cursor is invalid
     */
    fun decodeCompositeCursorToUuid(cursor: String): CompositeCursor {
        return try {
            val decoded = Base64.getUrlDecoder().decode(cursor)
            val json = String(decoded)
            // Read as Map first, then construct CompositeCursor manually to handle Any? types
            @Suppress("UNCHECKED_CAST")
            val map = objectMapper.readValue(json, Map::class.java) as Map<String, Any?>
            val fieldValues = (map["fieldValues"] as? Map<*, *>)?.mapKeys { it.key.toString() }
                ?.mapValues { it.value } as? Map<String, Any?> ?: emptyMap()
            val id = map["id"] as? String ?: throw IllegalArgumentException("Missing id in cursor")
            val composite = CompositeCursor(fieldValues = fieldValues, id = id)
            // Validate that id is a valid UUID
            UUID.fromString(composite.id)
            composite
        } catch (e: Exception) {
            throw IllegalArgumentException("Invalid composite cursor format: $cursor", e)
        }
    }

    /**
     * Decode a base64 URL-safe cursor string to a composite cursor with Int id.
     *
     * @param cursor The cursor string to decode
     * @return Decoded CompositeCursor with Int id
     * @throws IllegalArgumentException if the cursor is invalid
     */
    fun decodeCompositeCursorToInt(cursor: String): CompositeCursor {
        return try {
            val decoded = Base64.getUrlDecoder().decode(cursor)
            val json = String(decoded)
            // Read as Map first, then construct CompositeCursor manually to handle Any? types
            @Suppress("UNCHECKED_CAST")
            val map = objectMapper.readValue(json, Map::class.java) as Map<String, Any?>
            val fieldValues = (map["fieldValues"] as? Map<*, *>)?.mapKeys { it.key.toString() }
                ?.mapValues { it.value } as? Map<String, Any?> ?: emptyMap()
            val id = map["id"] as? String ?: throw IllegalArgumentException("Missing id in cursor")
            val composite = CompositeCursor(fieldValues = fieldValues, id = id)
            // Validate that id is a valid Int
            composite.id.toInt()
            composite
        } catch (e: Exception) {
            throw IllegalArgumentException("Invalid composite cursor format: $cursor", e)
        }
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
     * @param totalCount Optional total count of items matching the query (typically used for search operations)
     * @return Connection with edges, nodes, and pageInfo
     */
    fun <T> buildConnection(
        items: List<T>,
        hasMore: Boolean,
        encodeCursor: (T) -> String,
        totalCount: Long? = null,
    ): Connection<T> {
        if (items.isEmpty()) {
            return Connection(
                edges = emptyList(),
                nodes = emptyList(),
                pageInfo = PageInfo(
                    hasNextPage = false,
                    hasPreviousPage = false,
                ),
                totalCount = totalCount,
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
            totalCount = totalCount,
        )
    }

    /**
     * Build a Connection from a list of items with UUID-based cursors.
     * Convenience method for entities with UUID primary keys.
     *
     * @param items The list of items to paginate
     * @param hasMore Whether there are more items available
     * @param getId Function to extract UUID from item
     * @param totalCount Optional total count of items matching the query (typically used for search operations)
     * @return Connection with edges, nodes, and pageInfo
     */
    fun <T> buildConnectionWithUuid(
        items: List<T>,
        hasMore: Boolean,
        getId: (T) -> UUID?,
        totalCount: Long? = null,
    ): Connection<T> {
        return buildConnection(
            items = items,
            hasMore = hasMore,
            encodeCursor = { item ->
                val id = getId(item) ?: throw IllegalArgumentException("Item must have a non-null ID")
                CursorEncoder.encodeCursor(id)
            },
            totalCount = totalCount,
        )
    }

    /**
     * Build a Connection from a list of items with Int-based cursors.
     * Convenience method for entities with Int primary keys.
     *
     * @param items The list of items to paginate
     * @param hasMore Whether there are more items available
     * @param getId Function to extract Int from item
     * @param totalCount Optional total count of items matching the query (typically used for search operations)
     * @return Connection with edges, nodes, and pageInfo
     */
    fun <T> buildConnectionWithInt(
        items: List<T>,
        hasMore: Boolean,
        getId: (T) -> Int?,
        totalCount: Long? = null,
    ): Connection<T> {
        return buildConnection(
            items = items,
            hasMore = hasMore,
            encodeCursor = { item ->
                val id = getId(item) ?: throw IllegalArgumentException("Item must have a non-null ID")
                CursorEncoder.encodeCursor(id)
            },
            totalCount = totalCount,
        )
    }
}

