package io.github.salomax.neotool.security.graphql.mapper

import io.github.salomax.neotool.security.domain.rbac.Permission
import io.github.salomax.neotool.security.domain.rbac.Role
import io.github.salomax.neotool.security.graphql.dto.AuthorizationResultDTO
import io.github.salomax.neotool.security.graphql.dto.PermissionDTO
import io.github.salomax.neotool.security.graphql.dto.RoleDTO
import io.github.salomax.neotool.security.service.AuthorizationResult
import jakarta.inject.Singleton

/**
 * Mapper for converting between authorization domain objects and GraphQL DTOs.
 * Separates mapping concerns from resolver logic for better testability and maintainability.
 */
@Singleton
class AuthorizationMapper {
    /**
     * Convert AuthorizationResult to AuthorizationResultDTO.
     */
    fun toAuthorizationResultDTO(result: AuthorizationResult): AuthorizationResultDTO {
        return AuthorizationResultDTO(
            allowed = result.allowed,
            reason = result.reason,
        )
    }

    /**
     * Convert Permission domain object to PermissionDTO.
     */
    fun toPermissionDTO(permission: Permission): PermissionDTO {
        return PermissionDTO(
            id = permission.id?.toString(),
            name = permission.name,
        )
    }

    /**
     * Convert Role domain object to RoleDTO.
     */
    fun toRoleDTO(role: Role): RoleDTO {
        return RoleDTO(
            id = role.id?.toString(),
            name = role.name,
            createdAt = role.createdAt.toString(),
            updatedAt = role.updatedAt.toString(),
        )
    }
}
