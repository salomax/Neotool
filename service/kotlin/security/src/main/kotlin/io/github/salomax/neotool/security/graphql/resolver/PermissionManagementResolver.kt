package io.github.salomax.neotool.security.graphql.resolver

import io.github.salomax.neotool.common.graphql.pagination.PaginationConstants
import io.github.salomax.neotool.security.graphql.dto.PermissionConnectionDTO
import io.github.salomax.neotool.security.graphql.mapper.PermissionManagementMapper
import io.github.salomax.neotool.security.repo.RoleRepository
import io.github.salomax.neotool.security.service.PermissionManagementService
import jakarta.inject.Singleton
import mu.KotlinLogging

/**
 * GraphQL resolver for permission management operations.
 * Provides queries for listing and searching permissions,
 * and relationship resolver for Permission.roles.
 */
@Singleton
class PermissionManagementResolver(
    private val permissionManagementService: PermissionManagementService,
    private val roleRepository: RoleRepository,
    private val mapper: PermissionManagementMapper,
) {
    private val logger = KotlinLogging.logger {}

    /**
     * List all permissions with optional pagination and search.
     */
    fun permissions(
        first: Int?,
        after: String?,
        query: String?,
    ): PermissionConnectionDTO {
        return try {
            val pageSize = first ?: PaginationConstants.DEFAULT_PAGE_SIZE
            val connection =
                if (query != null && query.isNotBlank()) {
                    permissionManagementService
                        .searchPermissions(query, pageSize, after)
                } else {
                    permissionManagementService
                        .listPermissions(pageSize, after)
                }
            mapper.toPermissionConnectionDTO(connection)
        } catch (e: Exception) {
            logger.error(e) { "Error listing permissions" }
            throw e
        }
    }

    /**
     * Resolve Permission.roles relationship.
     * Returns all roles that have this permission assigned.
     */
    fun resolvePermissionRoles(permissionId: String): List<io.github.salomax.neotool.security.graphql.dto.RoleDTO> {
        return try {
            val permissionIdInt = mapper.toPermissionId(permissionId)
            // Find roles that have this permission via role_permissions join table
            val roleIds = roleRepository.findRoleIdsByPermissionId(permissionIdInt)

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
            logger.error(e) { "Error resolving permission roles for permission: $permissionId" }
            emptyList()
        }
    }
}
