package io.github.salomax.neotool.security.test.graphql

import io.github.salomax.neotool.common.graphql.InputValidator
import io.github.salomax.neotool.security.graphql.SecurityAuthResolver
import io.github.salomax.neotool.security.graphql.dto.RequestPasswordResetPayloadDTO
import io.github.salomax.neotool.security.graphql.dto.ResetPasswordPayloadDTO
import io.github.salomax.neotool.security.graphql.dto.SignInPayloadDTO
import io.github.salomax.neotool.security.graphql.dto.SignUpPayloadDTO
import io.github.salomax.neotool.security.graphql.dto.UserDTO
import io.github.salomax.neotool.security.graphql.mapper.SecurityGraphQLMapper
import io.github.salomax.neotool.security.repo.UserRepository
import io.github.salomax.neotool.security.service.AuthenticationService
import io.github.salomax.neotool.security.test.SecurityTestDataBuilders
import jakarta.validation.ConstraintViolationException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doNothing
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.UUID
import org.mockito.ArgumentMatchers.any as anyArg

@DisplayName("SecurityAuthResolver Unit Tests")
class SecurityAuthResolverTest {
    private lateinit var authenticationService: AuthenticationService
    private lateinit var userRepository: UserRepository
    private lateinit var inputValidator: InputValidator
    private lateinit var mapper: SecurityGraphQLMapper
    private lateinit var resolver: SecurityAuthResolver

    @BeforeEach
    fun setUp() {
        authenticationService = mock()
        userRepository = mock()
        inputValidator = mock()
        mapper = mock()
        resolver = SecurityAuthResolver(authenticationService, userRepository, inputValidator, mapper)
    }

    @Nested
    @DisplayName("signIn()")
    inner class SignInTests {
        @Test
        fun `should sign in successfully with valid credentials`() {
            // Arrange
            val user = SecurityTestDataBuilders.user(email = "test@example.com")
            val userDTO = UserDTO(id = user.id.toString(), email = user.email, displayName = user.displayName)
            val input = mapOf("email" to "test@example.com", "password" to "password123", "rememberMe" to false)

            whenever(authenticationService.authenticate("test@example.com", "password123")).thenReturn(user)
            whenever(authenticationService.generateAccessToken(user)).thenReturn("access-token")
            whenever(mapper.userToDTO(user)).thenReturn(userDTO)

            // Act
            val result = resolver.signIn(input)

            // Assert
            assertThat(result.success).isTrue()
            val payload = result.data as SignInPayloadDTO
            assertThat(payload.token).isEqualTo("access-token")
            assertThat(payload.refreshToken).isNull()
            assertThat(payload.user).isEqualTo(userDTO)
            verify(authenticationService).authenticate("test@example.com", "password123")
            verify(authenticationService).generateAccessToken(user)
            verify(authenticationService, never()).generateRefreshToken(any())
            verify(authenticationService, never()).saveRememberMeToken(any(), any())
        }

        @Test
        fun `should sign in with rememberMe and generate refresh token`() {
            // Arrange
            val user = SecurityTestDataBuilders.user(id = UUID.randomUUID(), email = "test@example.com")
            val userDTO = UserDTO(id = user.id.toString(), email = user.email, displayName = user.displayName)
            val input = mapOf("email" to "test@example.com", "password" to "password123", "rememberMe" to true)

            whenever(authenticationService.authenticate("test@example.com", "password123")).thenReturn(user)
            whenever(authenticationService.generateAccessToken(user)).thenReturn("access-token")
            whenever(authenticationService.generateRefreshToken(user)).thenReturn("refresh-token")
            whenever(mapper.userToDTO(user)).thenReturn(userDTO)

            // Act
            val result = resolver.signIn(input)

            // Assert
            assertThat(result.success).isTrue()
            val payload = result.data as SignInPayloadDTO
            assertThat(payload.token).isEqualTo("access-token")
            assertThat(payload.refreshToken).isEqualTo("refresh-token")
            verify(authenticationService).generateRefreshToken(user)
            verify(authenticationService).saveRememberMeToken(user.id!!, "refresh-token")
        }

        @Test
        fun `should return error when email is missing`() {
            // Arrange
            val input = mapOf("password" to "password123")

            // Act
            val result = resolver.signIn(input)

            // Assert
            assertThat(result.success).isFalse()
            assertThat(result.errors).isNotEmpty()
        }

        @Test
        fun `should return error when password is missing`() {
            // Arrange
            val input = mapOf("email" to "test@example.com")

            // Act
            val result = resolver.signIn(input)

            // Assert
            assertThat(result.success).isFalse()
            assertThat(result.errors).isNotEmpty()
        }

        @Test
        fun `should return error when authentication fails`() {
            // Arrange
            val input = mapOf("email" to "test@example.com", "password" to "wrongpassword")
            whenever(authenticationService.authenticate("test@example.com", "wrongpassword")).thenReturn(null)

            // Act
            val result = resolver.signIn(input)

            // Assert
            assertThat(result.success).isFalse()
            assertThat(result.errors).isNotEmpty()
        }

        @Test
        fun `should handle null rememberMe as false`() {
            // Arrange
            val user = SecurityTestDataBuilders.user(email = "test@example.com")
            val userDTO = UserDTO(id = user.id.toString(), email = user.email)
            val input = mapOf("email" to "test@example.com", "password" to "password123", "rememberMe" to null)

            whenever(authenticationService.authenticate("test@example.com", "password123")).thenReturn(user)
            whenever(authenticationService.generateAccessToken(user)).thenReturn("access-token")
            whenever(mapper.userToDTO(user)).thenReturn(userDTO)

            // Act
            val result = resolver.signIn(input)

            // Assert
            assertThat(result.success).isTrue()
            verify(authenticationService, never()).generateRefreshToken(any())
        }
    }

