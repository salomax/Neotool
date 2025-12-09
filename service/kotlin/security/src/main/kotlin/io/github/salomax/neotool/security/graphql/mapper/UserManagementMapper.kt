package io.github.salomax.neotool.security.graphql.mapper

import io.github.salomax.neotool.common.graphql.pagination.Connection
import io.github.salomax.neotool.common.graphql.pagination.OrderDirection
import io.github.salomax.neotool.security.domain.rbac.User
import io.github.salomax.neotool.security.graphql.dto.PageInfoDTO
import io.github.salomax.neotool.security.graphql.dto.UserConnectionDTO
import io.github.salomax.neotool.security.graphql.dto.UserDTO
import io.github.salomax.neotool.security.graphql.dto.UserEdgeDTO
import io.github.salomax.neotool.security.service.UserOrderBy
import io.github.salomax.neotool.security.service.UserOrderField
import jakarta.inject.Singleton
import java.util.UUID

/**
 * Mapper for converting between user management domain objects and GraphQL DTOs.
 * Handles User domain objects and Relay pagination connections.
 */
@Singleton
class UserManagementMapper {
    /**
     * Convert User domain object to UserDTO.
     */
    fun toUserDTO(user: User): UserDTO {
        return UserDTO(
            id = user.id?.toString() ?: throw IllegalArgumentException("User must have an ID"),
            email = user.email,
            displayName = user.displayName,
            enabled = user.enabled,
        )
    }

    /**
     * Convert Connection<User> to UserConnectionDTO for Relay pagination.
     */
    fun toUserConnectionDTO(connection: Connection<User>): UserConnectionDTO {
        val edges =
            connection
                .edges
                .map { edge ->
                    UserEdgeDTO(
                        node = toUserDTO(edge.node),
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
     * Convert String ID to UUID.
     */
    fun toUserId(id: String): UUID {
        return UUID.fromString(id)
    }

    /**
     * Convert String ID to Int for role ID.
     */
    fun toRoleId(id: String): Int {
        return id.toInt()
    }

    /**
     * Convert String ID to UUID for group ID.
     */
    fun toGroupId(id: String): UUID {
        return UUID.fromString(id)
    }

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
                    "DISPLAY_NAME" -> UserOrderField.DISPLAY_NAME
                    "EMAIL" -> UserOrderField.EMAIL
                    "ENABLED" -> UserOrderField.ENABLED
                    else ->
                        throw IllegalArgumentException(
                            "Invalid UserOrderField: $fieldStr. Allowed: DISPLAY_NAME, EMAIL, ENABLED",
                        )
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
}
