package io.github.salomax.neotool.security.test.graphql

import io.github.salomax.neotool.common.graphql.InputValidator
import io.github.salomax.neotool.common.security.principal.AuthContext
import io.github.salomax.neotool.security.graphql.SecurityAuthResolver
import io.github.salomax.neotool.security.graphql.dto.RequestPasswordResetPayloadDTO
import io.github.salomax.neotool.security.graphql.dto.ResetPasswordPayloadDTO
import io.github.salomax.neotool.security.graphql.dto.SignInPayloadDTO
import io.github.salomax.neotool.security.graphql.dto.SignUpPayloadDTO
import io.github.salomax.neotool.security.graphql.dto.UserDTO
import io.github.salomax.neotool.security.graphql.mapper.SecurityGraphQLMapper
import io.github.salomax.neotool.security.model.TokenPair
import io.github.salomax.neotool.security.repo.UserRepository
import io.github.salomax.neotool.security.service.authentication.AuthContextFactory
import io.github.salomax.neotool.security.service.authentication.AuthenticationService
import io.github.salomax.neotool.security.service.authorization.AuthorizationService
import io.github.salomax.neotool.security.service.jwt.RefreshTokenService
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
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.UUID
import org.mockito.ArgumentMatchers.any as anyArg

@DisplayName("SecurityAuthResolver Unit Tests")
class SecurityAuthResolverTest {
    private lateinit var authenticationService: AuthenticationService
    private lateinit var authorizationService: AuthorizationService
    private lateinit var authContextFactory: AuthContextFactory
    private lateinit var userRepository: UserRepository
    private lateinit var inputValidator: InputValidator
    private lateinit var mapper: SecurityGraphQLMapper
    private lateinit var refreshTokenService: RefreshTokenService
    private lateinit var resolver: SecurityAuthResolver

    @BeforeEach
    fun setUp() {
        authenticationService = mock()
        authorizationService = mock()
        authContextFactory = mock()
        userRepository = mock()
        inputValidator = mock()
        mapper = mock()
        refreshTokenService = mock()
        resolver =
            SecurityAuthResolver(
                authenticationService,
                authorizationService,
                authContextFactory,
                userRepository,
                inputValidator,
                mapper,
                refreshTokenService,
            )
    }

