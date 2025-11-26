package io.github.salomax.neotool.security.test.unit.graphql.mapper

import io.github.salomax.neotool.security.graphql.dto.UserDTO
import io.github.salomax.neotool.security.graphql.mapper.SecurityGraphQLMapper
import io.github.salomax.neotool.security.model.UserEntity
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

@DisplayName("SecurityGraphQLMapper Unit Tests")
class SecurityGraphQLMapperTest {
    private lateinit var mapper: SecurityGraphQLMapper

    @BeforeEach
    fun setUp() {
        mapper = SecurityGraphQLMapper()
    }

    @Nested
    @DisplayName("userToDTO()")
    inner class UserToDTOTests {
        @Test
        fun `should map UserEntity to UserDTO with all fields`() {
            // Arrange
            val userId = UUID.randomUUID()
            val user =
                UserEntity(
                    id = userId,
                    email = "test@example.com",
                    displayName = "Test User",
                    passwordHash = "hashed",
                    createdAt = Instant.now(),
                )

            // Act
            val result = mapper.userToDTO(user)

            // Assert
            assertThat(result).isNotNull
            assertThat(result.id).isEqualTo(userId.toString())
            assertThat(result.email).isEqualTo("test@example.com")
            assertThat(result.displayName).isEqualTo("Test User")
        }

        @Test
        fun `should map UserEntity to UserDTO with null displayName`() {
            // Arrange
            val userId = UUID.randomUUID()
            val user =
                UserEntity(
                    id = userId,
                    email = "test@example.com",
                    displayName = null,
                    passwordHash = "hashed",
                    createdAt = Instant.now(),
                )

            // Act
            val result = mapper.userToDTO(user)

            // Assert
            assertThat(result).isNotNull
            assertThat(result.id).isEqualTo(userId.toString())
            assertThat(result.email).isEqualTo("test@example.com")
            assertThat(result.displayName).isNull()
        }

        @Test
        fun `should map UserEntity to UserDTO with empty displayName`() {
            // Arrange
            val userId = UUID.randomUUID()
            val user =
                UserEntity(
                    id = userId,
                    email = "test@example.com",
                    displayName = "",
                    passwordHash = "hashed",
                    createdAt = Instant.now(),
                )

            // Act
            val result = mapper.userToDTO(user)

            // Assert
            assertThat(result).isNotNull
            assertThat(result.id).isEqualTo(userId.toString())
            assertThat(result.email).isEqualTo("test@example.com")
            assertThat(result.displayName).isEqualTo("")
        }

        @Test
        fun `should correctly convert UUID id to String`() {
            // Arrange
            val userId = UUID.fromString("550e8400-e29b-41d4-a716-446655440000")
            val user =
                UserEntity(
                    id = userId,
                    email = "test@example.com",
                    displayName = "Test User",
                    passwordHash = "hashed",
                    createdAt = Instant.now(),
                )

            // Act
            val result = mapper.userToDTO(user)

            // Assert
            assertThat(result.id).isEqualTo("550e8400-e29b-41d4-a716-446655440000")
            assertThat(result.id).isNotEqualTo(userId) // Different types
        }

        @Test
        fun `should map UserEntity with all optional fields set`() {
            // Arrange
            val userId = UUID.randomUUID()
            val createdAt = Instant.parse("2024-01-01T00:00:00Z")
            val user =
                UserEntity(
                    id = userId,
                    email = "user@example.com",
                    displayName = "John Doe",
                    passwordHash = "hashed_password",
                    rememberMeToken = "remember_token",
                    passwordResetToken = "reset_token",
                    passwordResetExpiresAt = Instant.now().plusSeconds(3600),
                    passwordResetUsedAt = null,
                    createdAt = createdAt,
                )

            // Act
            val result = mapper.userToDTO(user)

            // Assert
            assertThat(result).isNotNull
            assertThat(result.id).isEqualTo(userId.toString())
            assertThat(result.email).isEqualTo("user@example.com")
            assertThat(result.displayName).isEqualTo("John Doe")
            // Note: Internal fields like passwordHash, tokens are not exposed in DTO
        }
    }
}


