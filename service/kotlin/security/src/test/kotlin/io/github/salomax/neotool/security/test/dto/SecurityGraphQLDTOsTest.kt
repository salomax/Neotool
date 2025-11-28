package io.github.salomax.neotool.security.test.dto

import io.github.salomax.neotool.security.graphql.dto.AuthorizationResultDTO
import io.github.salomax.neotool.security.graphql.dto.PermissionDTO
import io.github.salomax.neotool.security.graphql.dto.RequestPasswordResetInputDTO
import io.github.salomax.neotool.security.graphql.dto.RequestPasswordResetPayloadDTO
import io.github.salomax.neotool.security.graphql.dto.ResetPasswordInputDTO
import io.github.salomax.neotool.security.graphql.dto.ResetPasswordPayloadDTO
import io.github.salomax.neotool.security.graphql.dto.RoleDTO
import io.github.salomax.neotool.security.graphql.dto.SignInInputDTO
import io.github.salomax.neotool.security.graphql.dto.SignInPayloadDTO
import io.github.salomax.neotool.security.graphql.dto.SignUpInputDTO
import io.github.salomax.neotool.security.graphql.dto.SignUpPayloadDTO
import io.github.salomax.neotool.security.graphql.dto.UserDTO
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("SecurityGraphQLDTOs Tests")
class SecurityGraphQLDTOsTest {
    @Nested
    @DisplayName("SignInInputDTO")
    inner class SignInInputDTOTests {
        @Test
        fun `should create with all fields`() {
            // Act
            val dto =
                SignInInputDTO(
                    email = "test@example.com",
                    password = "password123",
                    rememberMe = true,
                )

            // Assert
            assertThat(dto.email).isEqualTo("test@example.com")
            assertThat(dto.password).isEqualTo("password123")
            assertThat(dto.rememberMe).isTrue()
        }

        @Test
        fun `should have default values`() {
            // Act
            val dto = SignInInputDTO()

            // Assert
            assertThat(dto.email).isEmpty()
            assertThat(dto.password).isEmpty()
            assertThat(dto.rememberMe).isFalse()
        }
    }

    @Nested
    @DisplayName("SignInPayloadDTO")
    inner class SignInPayloadDTOTests {
        @Test
        fun `should create with all fields`() {
            // Arrange
            val user = UserDTO(id = "123", email = "test@example.com", displayName = "Test User")

            // Act
            val dto =
                SignInPayloadDTO(
                    token = "access-token",
                    refreshToken = "refresh-token",
                    user = user,
                )

            // Assert
            assertThat(dto.token).isEqualTo("access-token")
            assertThat(dto.refreshToken).isEqualTo("refresh-token")
            assertThat(dto.user).isEqualTo(user)
        }

        @Test
        fun `should allow null refreshToken`() {
            // Arrange
            val user = UserDTO(id = "123", email = "test@example.com")

            // Act
            val dto = SignInPayloadDTO(token = "access-token", user = user)

            // Assert
            assertThat(dto.token).isEqualTo("access-token")
            assertThat(dto.refreshToken).isNull()
            assertThat(dto.user).isEqualTo(user)
        }
    }

    @Nested
    @DisplayName("SignUpInputDTO")
    inner class SignUpInputDTOTests {
        @Test
        fun `should create with all fields`() {
            // Act
            val dto =
                SignUpInputDTO(
                    name = "Test User",
                    email = "test@example.com",
                    password = "password123",
                )

            // Assert
            assertThat(dto.name).isEqualTo("Test User")
            assertThat(dto.email).isEqualTo("test@example.com")
            assertThat(dto.password).isEqualTo("password123")
        }

        @Test
        fun `should have default values`() {
            // Act
            val dto = SignUpInputDTO()

            // Assert
            assertThat(dto.name).isEmpty()
            assertThat(dto.email).isEmpty()
            assertThat(dto.password).isEmpty()
        }
    }

    @Nested
    @DisplayName("SignUpPayloadDTO")
    inner class SignUpPayloadDTOTests {
        @Test
        fun `should create with all fields`() {
            // Arrange
            val user = UserDTO(id = "123", email = "test@example.com", displayName = "Test User")

            // Act
            val dto =
                SignUpPayloadDTO(
                    token = "access-token",
                    refreshToken = "refresh-token",
                    user = user,
                )

            // Assert
            assertThat(dto.token).isEqualTo("access-token")
            assertThat(dto.refreshToken).isEqualTo("refresh-token")
            assertThat(dto.user).isEqualTo(user)
        }
    }

    @Nested
    @DisplayName("UserDTO")
    inner class UserDTOTests {
        @Test
        fun `should create with all fields`() {
            // Act
            val dto =
                UserDTO(
                    id = "123",
                    email = "test@example.com",
                    displayName = "Test User",
                )

            // Assert
            assertThat(dto.id).isEqualTo("123")
            assertThat(dto.email).isEqualTo("test@example.com")
            assertThat(dto.displayName).isEqualTo("Test User")
        }

        @Test
        fun `should allow null displayName`() {
            // Act
            val dto = UserDTO(id = "123", email = "test@example.com")

            // Assert
            assertThat(dto.id).isEqualTo("123")
            assertThat(dto.email).isEqualTo("test@example.com")
            assertThat(dto.displayName).isNull()
        }
    }