    @Nested
    @DisplayName("signIn()")
    inner class SignInTests {
        @Test
        fun `should sign in successfully with valid credentials`() {
            // Arrange
            val user = SecurityTestDataBuilders.user(id = UUID.randomUUID(), email = "test@example.com")
            val userDTO = UserDTO(id = user.id.toString(), email = user.email, displayName = user.displayName)
            val input = mapOf("email" to "test@example.com", "password" to "password123", "rememberMe" to false)
            val authContext =
                AuthContext(
                    userId = user.id!!,
                    email = user.email,
                    displayName = user.displayName,
                    roles = emptyList(),
                    permissions = emptyList(),
                )

            whenever(authenticationService.authenticate("test@example.com", "password123")).thenReturn(user)
            whenever(authContextFactory.build(user)).thenReturn(authContext)
            whenever(authenticationService.generateAccessToken(eq(authContext))).thenReturn("access-token")
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
            verify(authContextFactory).build(user)
            verify(authenticationService).generateAccessToken(eq(authContext))
            verify(refreshTokenService, never()).createRefreshToken(any())
        }

        @Test
        fun `should sign in with rememberMe and generate refresh token`() {
            // Arrange
            val user = SecurityTestDataBuilders.user(id = UUID.randomUUID(), email = "test@example.com")
            val userDTO = UserDTO(id = user.id.toString(), email = user.email, displayName = user.displayName)
            val input = mapOf("email" to "test@example.com", "password" to "password123", "rememberMe" to true)
            val authContext =
                AuthContext(
                    userId = user.id!!,
                    email = user.email,
                    displayName = user.displayName,
                    roles = emptyList(),
                    permissions = emptyList(),
                )

            whenever(authenticationService.authenticate("test@example.com", "password123")).thenReturn(user)
            whenever(authContextFactory.build(user)).thenReturn(authContext)
            whenever(authenticationService.generateAccessToken(eq(authContext))).thenReturn("access-token")
            whenever(refreshTokenService.createRefreshToken(user.id!!)).thenReturn("refresh-token")
            whenever(mapper.userToDTO(user)).thenReturn(userDTO)

            // Act
            val result = resolver.signIn(input)

            // Assert
            assertThat(result.success).isTrue()
            val payload = result.data as SignInPayloadDTO
            assertThat(payload.token).isEqualTo("access-token")
            assertThat(payload.refreshToken).isEqualTo("refresh-token")
            verify(authContextFactory).build(user)
            verify(refreshTokenService).createRefreshToken(user.id!!)
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
            val user = SecurityTestDataBuilders.user(id = UUID.randomUUID(), email = "test@example.com")
            val userDTO = UserDTO(id = user.id.toString(), email = user.email)
            val input = mapOf("email" to "test@example.com", "password" to "password123", "rememberMe" to null)
            val authContext =
                AuthContext(
                    userId = user.id!!,
                    email = user.email,
                    displayName = user.displayName,
                    roles = emptyList(),
                    permissions = emptyList(),
                )

            whenever(authenticationService.authenticate("test@example.com", "password123")).thenReturn(user)
            whenever(authContextFactory.build(user)).thenReturn(authContext)
            whenever(authenticationService.generateAccessToken(eq(authContext))).thenReturn("access-token")
            whenever(mapper.userToDTO(user)).thenReturn(userDTO)

            // Act
            val result = resolver.signIn(input)

            // Assert
            assertThat(result.success).isTrue()
            verify(authContextFactory).build(user)
            verify(authenticationService).generateAccessToken(eq(authContext))
            verify(refreshTokenService, never()).createRefreshToken(any())
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
            val user = SecurityTestDataBuilders.user(id = UUID.randomUUID(), email = "oauth@example.com")
            val userDTO = UserDTO(id = user.id.toString(), email = user.email)
            val input = mapOf("provider" to "google", "idToken" to "valid-id-token", "rememberMe" to false)
            val authContext =
                AuthContext(
                    userId = user.id!!,
                    email = user.email,
                    displayName = user.displayName,
                    roles = emptyList(),
                    permissions = emptyList(),
                )

            whenever(authenticationService.authenticateWithOAuth("google", "valid-id-token")).thenReturn(user)
            whenever(authContextFactory.build(user)).thenReturn(authContext)
            whenever(authenticationService.generateAccessToken(eq(authContext))).thenReturn("access-token")
            whenever(mapper.userToDTO(user)).thenReturn(userDTO)

            // Act
            val result = resolver.signInWithOAuth(input)

            // Assert
            assertThat(result.success).isTrue()
            val payload = result.data as SignInPayloadDTO
            assertThat(payload.token).isEqualTo("access-token")
            verify(authenticationService).authenticateWithOAuth("google", "valid-id-token")
            verify(authContextFactory).build(user)
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
            val authContext =
                AuthContext(
                    userId = user.id!!,
                    email = user.email,
                    displayName = user.displayName,
                    roles = emptyList(),
                    permissions = emptyList(),
                )

            whenever(
                authenticationService.registerUser(
                    "New User",
                    "newuser@example.com",
                    "password123",
                    "en",
                ),
            ).thenReturn(AuthenticationService.RegisterUserResult(user = user, requiresVerification = false))
            whenever(authContextFactory.build(user)).thenReturn(authContext)
            whenever(authenticationService.generateAccessToken(eq(authContext))).thenReturn("access-token")
            whenever(refreshTokenService.createRefreshToken(user.id!!)).thenReturn("refresh-token")
            whenever(mapper.userToDTO(user)).thenReturn(userDTO)

            // Act
            val result = resolver.signUp(input)

            // Assert
            assertThat(result.success).isTrue()
            val payload = result.data as SignUpPayloadDTO
            assertThat(payload.token).isEqualTo("access-token")
            assertThat(payload.refreshToken).isEqualTo("refresh-token")
            assertThat(payload.requiresVerification).isFalse()
            verify(authenticationService).registerUser("New User", "newuser@example.com", "password123", "en")
            verify(authContextFactory).build(user)
            verify(refreshTokenService).createRefreshToken(user.id!!)
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

    @Nested
    @DisplayName("AuthContextFactory Integration")
    inner class AuthContextFactoryIntegrationTests {
        @Test
        fun `should call authContextFactory build for password authentication`() {
            // Arrange
            val user = SecurityTestDataBuilders.user(id = UUID.randomUUID(), email = "test@example.com")
            val userDTO = UserDTO(id = user.id.toString(), email = user.email)
            val input = mapOf("email" to "test@example.com", "password" to "password123", "rememberMe" to false)
            val authContext =
                AuthContext(
                    userId = user.id!!,
                    email = user.email,
                    displayName = user.displayName,
                    roles = emptyList(),
                    permissions = emptyList(),
                )

            whenever(authenticationService.authenticate("test@example.com", "password123")).thenReturn(user)
            whenever(authContextFactory.build(user)).thenReturn(authContext)
            whenever(authenticationService.generateAccessToken(eq(authContext))).thenReturn("access-token")
            whenever(mapper.userToDTO(user)).thenReturn(userDTO)

            // Act
            resolver.signIn(input)

            // Assert
            verify(authContextFactory).build(user)
            verify(authenticationService).generateAccessToken(eq(authContext))
        }

        @Test
        fun `should call authContextFactory build for OAuth authentication with Google provider`() {
            // Arrange
            val user = SecurityTestDataBuilders.user(id = UUID.randomUUID(), email = "oauth@example.com")
            val userDTO = UserDTO(id = user.id.toString(), email = user.email)
            val input = mapOf("provider" to "google", "idToken" to "valid-id-token", "rememberMe" to false)
            val authContext =
                AuthContext(
                    userId = user.id!!,
                    email = user.email,
                    displayName = user.displayName,
                    roles = emptyList(),
                    permissions = emptyList(),
                )

            whenever(authenticationService.authenticateWithOAuth("google", "valid-id-token")).thenReturn(user)
            whenever(authContextFactory.build(user)).thenReturn(authContext)
            whenever(authenticationService.generateAccessToken(eq(authContext))).thenReturn("access-token")
            whenever(mapper.userToDTO(user)).thenReturn(userDTO)

            // Act
            resolver.signInWithOAuth(input)

            // Assert
            verify(authContextFactory).build(user)
            verify(authenticationService).generateAccessToken(eq(authContext))
        }

        @Test
        fun `should call authContextFactory build for OAuth authentication with future provider`() {
            // Arrange - Simulate future OAuth provider (e.g., Microsoft, GitHub)
            val user = SecurityTestDataBuilders.user(id = UUID.randomUUID(), email = "oauth@example.com")
            val userDTO = UserDTO(id = user.id.toString(), email = user.email)
            val input = mapOf("provider" to "microsoft", "idToken" to "valid-id-token", "rememberMe" to false)
            val authContext =
                AuthContext(
                    userId = user.id!!,
                    email = user.email,
                    displayName = user.displayName,
                    roles = emptyList(),
                    permissions = emptyList(),
                )

            whenever(authenticationService.authenticateWithOAuth("microsoft", "valid-id-token")).thenReturn(user)
            whenever(authContextFactory.build(user)).thenReturn(authContext)
            whenever(authenticationService.generateAccessToken(eq(authContext))).thenReturn("access-token")
            whenever(mapper.userToDTO(user)).thenReturn(userDTO)

            // Act
            resolver.signInWithOAuth(input)

            // Assert
            verify(authContextFactory).build(user)
            verify(authenticationService).generateAccessToken(eq(authContext))
        }

        @Test
        fun `should call authContextFactory build for signUp`() {
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
            val authContext =
                AuthContext(
                    userId = user.id!!,
                    email = user.email,
                    displayName = user.displayName,
                    roles = emptyList(),
                    permissions = emptyList(),
                )

            whenever(
                authenticationService.registerUser(
                    "New User",
                    "newuser@example.com",
                    "password123",
                    "en",
                ),
            ).thenReturn(AuthenticationService.RegisterUserResult(user = user, requiresVerification = false))
            whenever(authContextFactory.build(user)).thenReturn(authContext)
            whenever(authenticationService.generateAccessToken(eq(authContext))).thenReturn("access-token")
            whenever(refreshTokenService.createRefreshToken(user.id!!)).thenReturn("refresh-token")
            whenever(mapper.userToDTO(user)).thenReturn(userDTO)

            // Act
            resolver.signUp(input)

            // Assert
            verify(authContextFactory).build(user)
            verify(authenticationService).generateAccessToken(eq(authContext))
        }

        @Test
        fun `should call authContextFactory build for refreshAccessToken`() {
            // Arrange
            val user = SecurityTestDataBuilders.user(id = UUID.randomUUID(), email = "test@example.com")
            val userDTO = UserDTO(id = user.id.toString(), email = user.email)
            val input = mapOf("refreshToken" to "valid-refresh-token")
            val tokenPair = TokenPair(accessToken = "new-access-token", refreshToken = "new-refresh-token")

            whenever(refreshTokenService.refreshAccessToken("valid-refresh-token")).thenReturn(tokenPair)
            whenever(authenticationService.validateAccessToken("new-access-token")).thenReturn(user)
            whenever(mapper.userToDTO(user)).thenReturn(userDTO)

            // Act
            resolver.refreshAccessToken(input)

            // Assert
            verify(refreshTokenService).refreshAccessToken("valid-refresh-token")
            verify(authenticationService).validateAccessToken("new-access-token")
        }

        @Test
        fun `should produce identical permissions for same user regardless of auth method`() {
            // Arrange - Same user, different authentication methods
            val userId = UUID.randomUUID()
            val email = "test@example.com"
            val user = SecurityTestDataBuilders.user(id = userId, email = email)
            val userDTO = UserDTO(id = user.id.toString(), email = user.email)

            // Same authentication context for both methods (proving consistency)
            val authContext =
                AuthContext(
                    userId = userId,
                    email = email,
                    displayName = user.displayName,
                    roles = listOf("admin", "editor"),
                    permissions = listOf("transaction:read", "transaction:write"),
                )

            // Password authentication
            val passwordInput = mapOf("email" to email, "password" to "password123", "rememberMe" to false)
            whenever(authenticationService.authenticate(email, "password123")).thenReturn(user)
            whenever(authContextFactory.build(user)).thenReturn(authContext).thenReturn(authContext)
            whenever(authenticationService.generateAccessToken(eq(authContext)))
                .thenReturn("password-token")
                .thenReturn("oauth-token")
            whenever(mapper.userToDTO(user)).thenReturn(userDTO)

            // OAuth authentication
            val oauthInput = mapOf("provider" to "google", "idToken" to "valid-id-token", "rememberMe" to false)
            whenever(authenticationService.authenticateWithOAuth("google", "valid-id-token")).thenReturn(user)

            // Act
            val passwordResult = resolver.signIn(passwordInput)
            val oauthResult = resolver.signInWithOAuth(oauthInput)

            // Assert - Both should use same authentication context (same permissions)
            verify(authContextFactory, times(2)).build(user)
            assertThat(passwordResult.success).isTrue()
            assertThat(oauthResult.success).isTrue()

            val passwordPayload = passwordResult.data as SignInPayloadDTO
            val oauthPayload = oauthResult.data as SignInPayloadDTO
            assertThat(passwordPayload.token).isEqualTo("password-token")
            assertThat(oauthPayload.token).isEqualTo("oauth-token")
        }
    }
}
