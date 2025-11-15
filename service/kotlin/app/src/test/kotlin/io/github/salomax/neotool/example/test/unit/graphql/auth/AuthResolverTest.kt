package io.github.salomax.neotool.example.test.unit.graphql.auth

import io.github.salomax.neotool.example.graphql.auth.AuthResolver
import io.github.salomax.neotool.example.graphql.dto.SignInPayloadDTO
import io.github.salomax.neotool.example.graphql.dto.UserDTO
import io.github.salomax.neotool.example.graphql.payload.GraphQLPayload
import io.github.salomax.neotool.security.model.UserEntity
import io.github.salomax.neotool.security.repo.UserRepository
import io.github.salomax.neotool.security.service.AuthenticationService
import io.github.salomax.neotool.security.test.SecurityTestDataBuilders
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import java.util.*

@DisplayName("AuthResolver Unit Tests")
class AuthResolverTest {

    private lateinit var authenticationService: AuthenticationService
    private lateinit var userRepository: UserRepository
    private lateinit var authResolver: AuthResolver

    @BeforeEach
    fun setUp() {
        authenticationService = mock()
        userRepository = mock()
        authResolver = AuthResolver(authenticationService, userRepository)
    }

    @Nested
    @DisplayName("Sign In Mutation")
    inner class SignInMutationTests {

        @Test
        fun `should sign in user successfully with JWT token`() {
            val email = "test@example.com"
            val password = "TestPassword123!"
            val userId = UUID.randomUUID()
            val user = SecurityTestDataBuilders.user(
                id = userId,
                email = email,
                displayName = "Test User"
            )
            val accessToken = "jwt-access-token"
            val refreshToken = "jwt-refresh-token"

            whenever(authenticationService.authenticate(email, password)).thenReturn(user)
            whenever(authenticationService.generateAccessToken(user)).thenReturn(accessToken)
            whenever(authenticationService.generateRefreshToken(user)).thenReturn(refreshToken)
            whenever(authenticationService.saveRememberMeToken(userId, refreshToken)).thenReturn(user)

            val input = mapOf(
                "email" to email,
                "password" to password,
                "rememberMe" to true
            )

            val result = authResolver.signIn(input)

            assertThat(result).isNotNull()
            assertThat(result.isSuccess).isTrue()
            val payload = result.data as? SignInPayloadDTO
            assertThat(payload).isNotNull()
            assertThat(payload?.token).isEqualTo(accessToken)
            assertThat(payload?.refreshToken).isEqualTo(refreshToken)
            assertThat(payload?.user?.email).isEqualTo(email)
            assertThat(payload?.user?.id).isEqualTo(userId.toString())

            verify(authenticationService).authenticate(email, password)
            verify(authenticationService).generateAccessToken(user)
            verify(authenticationService).generateRefreshToken(user)
            verify(authenticationService).saveRememberMeToken(userId, refreshToken)
        }

        @Test
        fun `should sign in user without remember me`() {
            val email = "test@example.com"
            val password = "TestPassword123!"
            val userId = UUID.randomUUID()
            val user = SecurityTestDataBuilders.user(
                id = userId,
                email = email,
                displayName = "Test User"
            )
            val accessToken = "jwt-access-token"

            whenever(authenticationService.authenticate(email, password)).thenReturn(user)
            whenever(authenticationService.generateAccessToken(user)).thenReturn(accessToken)

            val input = mapOf(
                "email" to email,
                "password" to password,
                "rememberMe" to false
            )

            val result = authResolver.signIn(input)

            assertThat(result).isNotNull()
            assertThat(result.isSuccess).isTrue()
            val payload = result.data as? SignInPayloadDTO
            assertThat(payload).isNotNull()
            assertThat(payload?.token).isEqualTo(accessToken)
            assertThat(payload?.refreshToken).isNull()

            verify(authenticationService).authenticate(email, password)
            verify(authenticationService).generateAccessToken(user)
            verify(authenticationService, never()).generateRefreshToken(any())
            verify(authenticationService, never()).saveRememberMeToken(any(), any())
        }

        @Test
        fun `should return error for invalid credentials`() {
            val email = "test@example.com"
            val password = "WrongPassword123!"

            whenever(authenticationService.authenticate(email, password)).thenReturn(null)

            val input = mapOf(
                "email" to email,
                "password" to password
            )

            val result = authResolver.signIn(input)

            assertThat(result).isNotNull()
            assertThat(result.isSuccess).isFalse()
            assertThat(result.errors).isNotEmpty()

            verify(authenticationService).authenticate(email, password)
            verify(authenticationService, never()).generateAccessToken(any())
        }

        @Test
        fun `should return error for missing email`() {
            val input = mapOf(
                "password" to "TestPassword123!"
            )

            val result = authResolver.signIn(input)

            assertThat(result).isNotNull()
            assertThat(result.isSuccess).isFalse()
            assertThat(result.errors).isNotEmpty()

            verify(authenticationService, never()).authenticate(any(), any())
        }

        @Test
        fun `should return error for missing password`() {
            val input = mapOf(
                "email" to "test@example.com"
            )

            val result = authResolver.signIn(input)

            assertThat(result).isNotNull()
            assertThat(result.isSuccess).isFalse()
            assertThat(result.errors).isNotEmpty()

            verify(authenticationService, never()).authenticate(any(), any())
        }
    }

    @Nested
    @DisplayName("Get Current User")
    inner class GetCurrentUserTests {

        @Test
        fun `should return user for valid JWT access token`() {
            val userId = UUID.randomUUID()
            val email = "test@example.com"
            val user = SecurityTestDataBuilders.user(
                id = userId,
                email = email,
                displayName = "Test User"
            )
            val token = "valid-jwt-access-token"

            whenever(authenticationService.validateAccessToken(token)).thenReturn(user)

            val result = authResolver.getCurrentUser(token)

            assertThat(result).isNotNull()
            assertThat(result?.id).isEqualTo(userId.toString())
            assertThat(result?.email).isEqualTo(email)
            assertThat(result?.displayName).isEqualTo("Test User")

            verify(authenticationService).validateAccessToken(token)
        }

        @Test
        fun `should return null for null token`() {
            val result = authResolver.getCurrentUser(null)

            assertThat(result).isNull()
            verify(authenticationService, never()).validateAccessToken(any())
        }

        @Test
        fun `should return null for invalid token`() {
            val invalidToken = "invalid-token"

            whenever(authenticationService.validateAccessToken(invalidToken)).thenReturn(null)

            val result = authResolver.getCurrentUser(invalidToken)

            assertThat(result).isNull()
            verify(authenticationService).validateAccessToken(invalidToken)
        }

        @Test
        fun `should return null for expired token`() {
            val expiredToken = "expired-jwt-token"

            whenever(authenticationService.validateAccessToken(expiredToken)).thenReturn(null)

            val result = authResolver.getCurrentUser(expiredToken)

            assertThat(result).isNull()
            verify(authenticationService).validateAccessToken(expiredToken)
        }

        @Test
        fun `should return null for refresh token used as access token`() {
            val refreshToken = "jwt-refresh-token"

            whenever(authenticationService.validateAccessToken(refreshToken)).thenReturn(null)

            val result = authResolver.getCurrentUser(refreshToken)

            assertThat(result).isNull()
            verify(authenticationService).validateAccessToken(refreshToken)
        }
    }
}

