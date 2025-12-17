package io.github.salomax.neotool.security.graphql.dto

import io.github.salomax.neotool.common.graphql.BaseInputDTO
import io.micronaut.core.annotation.Introspected
import io.micronaut.serde.annotation.Serdeable
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank

/**
 * Security module GraphQL DTOs
 */
@Introspected
@Serdeable
data class SignInInputDTO(
    @field:Email(message = "Email must be valid")
    @field:NotBlank(message = "Email is required")
    var email: String = "",
    @field:NotBlank(message = "Password is required")
    var password: String = "",
    var rememberMe: Boolean? = false,
) : BaseInputDTO()

@Introspected
@Serdeable
data class SignInPayloadDTO(
    val token: String,
    val refreshToken: String? = null,
    val user: UserDTO,
)

@Introspected
@Serdeable
data class SignUpInputDTO(
    @field:NotBlank(message = "Name is required")
    var name: String = "",
    @field:Email(message = "Email must be valid")
    @field:NotBlank(message = "Email is required")
    var email: String = "",
    @field:NotBlank(message = "Password is required")
    var password: String = "",
) : BaseInputDTO()

@Introspected
@Serdeable
data class SignUpPayloadDTO(
    val token: String,
    val refreshToken: String? = null,
    val user: UserDTO,
)

@Introspected
@Serdeable
data class UserDTO(
    val id: String,
    val email: String,
    val displayName: String? = null,
    val avatarUrl: String? = null,
    val enabled: Boolean? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
)

@Introspected
@Serdeable
data class RequestPasswordResetInputDTO(
    @field:Email(message = "Email must be valid")
    @field:NotBlank(message = "Email is required")
    var email: String = "",
    var locale: String? = "en",
) : BaseInputDTO()

@Introspected
@Serdeable
data class RequestPasswordResetPayloadDTO(
    val success: Boolean,
    val message: String,
)

@Introspected
@Serdeable
data class ResetPasswordInputDTO(
    @field:NotBlank(message = "Token is required")
    var token: String = "",
    @field:NotBlank(message = "Password is required")
    var newPassword: String = "",
) : BaseInputDTO()

@Introspected
@Serdeable
data class ResetPasswordPayloadDTO(
    val success: Boolean,
    val message: String,
)

// Authorization DTOs
@Introspected
@Serdeable
data class AuthorizationResultDTO(
    val allowed: Boolean,
    val reason: String,
)

@Introspected
@Serdeable
data class PermissionDTO(
    val id: String?,
    val name: String,
)

@Introspected
@Serdeable
data class RoleDTO(
    val id: String?,
    val name: String,
    val createdAt: String? = null,
    val updatedAt: String? = null,
)

@Introspected
@Serdeable
data class GroupDTO(
    val id: String,
    val name: String,
    val description: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
)

// Input DTOs for mutations
@Introspected
@Serdeable
data class CreateGroupInputDTO(
    @field:NotBlank(message = "Name is required")
    var name: String = "",
    var description: String? = null,
    var userIds: List<String>? = null,
) : BaseInputDTO()

@Introspected
@Serdeable
data class UpdateGroupInputDTO(
    @field:NotBlank(message = "Name is required")
    var name: String = "",
    var description: String? = null,
    var userIds: List<String>? = null,
) : BaseInputDTO()

@Introspected
@Serdeable
data class CreateRoleInputDTO(
    @field:NotBlank(message = "Name is required")
    var name: String = "",
) : BaseInputDTO()

@Introspected
@Serdeable
data class UpdateRoleInputDTO(
    @field:NotBlank(message = "Name is required")
    var name: String = "",
) : BaseInputDTO()

@Introspected
@Serdeable
data class UpdateUserInputDTO(
    var displayName: String? = null,
) : BaseInputDTO()

// Relay pagination DTOs
@Introspected
@Serdeable
data class PageInfoDTO(
    val hasNextPage: Boolean,
    val hasPreviousPage: Boolean,
    val startCursor: String? = null,
    val endCursor: String? = null,
)

@Introspected
@Serdeable
data class UserEdgeDTO(
    val node: UserDTO,
    val cursor: String,
)

@Introspected
@Serdeable
data class GroupEdgeDTO(
    val node: GroupDTO,
    val cursor: String,
)

@Introspected
@Serdeable
data class RoleEdgeDTO(
    val node: RoleDTO,
    val cursor: String,
)

@Introspected
@Serdeable
data class PermissionEdgeDTO(
    val node: PermissionDTO,
    val cursor: String,
)

@Introspected
@Serdeable
data class UserConnectionDTO(
    val edges: List<UserEdgeDTO>,
    val pageInfo: PageInfoDTO,
    val totalCount: Int? = null,
)

@Introspected
@Serdeable
data class GroupConnectionDTO(
    val edges: List<GroupEdgeDTO>,
    val pageInfo: PageInfoDTO,
    val totalCount: Int? = null,
)

@Introspected
@Serdeable
data class RoleConnectionDTO(
    val edges: List<RoleEdgeDTO>,
    val pageInfo: PageInfoDTO,
    val totalCount: Int? = null,
)

@Introspected
@Serdeable
data class PermissionConnectionDTO(
    val edges: List<PermissionEdgeDTO>,
    val pageInfo: PageInfoDTO,
)

// OrderBy DTOs
@Introspected
@Serdeable
data class UserOrderByInputDTO(
    val field: String,
    val direction: String,
)

@Introspected
@Serdeable
data class GroupOrderByInputDTO(
    val field: String,
    val direction: String,
)

@Introspected
@Serdeable
data class RoleOrderByInputDTO(
    val field: String,
    val direction: String,
)