    @Nested
    @DisplayName("RequestPasswordResetInputDTO")
    inner class RequestPasswordResetInputDTOTests {
        @Test
        fun `should create with all fields`() {
            // Act
            val dto =
                RequestPasswordResetInputDTO(
                    email = "test@example.com",
                    locale = "pt",
                )

            // Assert
            assertThat(dto.email).isEqualTo("test@example.com")
            assertThat(dto.locale).isEqualTo("pt")
        }

        @Test
        fun `should have default locale`() {
            // Act
            val dto = RequestPasswordResetInputDTO(email = "test@example.com")

            // Assert
            assertThat(dto.email).isEqualTo("test@example.com")
            assertThat(dto.locale).isEqualTo("en")
        }

        @Test
        fun `should allow null locale`() {
            // Act
            val dto = RequestPasswordResetInputDTO(email = "test@example.com", locale = null)

            // Assert
            assertThat(dto.email).isEqualTo("test@example.com")
            assertThat(dto.locale).isNull()
        }
    }

    @Nested
    @DisplayName("RequestPasswordResetPayloadDTO")
    inner class RequestPasswordResetPayloadDTOTests {
        @Test
        fun `should create with all fields`() {
            // Act
            val dto =
                RequestPasswordResetPayloadDTO(
                    success = true,
                    message = "Password reset email sent",
                )

            // Assert
            assertThat(dto.success).isTrue()
            assertThat(dto.message).isEqualTo("Password reset email sent")
        }
    }

    @Nested
    @DisplayName("ResetPasswordInputDTO")
    inner class ResetPasswordInputDTOTests {
        @Test
        fun `should create with all fields`() {
            // Act
            val dto =
                ResetPasswordInputDTO(
                    token = "reset-token",
                    newPassword = "newPassword123",
                )

            // Assert
            assertThat(dto.token).isEqualTo("reset-token")
            assertThat(dto.newPassword).isEqualTo("newPassword123")
        }

        @Test
        fun `should have default values`() {
            // Act
            val dto = ResetPasswordInputDTO()

            // Assert
            assertThat(dto.token).isEmpty()
            assertThat(dto.newPassword).isEmpty()
        }
    }

    @Nested
    @DisplayName("ResetPasswordPayloadDTO")
    inner class ResetPasswordPayloadDTOTests {
        @Test
        fun `should create with all fields`() {
            // Act
            val dto =
                ResetPasswordPayloadDTO(
                    success = true,
                    message = "Password reset successful",
                )

            // Assert
            assertThat(dto.success).isTrue()
            assertThat(dto.message).isEqualTo("Password reset successful")
        }
    }

    @Nested
    @DisplayName("AuthorizationResultDTO")
    inner class AuthorizationResultDTOTests {
        @Test
        fun `should create with all fields`() {
            // Act
            val dto =
                AuthorizationResultDTO(
                    allowed = true,
                    reason = "User has required permission",
                )

            // Assert
            assertThat(dto.allowed).isTrue()
            assertThat(dto.reason).isEqualTo("User has required permission")
        }

        @Test
        fun `should create with denied result`() {
            // Act
            val dto =
                AuthorizationResultDTO(
                    allowed = false,
                    reason = "User lacks required permission",
                )

            // Assert
            assertThat(dto.allowed).isFalse()
            assertThat(dto.reason).isEqualTo("User lacks required permission")
        }
    }

    @Nested
    @DisplayName("PermissionDTO")
    inner class PermissionDTOTests {
        @Test
        fun `should create with all fields`() {
            // Act
            val dto =
                PermissionDTO(
                    id = 1,
                    name = "transaction:read",
                )

            // Assert
            assertThat(dto.id).isEqualTo(1)
            assertThat(dto.name).isEqualTo("transaction:read")
        }

        @Test
        fun `should allow null id`() {
            // Act
            val dto = PermissionDTO(id = null, name = "transaction:read")

            // Assert
            assertThat(dto.id).isNull()
            assertThat(dto.name).isEqualTo("transaction:read")
        }
    }

    @Nested
    @DisplayName("RoleDTO")
    inner class RoleDTOTests {
        @Test
        fun `should create with all fields`() {
            // Act
            val dto =
                RoleDTO(
                    id = 1,
                    name = "admin",
                )

            // Assert
            assertThat(dto.id).isEqualTo(1)
            assertThat(dto.name).isEqualTo("admin")
        }

        @Test
        fun `should allow null id`() {
            // Act
            val dto = RoleDTO(id = null, name = "user")

            // Assert
            assertThat(dto.id).isNull()
            assertThat(dto.name).isEqualTo("user")
        }
    }
}
