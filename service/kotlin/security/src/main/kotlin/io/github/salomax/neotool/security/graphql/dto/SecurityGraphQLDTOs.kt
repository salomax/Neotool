package io.github.salomax.neotool.security.graphql.dto

import io.micronaut.core.annotation.Introspected
import io.micronaut.serde.annotation.Serdeable
import io.github.salomax.neotool.graphql.BaseInputDTO
import jakarta.validation.constraints.*

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
    var rememberMe: Boolean? = false
) : BaseInputDTO()

@Introspected
@Serdeable
data class SignInPayloadDTO(
    val token: String,
    val refreshToken: String? = null,
    val user: UserDTO
)

@Introspected
@Serdeable
data class UserDTO(
    val id: String,
    val email: String,
    val displayName: String? = null
)