    @Nested
    @DisplayName("getCurrentUser()")
    inner class GetCurrentUserTests {
        @Test
        fun `should return user when token is valid`() {
            // Arrange
            val user = SecurityTestDataBuilders.user(email = "test@example.com")
            val userDTO = UserDTO(id = user.id.toString(), email = user.email, displayName = user.displayName)
            whenever(authenticationService.validateAccessToken("valid-token")).thenReturn(user)
            whenever(mapper.userToDTO(user)).thenReturn(userDTO)

            // Act
            val result = resolver.getCurrentUser("valid-token")

            // Assert
            assertThat(result).isNotNull()
            assertThat(result).isEqualTo(userDTO)
            verify(authenticationService).validateAccessToken("valid-token")
        }

        @Test
        fun `should return null when token is null`() {
            // Act
            val result = resolver.getCurrentUser(null)

            // Assert
            assertThat(result).isNull()
            verify(authenticationService, never()).validateAccessToken(any())
        }

        @Test
        fun `should return null when token is invalid`() {
            // Arrange
            whenever(authenticationService.validateAccessToken("invalid-token")).thenReturn(null)

            // Act
            val result = resolver.getCurrentUser("invalid-token")

            // Assert
            assertThat(result).isNull()
            verify(authenticationService).validateAccessToken("invalid-token")
        }

        @Test
        fun `should return null when token is expired`() {
            // Arrange
            whenever(authenticationService.validateAccessToken("expired-token")).thenReturn(null)

            // Act
            val result = resolver.getCurrentUser("expired-token")

            // Assert
            assertThat(result).isNull()
        }
    }

    @Nested
    @DisplayName("signInWithOAuth()")
    inner class SignInWithOAuthTests {
        @Test
        fun `should sign in successfully with OAuth`() {
            // Arrange
            val user = SecurityTestDataBuilders.user(email = "oauth@example.com")
            val userDTO = UserDTO(id = user.id.toString(), email = user.email)
            val input = mapOf("provider" to "google", "idToken" to "valid-id-token", "rememberMe" to false)

            whenever(authenticationService.authenticateWithOAuth("google", "valid-id-token")).thenReturn(user)
            whenever(authenticationService.generateAccessToken(user)).thenReturn("access-token")
            whenever(mapper.userToDTO(user)).thenReturn(userDTO)

            // Act
            val result = resolver.signInWithOAuth(input)

            // Assert
            assertThat(result.success).isTrue()
            val payload = result.data as SignInPayloadDTO
            assertThat(payload.token).isEqualTo("access-token")
            verify(authenticationService).authenticateWithOAuth("google", "valid-id-token")
        }

        @Test
        fun `should return error when provider is missing`() {
            // Arrange
            val input = mapOf("idToken" to "valid-id-token")

            // Act
            val result = resolver.signInWithOAuth(input)

            // Assert
            assertThat(result.success).isFalse()
        }

        @Test
        fun `should return error when idToken is missing`() {
            // Arrange
            val input = mapOf("provider" to "google")

            // Act
            val result = resolver.signInWithOAuth(input)

            // Assert
            assertThat(result.success).isFalse()
        }

        @Test
        fun `should return error when OAuth authentication fails`() {
            // Arrange
            val input = mapOf("provider" to "google", "idToken" to "invalid-token")
            whenever(authenticationService.authenticateWithOAuth("google", "invalid-token")).thenReturn(null)

            // Act
            val result = resolver.signInWithOAuth(input)

            // Assert
            assertThat(result.success).isFalse()
        }
    }

