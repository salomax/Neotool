package io.github.salomax.neotool.security.graphql.resolver

import io.github.salomax.neotool.security.graphql.dto.CreateGroupInputDTO
import io.github.salomax.neotool.security.graphql.dto.GroupConnectionDTO
import io.github.salomax.neotool.security.graphql.dto.GroupDTO
import io.github.salomax.neotool.security.graphql.dto.UpdateGroupInputDTO
import io.github.salomax.neotool.security.graphql.mapper.GroupManagementMapper
import io.github.salomax.neotool.security.repo.GroupMembershipRepository
import io.github.salomax.neotool.security.repo.GroupRoleAssignmentRepository
import io.github.salomax.neotool.security.repo.RoleRepository
import io.github.salomax.neotool.security.repo.UserRepository
import io.github.salomax.neotool.security.service.GroupManagementService
import jakarta.inject.Singleton
import mu.KotlinLogging
import java.time.Instant
import java.util.UUID

/**
 * GraphQL resolver for group management operations.
 * Provides queries for listing and searching groups, CRUD mutations,
 * and relationship resolvers for Group.roles and Group.members.
 */
@Singleton
class GroupManagementResolver(
    private val groupManagementService: GroupManagementService,
    private val groupRoleAssignmentRepository: GroupRoleAssignmentRepository,
    private val groupMembershipRepository: GroupMembershipRepository,
    private val roleRepository: RoleRepository,
    private val userRepository: UserRepository,
    private val mapper: GroupManagementMapper,
) {
    private val logger = KotlinLogging.logger {}

    /**
     * List all groups with optional pagination and search.
     */
    fun groups(
        first: Int?,
        after: String?,
        query: String?,
    ): GroupConnectionDTO {
        return try {
            val pageSize = first ?: 20
            val connection =
                if (query != null && query.isNotBlank()) {
                    groupManagementService
                        .searchGroups(query, pageSize, after)
                } else {
                    groupManagementService
                        .listGroups(pageSize, after)
                }
            mapper.toGroupConnectionDTO(connection)
        } catch (e: Exception) {
            logger.error(e) { "Error listing groups" }
            throw e
        }
    }

    /**
     * Create a new group.
     */
    fun createGroup(input: CreateGroupInputDTO): GroupDTO {
        return try {
            val command = mapper.toCreateGroupCommand(input)
            val group = groupManagementService.createGroup(command)
            mapper.toGroupDTO(group)
        } catch (e: Exception) {
            logger.error(e) { "Error creating group" }
            throw e
        }
    }

    /**
     * Update an existing group.
     */
    fun updateGroup(
        groupId: String,
        input: UpdateGroupInputDTO,
    ): GroupDTO {
        return try {
            val command = mapper.toUpdateGroupCommand(groupId, input)
            val group = groupManagementService.updateGroup(command)
            mapper.toGroupDTO(group)
        } catch (e: IllegalArgumentException) {
            logger.warn { "Invalid group ID or group not found: $groupId" }
            throw e
        } catch (e: Exception) {
            logger.error(e) { "Error updating group: $groupId" }
            throw e
        }
    }

    /**
     * Delete a group.
     */
    fun deleteGroup(groupId: String): Boolean {
        return try {
            val groupIdUuid = mapper.toGroupId(groupId)
            groupManagementService.deleteGroup(groupIdUuid)
            true
        } catch (e: IllegalArgumentException) {
            logger.warn { "Invalid group ID or group not found: $groupId" }
            throw e
        } catch (e: Exception) {
            logger.error(e) { "Error deleting group: $groupId" }
            throw e
        }
    }

    /**
     * Resolve Group.roles relationship.
     * Returns all roles assigned to the group.
     */
    fun resolveGroupRoles(groupId: String): List<io.github.salomax.neotool.security.graphql.dto.RoleDTO> {
        return try {
            val groupIdUuid = UUID.fromString(groupId)
            val assignments = groupRoleAssignmentRepository.findValidAssignmentsByGroupId(groupIdUuid, Instant.now())
            val roleIds = assignments.map { it.roleId }.distinct()

            if (roleIds.isEmpty()) {
                return emptyList()
            }

            val roles = roleRepository.findByIdIn(roleIds)
            roles.map { entity ->
                val role = entity.toDomain()
                io.github.salomax.neotool.security.graphql.dto.RoleDTO(
                    id = role.id?.toString() ?: throw IllegalArgumentException("Role must have an ID"),
                    name = role.name,
                )
            }
        } catch (e: Exception) {
            logger.error(e) { "Error resolving group roles for group: $groupId" }
            emptyList()
        }
    }

    /**
     * Resolve Group.members relationship.
     * Returns all users who are members of the group.
     */
    fun resolveGroupMembers(groupId: String): List<io.github.salomax.neotool.security.graphql.dto.UserDTO> {
        return try {
            val groupIdUuid = UUID.fromString(groupId)
            val memberships = groupMembershipRepository.findByGroupId(groupIdUuid)
            val userIds = memberships.map { it.userId }.distinct()

            if (userIds.isEmpty()) {
                return emptyList()
            }

            val users = userRepository.findByIdIn(userIds)
            users.map { entity ->
                val user = entity.toDomain()
                io.github.salomax.neotool.security.graphql.dto.UserDTO(
                    id = user.id?.toString() ?: throw IllegalArgumentException("User must have an ID"),
                    email = user.email,
                    displayName = user.displayName,
                    enabled = user.enabled,
                )
            }
        } catch (e: Exception) {
            logger.error(e) { "Error resolving group members for group: $groupId" }
            emptyList()
        }
    }
}
