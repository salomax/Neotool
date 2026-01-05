package io.github.salomax.neotool.security.graphql.mapper

import io.github.salomax.neotool.common.graphql.pagination.Connection
import io.github.salomax.neotool.common.graphql.pagination.OrderDirection
import io.github.salomax.neotool.common.security.principal.PrincipalType
import io.github.salomax.neotool.security.domain.UserManagement
import io.github.salomax.neotool.security.domain.rbac.User
import io.github.salomax.neotool.security.graphql.dto.PageInfoDTO
import io.github.salomax.neotool.security.graphql.dto.UpdateUserInputDTO
import io.github.salomax.neotool.security.graphql.dto.UserConnectionDTO
import io.github.salomax.neotool.security.graphql.dto.UserDTO
import io.github.salomax.neotool.security.graphql.dto.UserEdgeDTO
import io.github.salomax.neotool.security.model.UserOrderBy
import io.github.salomax.neotool.security.model.UserOrderField
import io.github.salomax.neotool.security.repo.PrincipalRepository
import jakarta.inject.Singleton
import java.util.UUID

/**
 * Mapper for converting between user management domain objects and GraphQL DTOs.
 * Handles User domain objects and Relay pagination connections.
 */
@Singleton
class UserManagementMapper(
    private val principalRepository: PrincipalRepository,
) {
    /**
     * Convert User domain object to UserDTO.
     * Fetches enabled status from Principal.
     */
    fun toUserDTO(user: User): UserDTO {
        val userId = user.id ?: throw IllegalArgumentException("User must have an ID")
        val enabled = getPrincipalEnabled(userId)

        return UserDTO(
            id = userId.toString(),
            email = user.email,
            displayName = user.displayName,
            avatarUrl = user.avatarUrl,
            enabled = enabled,
            createdAt = user.createdAt.toString(),
            updatedAt = user.updatedAt.toString(),
        )
    }

    /**
     * Get enabled status from Principal for a user ID.
     * Returns true if no principal exists (default enabled).
     */
    private fun getPrincipalEnabled(userId: UUID): Boolean {
        return principalRepository
            .findByPrincipalTypeAndExternalId(PrincipalType.USER, userId.toString())
            .map { it.enabled }
            .orElse(true) // Default to enabled if no principal exists
    }

    /**
     * Convert Connection<User> to UserConnectionDTO for Relay pagination.
     * Uses batch fetching to avoid N+1 queries for Principal.enabled.
     */
    fun toUserConnectionDTO(connection: Connection<User>): UserConnectionDTO {
        // Batch fetch all principals for the users in this connection
        val userIds = connection.edges.mapNotNull { it.node.id }
        val principalsMap = batchFetchPrincipals(userIds)

        val edges =
            connection
                .edges
                .map { edge ->
                    UserEdgeDTO(
                        node = toUserDTO(edge.node, principalsMap),
                        cursor = edge.cursor,
                    )
                }

        val pageInfo =
            PageInfoDTO(
                hasNextPage = connection.pageInfo.hasNextPage,
                hasPreviousPage = connection.pageInfo.hasPreviousPage,
                startCursor = connection.pageInfo.startCursor,
                endCursor = connection.pageInfo.endCursor,
            )

        return UserConnectionDTO(
            edges = edges,
            pageInfo = pageInfo,
            totalCount = connection.totalCount?.toInt(),
        )
    }

    /**
     * Convert User domain object to UserDTO using pre-fetched principals map.
     * Used for batch operations to avoid N+1 queries.
     */
    private fun toUserDTO(
        user: User,
        principalsMap: Map<String, Boolean>,
    ): UserDTO {
        val userId = user.id ?: throw IllegalArgumentException("User must have an ID")
        val enabled = principalsMap[userId.toString()] ?: true // Default to enabled if not found

        return UserDTO(
            id = userId.toString(),
            email = user.email,
            displayName = user.displayName,
            avatarUrl = user.avatarUrl,
            enabled = enabled,
            createdAt = user.createdAt.toString(),
            updatedAt = user.updatedAt.toString(),
        )
    }

    /**
     * Batch fetch principals for multiple user IDs.
     * Returns a map of external ID (user UUID string) to enabled status.
     */
    private fun batchFetchPrincipals(userIds: List<UUID>): Map<String, Boolean> {
        if (userIds.isEmpty()) {
            return emptyMap()
        }

        val externalIds = userIds.map { it.toString() }
        return principalRepository
            .findByPrincipalTypeAndExternalIdIn(PrincipalType.USER, externalIds)
            .associate { it.externalId to it.enabled }
    }

    /**
     * Convert String ID to UUID.
     */
    fun toUserId(id: String): UUID = UUID.fromString(id)

    /**
     * Convert String ID to UUID for role ID.
     */
    fun toRoleId(id: String): UUID = UUID.fromString(id)

    /**
     * Convert String ID to UUID for group ID.
     */
    fun toGroupId(id: String): UUID = UUID.fromString(id)

    /**
     * Convert GraphQL orderBy input list to service layer UserOrderBy list.
     * Validates field names and directions.
     */
    fun toUserOrderByList(orderBy: List<Map<String, Any?>>?): List<UserOrderBy>? {
        if (orderBy == null || orderBy.isEmpty()) {
            return null
        }

        return orderBy.map { orderByMap ->
            val fieldStr =
                orderByMap["field"] as? String
                    ?: throw IllegalArgumentException("orderBy field is required")
            val directionStr =
                orderByMap["direction"] as? String
                    ?: throw IllegalArgumentException("orderBy direction is required")

            val field =
                when (fieldStr) {
                    "DISPLAY_NAME" -> {
                        UserOrderField.DISPLAY_NAME
                    }

                    "EMAIL" -> {
                        UserOrderField.EMAIL
                    }

                    else -> {
                        throw IllegalArgumentException(
                            "Invalid UserOrderField: $fieldStr. Allowed: DISPLAY_NAME, EMAIL",
                        )
                    }
                }

            val direction =
                when (directionStr) {
                    "ASC" -> OrderDirection.ASC
                    "DESC" -> OrderDirection.DESC
                    else -> throw IllegalArgumentException("Invalid OrderDirection: $directionStr. Allowed: ASC, DESC")
                }

            UserOrderBy(field, direction)
        }
    }

    /**
     * Convert UpdateUserInputDTO and userId to UpdateUserCommand.
     */
    fun toUpdateUserCommand(
        userId: String,
        input: UpdateUserInputDTO,
    ): UserManagement.UpdateUserCommand {
        val userIdUuid = toUserId(userId)
        return UserManagement.UpdateUserCommand(
            userId = userIdUuid,
            displayName = input.displayName?.takeIf { it.isNotBlank() },
        )
    }

    /**
     * Map GraphQL input map to UpdateUserInputDTO.
     */
    fun mapToUpdateUserInputDTO(input: Map<String, Any?>): UpdateUserInputDTO =
        UpdateUserInputDTO(
            displayName = extractField<String?>(input, "displayName", null),
        )

    /**
     * Extract field with type safety and default values.
     * Throws IllegalArgumentException if field is missing (when no default) or has wrong type.
     */
    private inline fun <reified T> extractField(
        input: Map<String, Any?>,
        name: String,
        defaultValue: T? = null,
    ): T {
        val value = input[name]
        if (value == null) {
            return defaultValue ?: throw IllegalArgumentException("Field '$name' is required")
        }
        if (value !is T) {
            throw IllegalArgumentException(
                "Field '$name' has invalid type. Expected ${T::class.simpleName}, got ${value::class.simpleName}",
            )
        }
        return value
    }
}
