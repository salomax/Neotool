package io.github.salomax.neotool.security.graphql.mapper

import io.github.salomax.neotool.common.graphql.pagination.Connection
import io.github.salomax.neotool.security.domain.GroupManagement
import io.github.salomax.neotool.security.domain.rbac.Group
import io.github.salomax.neotool.security.graphql.dto.CreateGroupInputDTO
import io.github.salomax.neotool.security.graphql.dto.GroupConnectionDTO
import io.github.salomax.neotool.security.graphql.dto.GroupDTO
import io.github.salomax.neotool.security.graphql.dto.GroupEdgeDTO
import io.github.salomax.neotool.security.graphql.dto.PageInfoDTO
import io.github.salomax.neotool.security.graphql.dto.UpdateGroupInputDTO
import jakarta.inject.Singleton
import java.util.UUID

/**
 * Mapper for converting between group management domain objects and GraphQL DTOs.
 * Handles Group domain objects, Relay pagination connections, and input DTOs.
 */
@Singleton
class GroupManagementMapper {
    /**
     * Convert Group domain object to GroupDTO.
     */
    fun toGroupDTO(group: Group): GroupDTO {
        return GroupDTO(
            id = group.id?.toString() ?: throw IllegalArgumentException("Group must have an ID"),
            name = group.name,
            description = group.description,
        )
    }

    /**
     * Convert Connection<Group> to GroupConnectionDTO for Relay pagination.
     */
    fun toGroupConnectionDTO(connection: Connection<Group>): GroupConnectionDTO {
        val edges =
            connection
                .edges
                .map { edge ->
                    GroupEdgeDTO(
                        node = toGroupDTO(edge.node),
                        cursor = edge.cursor,
                    )
                }

        val nodes =
            connection
                .nodes
                .map { toGroupDTO(it) }

        val pageInfo =
            PageInfoDTO(
                hasNextPage = connection.pageInfo.hasNextPage,
                hasPreviousPage = connection.pageInfo.hasPreviousPage,
                startCursor = connection.pageInfo.startCursor,
                endCursor = connection.pageInfo.endCursor,
            )

        return GroupConnectionDTO(
            edges = edges,
            nodes = nodes,
            pageInfo = pageInfo,
        )
    }

    /**
     * Convert CreateGroupInputDTO to CreateGroupCommand.
     */
    fun toCreateGroupCommand(input: CreateGroupInputDTO): GroupManagement.CreateGroupCommand {
        return GroupManagement.CreateGroupCommand(
            name = input.name,
            description = input.description,
        )
    }

    /**
     * Convert UpdateGroupInputDTO to UpdateGroupCommand.
     */
    fun toUpdateGroupCommand(
        groupId: String,
        input: UpdateGroupInputDTO,
    ): GroupManagement.UpdateGroupCommand {
        return GroupManagement.UpdateGroupCommand(
            groupId = UUID.fromString(groupId),
            name = input.name,
            description = input.description,
        )
    }

    /**
     * Convert String ID to UUID.
     */
    fun toGroupId(id: String): UUID {
        return UUID.fromString(id)
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
     * Map GraphQL input map to CreateGroupInputDTO
     * @param input The GraphQL input map
     * @return CreateGroupInputDTO with extracted and validated fields
     */
    fun mapToCreateGroupInputDTO(input: Map<String, Any?>): CreateGroupInputDTO {
        return CreateGroupInputDTO(
            name = extractField<String>(input, "name"),
            description = extractField<String?>(input, "description", null),
        )
    }
}
