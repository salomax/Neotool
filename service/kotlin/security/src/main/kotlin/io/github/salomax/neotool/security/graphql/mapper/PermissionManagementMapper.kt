package io.github.salomax.neotool.security.graphql.mapper

import io.github.salomax.neotool.common.graphql.pagination.Connection
import io.github.salomax.neotool.security.domain.rbac.Permission
import io.github.salomax.neotool.security.graphql.dto.PageInfoDTO
import io.github.salomax.neotool.security.graphql.dto.PermissionConnectionDTO
import io.github.salomax.neotool.security.graphql.dto.PermissionDTO
import io.github.salomax.neotool.security.graphql.dto.PermissionEdgeDTO
import jakarta.inject.Singleton

/**
 * Mapper for converting between permission management domain objects and GraphQL DTOs.
 * Handles Permission domain objects and Relay pagination connections.
 */
@Singleton
class PermissionManagementMapper {
    /**
     * Convert Permission domain object to PermissionDTO.
     */
    fun toPermissionDTO(permission: Permission): PermissionDTO {
        return PermissionDTO(
            id = permission.id?.toString() ?: throw IllegalArgumentException("Permission must have an ID"),
            name = permission.name,
        )
    }

    /**
     * Convert Connection<Permission> to PermissionConnectionDTO for Relay pagination.
     */
    fun toPermissionConnectionDTO(connection: Connection<Permission>): PermissionConnectionDTO {
        val edges =
            connection
                .edges
                .map { edge ->
                    PermissionEdgeDTO(
                        node = toPermissionDTO(edge.node),
                        cursor = edge.cursor,
                    )
                }

        val nodes =
            connection
                .nodes
                .map { toPermissionDTO(it) }

        val pageInfo =
            PageInfoDTO(
                hasNextPage = connection.pageInfo.hasNextPage,
                hasPreviousPage = connection.pageInfo.hasPreviousPage,
                startCursor = connection.pageInfo.startCursor,
                endCursor = connection.pageInfo.endCursor,
            )

        return PermissionConnectionDTO(
            edges = edges,
            nodes = nodes,
            pageInfo = pageInfo,
        )
    }

    /**
     * Convert String ID to Int.
     */
    fun toPermissionId(id: String): Int {
        return id.toInt()
    }
}
