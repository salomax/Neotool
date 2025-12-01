package io.github.salomax.neotool.security.service

import io.github.salomax.neotool.common.graphql.pagination.Connection
import io.github.salomax.neotool.common.graphql.pagination.ConnectionBuilder
import io.github.salomax.neotool.common.graphql.pagination.CursorEncoder
import io.github.salomax.neotool.common.graphql.pagination.PaginationConstants
import io.github.salomax.neotool.security.domain.rbac.Permission
import io.github.salomax.neotool.security.repo.PermissionRepository
import jakarta.inject.Singleton
import mu.KotlinLogging

/**
 * Service for managing permissions.
 * Provides read-only operations for listing and searching permissions.
 */
@Singleton
class PermissionManagementService(
    private val permissionRepository: PermissionRepository,
) {
    private val logger = KotlinLogging.logger {}

    /**
     * List all permissions with cursor-based pagination.
     *
     * @param first Maximum number of results to return (default: 20, max: 100)
     * @param after Cursor string for pagination (base64-encoded Int)
     * @return Connection containing paginated permissions
     */
    fun listPermissions(
        first: Int = PaginationConstants.DEFAULT_PAGE_SIZE,
        after: String?,
    ): Connection<Permission> {
        val pageSize =
            minOf(first, PaginationConstants.MAX_PAGE_SIZE)

        val afterCursor =
            try {
                after?.let { CursorEncoder.decodeCursorToInt(it) }
            } catch (e: Exception) {
                throw IllegalArgumentException("Invalid cursor: $after", e)
            }

        // Query one extra to check for more results
        val entities =
            permissionRepository.findAll(pageSize + 1, afterCursor)
        val hasMore = entities.size > pageSize

        // Take only requested number and convert to domain
        val permissions =
            entities
                .take(pageSize)
                .map { it.toDomain() }

        return ConnectionBuilder.buildConnectionWithInt(
            items = permissions,
            hasMore = hasMore,
            getId = { it.id },
        )
    }

    /**
     * Search permissions by name with cursor-based pagination.
     *
     * @param query Search query (partial match, case-insensitive)
     * @param first Maximum number of results to return (default: 20, max: 100)
     * @param after Cursor string for pagination (base64-encoded Int)
     * @return Connection containing paginated matching permissions
     */
    fun searchPermissions(
        query: String,
        first: Int = PaginationConstants.DEFAULT_PAGE_SIZE,
        after: String?,
    ): Connection<Permission> {
        val pageSize =
            minOf(first, PaginationConstants.MAX_PAGE_SIZE)

        val afterCursor =
            try {
                after?.let { CursorEncoder.decodeCursorToInt(it) }
            } catch (e: Exception) {
                throw IllegalArgumentException("Invalid cursor: $after", e)
            }

        // Query one extra to check for more results
        val entities =
            permissionRepository.searchByName(query, pageSize + 1, afterCursor)
        val hasMore = entities.size > pageSize

        // Take only requested number and convert to domain
        val permissions =
            entities
                .take(pageSize)
                .map { it.toDomain() }

        return ConnectionBuilder.buildConnectionWithInt(
            items = permissions,
            hasMore = hasMore,
            getId = { it.id },
        )
    }
}
