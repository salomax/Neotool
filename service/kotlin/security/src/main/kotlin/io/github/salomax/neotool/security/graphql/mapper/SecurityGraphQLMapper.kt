package io.github.salomax.neotool.security.graphql.mapper

import io.github.salomax.neotool.security.graphql.dto.UserDTO
import io.github.salomax.neotool.security.model.UserEntity
import jakarta.inject.Singleton

/**
 * Mapper for converting between security domain entities and GraphQL DTOs.
 * Separates mapping concerns from resolver logic for better testability and maintainability.
 */
@Singleton
class SecurityGraphQLMapper {
    /**
     * Convert UserEntity to UserDTO
     * @param user The user entity to convert
     * @return UserDTO with mapped fields
     */
    fun userToDTO(user: UserEntity): UserDTO {
        return UserDTO(
            id = user.id.toString(),
            email = user.email,
            displayName = user.displayName,
        )
    }
}


