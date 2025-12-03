package io.github.salomax.neotool.security.graphql.resolver

import io.github.salomax.neotool.security.graphql.dto.UserConnectionDTO
import io.github.salomax.neotool.security.graphql.dto.UserDTO
import io.github.salomax.neotool.security.graphql.mapper.UserManagementMapper
import io.github.salomax.neotool.security.repo.GroupMembershipRepository
import io.github.salomax.neotool.security.repo.GroupRepository
import io.github.salomax.neotool.security.repo.UserRepository
import io.github.salomax.neotool.security.service.AuthorizationService
import io.github.salomax.neotool.security.service.UserManagementService
import jakarta.inject.Singleton
import mu.KotlinLogging
import java.util.UUID

/**
 * GraphQL resolver for user management operations.
 * Provides queries for listing and searching users, mutations for enabling/disabling users,
 * and relationship resolvers for User.roles, User.groups, and User.permissions.
 */
@Singleton
class UserManagementResolver(
    private val userManagementService: UserManagementService,
    private val authorizationService: AuthorizationService,
    private val userRepository: UserRepository,
    private val groupRepository: GroupRepository,
    private val groupMembershipRepository: GroupMembershipRepository,
    private val mapper: UserManagementMapper,
) {
    private val logger = KotlinLogging.logger {}

    /**
     * Get a single user by ID.
     */
    fun user(id: String): UserDTO? {
        return try {
            val userIdUuid = mapper.toUserId(id)
            val entity = userRepository.findById(userIdUuid)
            entity.map { it.toDomain() }
                .map { mapper.toUserDTO(it) }
                .orElse(null)
        } catch (e: IllegalArgumentException) {
            logger.warn { "Invalid user ID: $id" }
            null
        } catch (e: Exception) {
            logger.error(e) { "Error getting user: $id" }
            null
        }
    }

    /**
     * Unified query for users with optional pagination and search.
     * When query is omitted or empty, returns all users (list behavior).
     * When query is provided, returns filtered users (search behavior).
     * totalCount is always calculated.
     */
    fun users(
        first: Int?,
        after: String?,
        query: String?,
    ): UserConnectionDTO {
        return try {
            val pageSize = first ?: 20
            val connection = userManagementService.searchUsers(query, pageSize, after)
            mapper.toUserConnectionDTO(connection)
        } catch (e: Exception) {
            logger.error(e) { "Error listing users" }
            throw e
        }
    }

    /**
     * Enable a user account.
     */
    fun enableUser(userId: String): UserDTO {
        return try {
            val userIdUuid = mapper.toUserId(userId)
            val user = userManagementService.enableUser(userIdUuid)
            mapper.toUserDTO(user)
        } catch (e: IllegalArgumentException) {
            logger.warn { "Invalid user ID or user not found: $userId" }
            throw e
        } catch (e: Exception) {
            logger.error(e) { "Error enabling user: $userId" }
            throw e
        }
    }

    /**
     * Disable a user account.
     */
    fun disableUser(userId: String): UserDTO {
        return try {
            val userIdUuid = mapper.toUserId(userId)
            val user = userManagementService.disableUser(userIdUuid)
            mapper.toUserDTO(user)
        } catch (e: IllegalArgumentException) {
            logger.warn { "Invalid user ID or user not found: $userId" }
            throw e
        } catch (e: Exception) {
            logger.error(e) { "Error disabling user: $userId" }
            throw e
        }
    }

    /**
     * Resolve User.roles relationship.
     * Returns all roles assigned to the user (direct and group-inherited).
     */
    fun resolveUserRoles(userId: String): List<io.github.salomax.neotool.security.graphql.dto.RoleDTO> {
        return try {
            val userIdUuid = UUID.fromString(userId)
            val roles = authorizationService.getUserRoles(userIdUuid)
            roles.map { role ->
                io.github.salomax.neotool.security.graphql.dto.RoleDTO(
                    id = role.id?.toString() ?: throw IllegalArgumentException("Role must have an ID"),
                    name = role.name,
                )
            }
        } catch (e: Exception) {
            logger.error(e) { "Error resolving user roles for user: $userId" }
            emptyList()
        }
    }

    /**
     * Resolve User.groups relationship.
     * Returns all groups the user belongs to.
     */
    fun resolveUserGroups(userId: String): List<io.github.salomax.neotool.security.graphql.dto.GroupDTO> {
        return try {
            val userIdUuid = UUID.fromString(userId)
            val memberships = groupMembershipRepository.findActiveMembershipsByUserId(userIdUuid)
            val groupIds = memberships.map { it.groupId }

            if (groupIds.isEmpty()) {
                return emptyList()
            }

            val groups = groupRepository.findByIdIn(groupIds)
            groups.map { entity ->
                val group = entity.toDomain()
                io.github.salomax.neotool.security.graphql.dto.GroupDTO(
                    id = group.id?.toString() ?: throw IllegalArgumentException("Group must have an ID"),
                    name = group.name,
                    description = group.description,
                )
            }
        } catch (e: Exception) {
            logger.error(e) { "Error resolving user groups for user: $userId" }
            emptyList()
        }
    }

    /**
     * Resolve User.permissions relationship.
     * Returns all effective permissions for the user (direct and group-inherited).
     */
    fun resolveUserPermissions(userId: String): List<io.github.salomax.neotool.security.graphql.dto.PermissionDTO> {
        return try {
            val userIdUuid = UUID.fromString(userId)
            val permissions = authorizationService.getUserPermissions(userIdUuid)
            permissions.map { permission ->
                io.github.salomax.neotool.security.graphql.dto.PermissionDTO(
                    id = permission.id?.toString() ?: throw IllegalArgumentException("Permission must have an ID"),
                    name = permission.name,
                )
            }
        } catch (e: Exception) {
            logger.error(e) { "Error resolving user permissions for user: $userId" }
            emptyList()
        }
    }

    /**
     * Assign a role to a user.
     */
    fun assignRoleToUser(
        userId: String,
        roleId: String,
    ): UserDTO {
        return try {
            val command =
                io.github.salomax.neotool.security.domain.UserManagement.AssignRoleToUserCommand(
                    userId = mapper.toUserId(userId),
                    roleId = mapper.toRoleId(roleId),
                )
            val user = userManagementService.assignRoleToUser(command)
            mapper.toUserDTO(user)
        } catch (e: IllegalArgumentException) {
            logger.warn { "Invalid user ID or role ID: userId=$userId, roleId=$roleId" }
            throw e
        } catch (e: Exception) {
            logger.error(e) { "Error assigning role to user: userId=$userId, roleId=$roleId" }
            throw e
        }
    }

    /**
     * Remove a role from a user.
     */
    fun removeRoleFromUser(
        userId: String,
        roleId: String,
    ): UserDTO {
        return try {
            val command =
                io.github.salomax.neotool.security.domain.UserManagement.RemoveRoleFromUserCommand(
                    userId = mapper.toUserId(userId),
                    roleId = mapper.toRoleId(roleId),
                )
            val user = userManagementService.removeRoleFromUser(command)
            mapper.toUserDTO(user)
        } catch (e: IllegalArgumentException) {
            logger.warn { "Invalid user ID or role ID: userId=$userId, roleId=$roleId" }
            throw e
        } catch (e: Exception) {
            logger.error(e) { "Error removing role from user: userId=$userId, roleId=$roleId" }
            throw e
        }
    }

    /**
     * Assign a group to a user.
     */
    fun assignGroupToUser(
        userId: String,
        groupId: String,
    ): UserDTO {
        return try {
            val command =
                io.github.salomax.neotool.security.domain.UserManagement.AssignGroupToUserCommand(
                    userId = mapper.toUserId(userId),
                    groupId = mapper.toGroupId(groupId),
                )
            val user = userManagementService.assignGroupToUser(command)
            mapper.toUserDTO(user)
        } catch (e: IllegalArgumentException) {
            logger.warn { "Invalid user ID or group ID: userId=$userId, groupId=$groupId" }
            throw e
        } catch (e: Exception) {
            logger.error(e) { "Error assigning group to user: userId=$userId, groupId=$groupId" }
            throw e
        }
    }

    /**
     * Remove a group from a user.
     */
    fun removeGroupFromUser(
        userId: String,
        groupId: String,
    ): UserDTO {
        return try {
            val command =
                io.github.salomax.neotool.security.domain.UserManagement.RemoveGroupFromUserCommand(
                    userId = mapper.toUserId(userId),
                    groupId = mapper.toGroupId(groupId),
                )
            val user = userManagementService.removeGroupFromUser(command)
            mapper.toUserDTO(user)
        } catch (e: IllegalArgumentException) {
            logger.warn { "Invalid user ID or group ID: userId=$userId, groupId=$groupId" }
            throw e
        } catch (e: Exception) {
            logger.error(e) { "Error removing group from user: userId=$userId, groupId=$groupId" }
            throw e
        }
    }
}
