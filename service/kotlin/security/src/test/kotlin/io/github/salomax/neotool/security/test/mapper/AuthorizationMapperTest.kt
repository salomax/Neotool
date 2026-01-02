package io.github.salomax.neotool.security.test.mapper

import io.github.salomax.neotool.security.domain.rbac.Permission
import io.github.salomax.neotool.security.domain.rbac.Role
import io.github.salomax.neotool.security.graphql.mapper.AuthorizationMapper
import io.github.salomax.neotool.security.service.authorization.AuthorizationResult
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

@DisplayName("AuthorizationMapper Unit Tests")
class AuthorizationMapperTest {
    private lateinit var mapper: AuthorizationMapper

    @BeforeEach
    fun setUp() {
        mapper = AuthorizationMapper()
    }

    @Nested
    @DisplayName("toAuthorizationResultDTO()")
    inner class ToAuthorizationResultDTOTests {
        @Test
        fun `should convert allowed AuthorizationResult to DTO`() {
            // Arrange
            val result = AuthorizationResult(allowed = true, reason = "User has required permission")

            // Act
            val dto = mapper.toAuthorizationResultDTO(result)

            // Assert
            assertThat(dto).isNotNull()
            assertThat(dto.allowed).isTrue()
            assertThat(dto.reason).isEqualTo("User has required permission")
        }

        @Test
        fun `should convert denied AuthorizationResult to DTO`() {
            // Arrange
            val result = AuthorizationResult(allowed = false, reason = "User lacks required permission")

            // Act
            val dto = mapper.toAuthorizationResultDTO(result)

            // Assert
            assertThat(dto).isNotNull()
            assertThat(dto.allowed).isFalse()
            assertThat(dto.reason).isEqualTo("User lacks required permission")
        }

        @Test
        fun `should handle empty reason string`() {
            // Arrange
            val result = AuthorizationResult(allowed = true, reason = "")

            // Act
            val dto = mapper.toAuthorizationResultDTO(result)

            // Assert
            assertThat(dto).isNotNull()
            assertThat(dto.allowed).isTrue()
            assertThat(dto.reason).isEmpty()
        }
    }

    @Nested
    @DisplayName("toPermissionDTO()")
    inner class ToPermissionDTOTests {
        @Test
        fun `should convert Permission with id to DTO`() {
            // Arrange
            val permission =
                Permission(
                    id = UUID.randomUUID(),
                    name = "transaction:read",
                    createdAt = Instant.now(),
                    updatedAt = Instant.now(),
                )

            // Act
            val dto = mapper.toPermissionDTO(permission)

            // Assert
            assertThat(dto).isNotNull()
            assertThat(dto.id).isNotNull()
            assertThat(dto.name).isEqualTo("transaction:read")
        }

        @Test
        fun `should convert Permission without id to DTO`() {
            // Arrange
            val permission =
                Permission(
                    id = null,
                    name = "transaction:write",
                    createdAt = Instant.now(),
                    updatedAt = Instant.now(),
                )

            // Act
            val dto = mapper.toPermissionDTO(permission)

            // Assert
            assertThat(dto).isNotNull()
            assertThat(dto.id).isNull()
            assertThat(dto.name).isEqualTo("transaction:write")
        }

        @Test
        fun `should preserve all permission fields in DTO`() {
            // Arrange
            val permission =
                Permission(
                    id = UUID.randomUUID(),
                    name = "user:delete",
                    createdAt = Instant.now(),
                    updatedAt = Instant.now(),
                )

            // Act
            val dto = mapper.toPermissionDTO(permission)

            // Assert
            assertThat(dto.id).isNotNull()
            assertThat(dto.name).isEqualTo("user:delete")
        }
    }

    @Nested
    @DisplayName("toRoleDTO()")
    inner class ToRoleDTOTests {
        @Test
        fun `should convert Role with id to DTO`() {
            // Arrange
            val role =
                Role(
                    id = UUID.randomUUID(),
                    name = "admin",
                    createdAt = Instant.now(),
                    updatedAt = Instant.now(),
                )

            // Act
            val dto = mapper.toRoleDTO(role)

            // Assert
            assertThat(dto).isNotNull()
            assertThat(dto.id).isNotNull()
            assertThat(dto.name).isEqualTo("admin")
        }

        @Test
        fun `should convert Role without id to DTO`() {
            // Arrange
            val role =
                Role(
                    id = null,
                    name = "user",
                    createdAt = Instant.now(),
                    updatedAt = Instant.now(),
                )

            // Act
            val dto = mapper.toRoleDTO(role)

            // Assert
            assertThat(dto).isNotNull()
            assertThat(dto.id).isNull()
            assertThat(dto.name).isEqualTo("user")
        }

        @Test
        fun `should preserve all role fields in DTO`() {
            // Arrange
            val roleId = UUID.randomUUID()
            val role =
                Role(
                    id = roleId,
                    name = "moderator",
                    createdAt = Instant.now(),
                    updatedAt = Instant.now(),
                )

            // Act
            val dto = mapper.toRoleDTO(role)

            // Assert
            assertThat(dto.id).isEqualTo(roleId.toString())
            assertThat(dto.name).isEqualTo("moderator")
        }
    }
}
