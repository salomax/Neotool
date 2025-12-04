package io.github.salomax.neotool.security.graphql.mapper

import io.github.salomax.neotool.common.graphql.pagination.Connection
import io.github.salomax.neotool.common.graphql.pagination.OrderDirection
import io.github.salomax.neotool.security.domain.RoleManagement
import io.github.salomax.neotool.security.domain.rbac.Permission
import io.github.salomax.neotool.security.domain.rbac.Role
import io.github.salomax.neotool.security.graphql.dto.CreateRoleInputDTO
import io.github.salomax.neotool.security.graphql.dto.PageInfoDTO
import io.github.salomax.neotool.security.graphql.dto.PermissionDTO
import io.github.salomax.neotool.security.graphql.dto.RoleConnectionDTO
import io.github.salomax.neotool.security.graphql.dto.RoleDTO
import io.github.salomax.neotool.security.graphql.dto.RoleEdgeDTO
import io.github.salomax.neotool.security.graphql.dto.UpdateRoleInputDTO
import io.github.salomax.neotool.security.service.RoleOrderBy
import io.github.salomax.neotool.security.service.RoleOrderField
import jakarta.inject.Singleton

/**
 * Mapper for converting between role management domain objects and GraphQL DTOs.
 * Handles Role domain objects, Relay pagination connections, and input DTOs.
 */
@Singleton
class RoleManagementMapper {
    /**
     * Convert Role domain object to RoleDTO.
     */
    fun toRoleDTO(role: Role): RoleDTO {
        return RoleDTO(
            id = role.id?.toString() ?: throw IllegalArgumentException("Role must have an ID"),
            name = role.name,
        )
    }

    /**
     * Convert Connection<Role> to RoleConnectionDTO for Relay pagination.
     */
    fun toRoleConnectionDTO(connection: Connection<Role>): RoleConnectionDTO {
        val edges =
            connection
                .edges
                .map { edge ->
                    RoleEdgeDTO(
                        node = toRoleDTO(edge.node),
                        cursor = edge.cursor,
                    )
                }

        val nodes =
            connection
                .nodes
                .map { toRoleDTO(it) }

        val pageInfo =
            PageInfoDTO(
                hasNextPage = connection.pageInfo.hasNextPage,
                hasPreviousPage = connection.pageInfo.hasPreviousPage,
                startCursor = connection.pageInfo.startCursor,
                endCursor = connection.pageInfo.endCursor,
            )

        return RoleConnectionDTO(
            edges = edges,
            nodes = nodes,
            pageInfo = pageInfo,
            totalCount = connection.totalCount?.toInt(),
        )
    }

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
     * Convert list of Permission domain objects to list of PermissionDTO.
     */
    fun toPermissionDTOList(permissions: List<Permission>): List<PermissionDTO> {
        return permissions.map { toPermissionDTO(it) }
    }

    /**
     * Convert CreateRoleInputDTO to CreateRoleCommand.
     */
    fun toCreateRoleCommand(input: CreateRoleInputDTO): RoleManagement.CreateRoleCommand {
        return RoleManagement.CreateRoleCommand(
            name = input.name,
        )
    }

    /**
     * Convert UpdateRoleInputDTO to UpdateRoleCommand.
     */
    fun toUpdateRoleCommand(
        roleId: String,
        input: UpdateRoleInputDTO,
    ): RoleManagement.UpdateRoleCommand {
        return RoleManagement.UpdateRoleCommand(
            roleId = roleId.toInt(),
            name = input.name,
        )
    }

    /**
     * Convert String ID to Int.
     */
    fun toRoleId(id: String): Int {
        return id.toInt()
    }

    /**
     * Convert String ID to Int for permission ID.
     */
    fun toPermissionId(id: String): Int {
        return id.toInt()
    }

    /**
     * Extract field with type safety and default values
     * @param input The input map from GraphQL
     * @param name The field name to extract
     * @param defaultValue Optional default value if field is missing or null
     * @return The extracted value or default, or throws if required field is missing
     */
    inline fun <reified T> extractField(
        input: Map<String, Any?>,
        name: String,
        defaultValue: T? = null,
    ): T {
        val value = input[name]
        if (value == null) {
            return defaultValue ?: throw IllegalArgumentException("Field '$name' is required")
        }

        return if (value is T) {
            value
        } else {
            // Type doesn't match, use default if provided, otherwise throw
            defaultValue ?: throw IllegalArgumentException("Field '$name' is required")
        }
    }

    /**
     * Map GraphQL input map to CreateRoleInputDTO
     * @param input The GraphQL input map
     * @return CreateRoleInputDTO with extracted and validated fields
     */
    fun mapToCreateRoleInputDTO(input: Map<String, Any?>): CreateRoleInputDTO {
        return CreateRoleInputDTO(
            name = extractField(input, "name"),
        )
    }

    /**
     * Map GraphQL input map to UpdateRoleInputDTO
     * @param input The GraphQL input map
     * @return UpdateRoleInputDTO with extracted and validated fields
     */
    fun mapToUpdateRoleInputDTO(input: Map<String, Any?>): UpdateRoleInputDTO {
        return UpdateRoleInputDTO(
            name = extractField(input, "name"),
        )
    }

    /**
     * Convert GraphQL orderBy input list to service layer RoleOrderBy list.
     * Validates field names and directions.
     */
    fun toRoleOrderByList(orderBy: List<Map<String, Any?>>?): List<RoleOrderBy>? {
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
                    "NAME" -> RoleOrderField.NAME
                    else -> throw IllegalArgumentException("Invalid RoleOrderField: $fieldStr. Allowed: NAME")
                }

            val direction =
                when (directionStr) {
                    "ASC" -> OrderDirection.ASC
                    "DESC" -> OrderDirection.DESC
                    else -> throw IllegalArgumentException("Invalid OrderDirection: $directionStr. Allowed: ASC, DESC")
                }

            RoleOrderBy(field, direction)
        }
    }
}
