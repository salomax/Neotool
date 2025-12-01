package io.github.salomax.neotool.security.service

import io.github.salomax.neotool.common.graphql.pagination.Connection
import io.github.salomax.neotool.common.graphql.pagination.ConnectionBuilder
import io.github.salomax.neotool.common.graphql.pagination.CursorEncoder
import io.github.salomax.neotool.common.graphql.pagination.PaginationConstants
import io.github.salomax.neotool.security.domain.GroupManagement
import io.github.salomax.neotool.security.domain.rbac.Group
import io.github.salomax.neotool.security.repo.GroupRepository
import jakarta.inject.Singleton
import jakarta.transaction.Transactional
import mu.KotlinLogging
import java.time.Instant
import java.util.UUID

/**
 * Service for managing groups.
 * Provides operations for listing, searching, creating, updating, and deleting groups.
 */
@Singleton
open class GroupManagementService(
    private val groupRepository: GroupRepository,
) {
    private val logger = KotlinLogging.logger {}

    /**
     * List all groups with cursor-based pagination.
     *
     * @param first Maximum number of results to return (default: 20, max: 100)
     * @param after Cursor string for pagination (base64-encoded UUID)
     * @return Connection containing paginated groups
     */
    fun listGroups(
        first: Int = PaginationConstants.DEFAULT_PAGE_SIZE,
        after: String?,
    ): Connection<Group> {
        val pageSize =
            minOf(first, PaginationConstants.MAX_PAGE_SIZE)

        val afterCursor =
            try {
                after?.let { CursorEncoder.decodeCursorToUuid(it) }
            } catch (e: Exception) {
                throw IllegalArgumentException("Invalid cursor: $after", e)
            }

        // Query one extra to check for more results
        val entities =
            groupRepository.findAll(pageSize + 1, afterCursor)
        val hasMore = entities.size > pageSize

        // Take only requested number and convert to domain
        val groups =
            entities
                .take(pageSize)
                .map { it.toDomain() }

        return ConnectionBuilder.buildConnectionWithUuid(
            items = groups,
            hasMore = hasMore,
            getId = { it.id },
        )
    }

    /**
     * Search groups by name with cursor-based pagination.
     *
     * @param query Search query (partial match, case-insensitive)
     * @param first Maximum number of results to return (default: 20, max: 100)
     * @param after Cursor string for pagination (base64-encoded UUID)
     * @return Connection containing paginated matching groups
     */
    fun searchGroups(
        query: String,
        first: Int = PaginationConstants.DEFAULT_PAGE_SIZE,
        after: String?,
    ): Connection<Group> {
        val pageSize =
            minOf(first, PaginationConstants.MAX_PAGE_SIZE)

        val afterCursor =
            try {
                after?.let { CursorEncoder.decodeCursorToUuid(it) }
            } catch (e: Exception) {
                throw IllegalArgumentException("Invalid cursor: $after", e)
            }

        // Query one extra to check for more results
        val entities =
            groupRepository.searchByName(query, pageSize + 1, afterCursor)
        val hasMore = entities.size > pageSize

        // Take only requested number and convert to domain
        val groups =
            entities
                .take(pageSize)
                .map { it.toDomain() }

        return ConnectionBuilder.buildConnectionWithUuid(
            items = groups,
            hasMore = hasMore,
            getId = { it.id },
        )
    }

    /**
     * Create a new group.
     *
     * @param command Create group command with name and optional description
     * @return The created group
     * @throws DataAccessException if group name already exists (database unique constraint)
     */
    @Transactional
    open fun createGroup(command: GroupManagement.CreateGroupCommand): Group {
        // Create domain object
        val group =
            Group(
                name = command.name,
                description = command.description,
                createdAt = Instant.now(),
                updatedAt = Instant.now(),
            )

        // Convert to entity and save
        val entity = group.toEntity()
        val saved = groupRepository.save(entity)

        logger.info { "Group created: ${saved.name} (ID: ${saved.id})" }

        return saved.toDomain()
    }

    /**
     * Update an existing group.
     *
     * @param command Update group command with groupId, name, and optional description
     * @return The updated group
     * @throws IllegalArgumentException if group not found
     * @throws DataAccessException if group name already exists (database unique constraint)
     */
    @Transactional
    open fun updateGroup(command: GroupManagement.UpdateGroupCommand): Group {
        val entity =
            groupRepository
                .findById(command.groupId)
                .orElseThrow {
                    IllegalArgumentException("Group not found with ID: ${command.groupId}")
                }

        // Update entity
        entity.name = command.name
        entity.description = command.description
        entity.updatedAt = Instant.now()

        val saved = groupRepository.update(entity)

        logger.info { "Group updated: ${saved.name} (ID: ${saved.id})" }

        return saved.toDomain()
    }

    /**
     * Delete a group.
     *
     * @param groupId The UUID of the group to delete
     * @throws IllegalArgumentException if group not found
     * @throws DataAccessException if group has dependencies (database foreign key constraint)
     */
    @Transactional
    open fun deleteGroup(groupId: UUID) {
        groupRepository.deleteById(groupId)

        logger.info { "Group deleted (ID: $groupId)" }
    }
}