    @Nested
    @DisplayName("signUp()")
    inner class SignUpTests {
        @Test
        fun `should sign up successfully`() {
            // Arrange
            val user =
                SecurityTestDataBuilders.user(
                    id = UUID.randomUUID(),
                    email = "newuser@example.com",
                    displayName = "New User",
                )
            val userDTO = UserDTO(id = user.id.toString(), email = user.email, displayName = user.displayName)
            val input =
                mapOf(
                    "name" to "New User",
                    "email" to "newuser@example.com",
                    "password" to "password123",
                )

            whenever(
                authenticationService.registerUser(
                    "New User",
                    "newuser@example.com",
                    "password123",
                ),
            ).thenReturn(user)
            whenever(authenticationService.generateAccessToken(user)).thenReturn("access-token")
            whenever(authenticationService.generateRefreshToken(user)).thenReturn("refresh-token")
            whenever(mapper.userToDTO(user)).thenReturn(userDTO)

            // Act
            val result = resolver.signUp(input)

            // Assert
            assertThat(result.success).isTrue()
            val payload = result.data as SignUpPayloadDTO
            assertThat(payload.token).isEqualTo("access-token")
            assertThat(payload.refreshToken).isEqualTo("refresh-token")
            verify(authenticationService).registerUser("New User", "newuser@example.com", "password123")
            verify(authenticationService).saveRememberMeToken(user.id!!, "refresh-token")
        }

        @Test
        fun `should return error when name is missing`() {
            // Arrange
            val input = mapOf("email" to "test@example.com", "password" to "password123")

            // Act
            val result = resolver.signUp(input)

            // Assert
            assertThat(result.success).isFalse()
        }

        @Test
        fun `should return error when email is missing`() {
            // Arrange
            val input = mapOf("name" to "Test User", "password" to "password123")

            // Act
            val result = resolver.signUp(input)

            // Assert
            assertThat(result.success).isFalse()
        }

        @Test
        fun `should return error when password is missing`() {
            // Arrange
            val input = mapOf("name" to "Test User", "email" to "test@example.com")

            // Act
            val result = resolver.signUp(input)

            // Assert
            assertThat(result.success).isFalse()
        }
    }

    @Nested
    @DisplayName("requestPasswordReset()")
    inner class RequestPasswordResetTests {
        @Test
        fun `should return success even when email is empty`() {
            // Arrange
            val input = mapOf("email" to "")

            // Act
            val result = resolver.requestPasswordReset(input)

            // Assert
            assertThat(result.success).isTrue()
            val payload = result.data as RequestPasswordResetPayloadDTO
            assertThat(payload.success).isTrue()
            assertThat(payload.message).contains("If an account with that email exists")
        }

        @Test
        fun `should return success when validation fails`() {
            // Arrange
            val input = mapOf("email" to "invalid-email")
            doThrow(ConstraintViolationException(emptySet())).`when`(inputValidator).validate(anyArg<Any>())

            // Act
            val result = resolver.requestPasswordReset(input)

            // Assert
            assertThat(result.success).isTrue()
            val payload = result.data as RequestPasswordResetPayloadDTO
            assertThat(payload.success).isTrue()
        }

        @Test
        fun `should return success when service throws exception`() {
            // Arrange
            val input = mapOf("email" to "test@example.com")
            doNothing().`when`(inputValidator).validate(anyArg<Any>())
            whenever(
                authenticationService.requestPasswordReset(
                    any<String>(),
                    any<String>(),
                ),
            ).thenThrow(RuntimeException("Service error"))

            // Act
            val result = resolver.requestPasswordReset(input)

            // Assert
            assertThat(result.success).isTrue()
            val payload = result.data as RequestPasswordResetPayloadDTO
            assertThat(payload.success).isTrue()
        }

        @Test
        fun `should use default locale when not provided`() {
            // Arrange
            val input = mapOf("email" to "test@example.com")
            doNothing().`when`(inputValidator).validate(anyArg<Any>())
            whenever(authenticationService.requestPasswordReset(any<String>(), any<String>())).thenReturn(true)

            // Act
            resolver.requestPasswordReset(input)

            // Assert
            verify(authenticationService).requestPasswordReset("test@example.com", "en")
        }
    }

    @Nested
    @DisplayName("resetPassword()")
    inner class ResetPasswordTests {
        @Test
        fun `should reset password successfully`() {
            // Arrange
            val input = mapOf("token" to "valid-token", "newPassword" to "newPassword123")

            // Act
            val result = resolver.resetPassword(input)

            // Assert
            assertThat(result.success).isTrue()
            val payload = result.data as ResetPasswordPayloadDTO
            assertThat(payload.success).isTrue()
            verify(authenticationService).resetPassword("valid-token", "newPassword123")
        }

        @Test
        fun `should return error when token is missing`() {
            // Arrange
            val input = mapOf("newPassword" to "newPassword123")

            // Act
            val result = resolver.resetPassword(input)

            // Assert
            assertThat(result.success).isFalse()
        }

        @Test
        fun `should return error when newPassword is missing`() {
            // Arrange
            val input = mapOf("token" to "valid-token")

            // Act
            val result = resolver.resetPassword(input)

            // Assert
            assertThat(result.success).isFalse()
        }

        @Test
        fun `should return error when reset fails`() {
            // Arrange
            val input = mapOf("token" to "invalid-token", "newPassword" to "newPassword123")
            whenever(authenticationService.resetPassword("invalid-token", "newPassword123"))
                .thenThrow(IllegalArgumentException("Invalid token"))

            // Act
            val result = resolver.resetPassword(input)

            // Assert
            assertThat(result.success).isFalse()
        }
    }
}
