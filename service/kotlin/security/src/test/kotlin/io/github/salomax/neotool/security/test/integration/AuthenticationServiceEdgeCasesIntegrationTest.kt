package io.github.salomax.neotool.security.test.integration

import io.github.salomax.neotool.common.test.integration.BaseIntegrationTest
import io.github.salomax.neotool.common.test.integration.PostgresIntegrationTest
import io.github.salomax.neotool.common.test.transaction.runTransaction
import io.github.salomax.neotool.security.model.UserEntity
import io.github.salomax.neotool.security.repo.UserRepository
import io.github.salomax.neotool.security.service.AuthenticationService
import io.github.salomax.neotool.security.test.SecurityTestDataBuilders
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import jakarta.persistence.EntityManager
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestMethodOrder
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

/**
 * Integration tests for AuthenticationService edge cases and error branches.
 * Tests cover refresh tokens, OAuth edge cases, password validation, and error handling.
 */
@MicronautTest(startApplication = true)
@DisplayName("Authentication Service Edge Cases Integration Tests")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Tag("integration")
@Tag("authentication")
@Tag("security")
@TestMethodOrder(MethodOrderer.Random::class)
open class AuthenticationServiceEdgeCasesIntegrationTest : BaseIntegrationTest(), PostgresIntegrationTest {
    @Inject
    lateinit var userRepository: UserRepository

    @Inject
    lateinit var authenticationService: AuthenticationService

    @Inject
    lateinit var entityManager: EntityManager

    private fun uniqueEmail() = SecurityTestDataBuilders.uniqueEmail("auth-edge-cases")

    fun saveUser(user: UserEntity) {
        entityManager.runTransaction {
            authenticationService.saveUser(user)
            entityManager.flush()
        }
    }

    @AfterEach
    fun cleanupTestData() {
        try {
            userRepository.deleteAll()
        } catch (e: Exception) {
            // Ignore cleanup errors
        }
    }

    @Nested
    @DisplayName("Refresh Token Validation")
    inner class RefreshTokenValidationTests {
        @Test
        fun `should validate refresh token successfully`() {
            val email = uniqueEmail()
            val password = "TestPassword123!"
            val user =
                SecurityTestDataBuilders.userWithPassword(
                    authenticationService = authenticationService,
                    email = email,
                    password = password,
                )
            saveUser(user)

            // Generate refresh token
            val refreshToken = authenticationService.generateRefreshToken(user)
            authenticationService.saveRememberMeToken(user.id, refreshToken)

            // Validate refresh token
            val validatedUser = authenticationService.validateRefreshToken(refreshToken)
            Assertions.assertThat(validatedUser).isNotNull()
            Assertions.assertThat(validatedUser?.email).isEqualTo(email)
        }

        @Test
        fun `should reject refresh token when user not found`() {
            val email = uniqueEmail()
            val password = "TestPassword123!"
            val user =
                SecurityTestDataBuilders.userWithPassword(
                    authenticationService = authenticationService,
                    email = email,
                    password = password,
                )
            saveUser(user)

            // Generate refresh token for user
            val refreshToken = authenticationService.generateRefreshToken(user)

            // Delete user
            userRepository.delete(user)

            // Try to validate refresh token - should fail
            val validatedUser = authenticationService.validateRefreshToken(refreshToken)
            Assertions.assertThat(validatedUser).isNull()
        }

        @Test
        fun `should reject refresh token when token is revoked`() {
            val email = uniqueEmail()
            val password = "TestPassword123!"
            val user =
                SecurityTestDataBuilders.userWithPassword(
                    authenticationService = authenticationService,
                    email = email,
                    password = password,
                )
            saveUser(user)

            // Generate and save refresh token
            val refreshToken = authenticationService.generateRefreshToken(user)
            authenticationService.saveRememberMeToken(user.id, refreshToken)

            // Revoke token by clearing it
            authenticationService.clearRememberMeToken(user.id)

            // Try to validate refresh token - should fail
            val validatedUser = authenticationService.validateRefreshToken(refreshToken)
            Assertions.assertThat(validatedUser).isNull()
        }

        @Test
        fun `should reject refresh token when token does not match stored token`() {
            val email = uniqueEmail()
            val password = "TestPassword123!"
            val user =
                SecurityTestDataBuilders.userWithPassword(
                    authenticationService = authenticationService,
                    email = email,
                    password = password,
                )
            saveUser(user)

            // Generate and save refresh token
            val refreshToken1 = authenticationService.generateRefreshToken(user)
            entityManager.runTransaction {
                authenticationService.saveRememberMeToken(user.id, refreshToken1)
                entityManager.flush()
            }

            // Clear to ensure fresh data
            entityManager.clear()

            // Reload user to get fresh data from database
            val reloadedUser = userRepository.findById(user.id).orElseThrow()

            // Verify that stored token matches the saved token
            Assertions.assertThat(reloadedUser.rememberMeToken).isEqualTo(refreshToken1)

            // Generate different refresh token (but don't save it)
            // Add a small delay to ensure different issuedAt timestamp
            Thread.sleep(1000)
            val refreshToken2 = authenticationService.generateRefreshToken(reloadedUser)

            // Verify that new token is different from stored token
            Assertions.assertThat(refreshToken2).isNotEqualTo(refreshToken1)

            // Try to validate with different token - should fail because stored token doesn't match
            val validatedUser = authenticationService.validateRefreshToken(refreshToken2)
            Assertions.assertThat(validatedUser).isNull()
        }

        @Test
        fun `should reject access token when used as refresh token`() {
            val email = uniqueEmail()
            val password = "TestPassword123!"
            val user =
                SecurityTestDataBuilders.userWithPassword(
                    authenticationService = authenticationService,
                    email = email,
                    password = password,
                )
            saveUser(user)

            // Generate access token
            val accessToken = authenticationService.generateAccessToken(user)

            // Try to validate access token as refresh token - should fail
            val validatedUser = authenticationService.validateRefreshToken(accessToken)
            Assertions.assertThat(validatedUser).isNull()
        }
    }

    @Nested
    @DisplayName("Authenticate By Token (Legacy)")
    inner class AuthenticateByTokenTests {
        @Test
        fun `should authenticate with JWT refresh token via authenticateByToken`() {
            val email = uniqueEmail()
            val password = "TestPassword123!"
            val user =
                SecurityTestDataBuilders.userWithPassword(
                    authenticationService = authenticationService,
                    email = email,
                    password = password,
                )
            saveUser(user)

            // Generate refresh token
            val refreshToken = authenticationService.generateRefreshToken(user)
            authenticationService.saveRememberMeToken(user.id, refreshToken)

            // Authenticate using legacy method
            val authenticatedUser = authenticationService.authenticateByToken(refreshToken)
            Assertions.assertThat(authenticatedUser).isNotNull()
            Assertions.assertThat(authenticatedUser?.email).isEqualTo(email)
        }

        @Test
        fun `should return null when token is invalid via authenticateByToken`() {
            val invalidToken = "invalid-token-123"

            // Try to authenticate with invalid token
            val authenticatedUser = authenticationService.authenticateByToken(invalidToken)
            Assertions.assertThat(authenticatedUser).isNull()
        }
    }

    @Nested
    @DisplayName("Password Reset Token Validation")
    inner class PasswordResetTokenValidationTests {
        @Test
        fun `should validate reset token successfully`() {
            val email = uniqueEmail()
            val password = "TestPassword123!"
            val user =
                SecurityTestDataBuilders.userWithPassword(
                    authenticationService = authenticationService,
                    email = email,
                    password = password,
                )
            saveUser(user)

            // Request password reset
            entityManager.runTransaction {
                authenticationService.requestPasswordReset(email, "en")
                entityManager.flush()
            }

            val savedUser = userRepository.findByEmail(email)
            val token = savedUser!!.passwordResetToken!!

            // Validate token
            val validatedUser = authenticationService.validateResetToken(token)
            Assertions.assertThat(validatedUser).isNotNull()
            Assertions.assertThat(validatedUser?.email).isEqualTo(email)
        }

        @Test
        fun `should reject blank reset token`() {
            val validatedUser = authenticationService.validateResetToken("")
            Assertions.assertThat(validatedUser).isNull()
        }

        @Test
        fun `should reject expired reset token`() {
            val email = uniqueEmail()
            val password = "TestPassword123!"
            val user =
                SecurityTestDataBuilders.userWithPassword(
                    authenticationService = authenticationService,
                    email = email,
                    password = password,
                )
            val expiredToken = UUID.randomUUID().toString()
            user.passwordResetToken = expiredToken
            user.passwordResetExpiresAt = Instant.now().minus(1, ChronoUnit.HOURS)
            saveUser(user)

            // Try to validate expired token
            val validatedUser = authenticationService.validateResetToken(expiredToken)
            Assertions.assertThat(validatedUser).isNull()
        }

        @Test
        fun `should reject already used reset token`() {
            val email = uniqueEmail()
            val password = "TestPassword123!"
            val user =
                SecurityTestDataBuilders.userWithPassword(
                    authenticationService = authenticationService,
                    email = email,
                    password = password,
                )
            val usedToken = UUID.randomUUID().toString()
            user.passwordResetToken = usedToken
            user.passwordResetExpiresAt = Instant.now().plus(1, ChronoUnit.HOURS)
            user.passwordResetUsedAt = Instant.now()
            saveUser(user)

            // Try to validate used token
            val validatedUser = authenticationService.validateResetToken(usedToken)
            Assertions.assertThat(validatedUser).isNull()
        }

        @Test
        fun `should reject reset token when expiration is null`() {
            val email = uniqueEmail()
            val password = "TestPassword123!"
            val user =
                SecurityTestDataBuilders.userWithPassword(
                    authenticationService = authenticationService,
                    email = email,
                    password = password,
                )
            val token = UUID.randomUUID().toString()
            user.passwordResetToken = token
            user.passwordResetExpiresAt = null
            saveUser(user)

            // Try to validate token with null expiration
            val validatedUser = authenticationService.validateResetToken(token)
            Assertions.assertThat(validatedUser).isNull()
        }
    }

    @Nested
    @DisplayName("Password Strength Validation")
    inner class PasswordStrengthValidationTests {
        @Test
        fun `should reject password shorter than 8 characters`() {
            val isValid = authenticationService.validatePasswordStrength("Short1!")
            Assertions.assertThat(isValid).isFalse()
        }

        @Test
        fun `should reject password without uppercase letter`() {
            val isValid = authenticationService.validatePasswordStrength("lowercase123!")
            Assertions.assertThat(isValid).isFalse()
        }

        @Test
        fun `should reject password without lowercase letter`() {
            val isValid = authenticationService.validatePasswordStrength("UPPERCASE123!")
            Assertions.assertThat(isValid).isFalse()
        }

        @Test
        fun `should reject password without number`() {
            val isValid = authenticationService.validatePasswordStrength("NoNumbers!")
            Assertions.assertThat(isValid).isFalse()
        }

        @Test
        fun `should reject password without special character`() {
            val isValid = authenticationService.validatePasswordStrength("NoSpecial123")
            Assertions.assertThat(isValid).isFalse()
        }

        @Test
        fun `should accept valid password`() {
            val isValid = authenticationService.validatePasswordStrength("ValidPass123!")
            Assertions.assertThat(isValid).isTrue()
        }
    }

    @Nested
    @DisplayName("Authentication Edge Cases")
    inner class AuthenticationEdgeCasesTests {
        @Test
        fun `should return null when authenticating with blank password`() {
            val email = uniqueEmail()
            val password = "TestPassword123!"
            val user =
                SecurityTestDataBuilders.userWithPassword(
                    authenticationService = authenticationService,
                    email = email,
                    password = password,
                )
            saveUser(user)

            // Try to authenticate with blank password
            val authenticatedUser = authenticationService.authenticate(email, "")
            Assertions.assertThat(authenticatedUser).isNull()
        }

        @Test
        fun `should return null when user has no password hash`() {
            val email = uniqueEmail()
            val user = SecurityTestDataBuilders.user(email = email, passwordHash = null)
            saveUser(user)

            // Try to authenticate user without password hash
            val authenticatedUser = authenticationService.authenticate(email, "SomePassword123!")
            Assertions.assertThat(authenticatedUser).isNull()
        }

        @Test
        fun `should handle password verification error gracefully`() {
            val email = uniqueEmail()
            val user =
                SecurityTestDataBuilders.user(
                    email = email,
                    passwordHash = "invalid-hash-format",
                )
            saveUser(user)

            // Try to verify password with invalid hash format
            val isValid = authenticationService.verifyPassword("SomePassword123!", user.passwordHash!!)
            Assertions.assertThat(isValid).isFalse()
        }
    }

    @Nested
    @DisplayName("User Registration Edge Cases")
    inner class UserRegistrationEdgeCasesTests {
        @Test
        fun `should throw exception when registering with existing email`() {
            val email = uniqueEmail()
            val password = "TestPassword123!"
            val user =
                SecurityTestDataBuilders.userWithPassword(
                    authenticationService = authenticationService,
                    email = email,
                    password = password,
                )
            saveUser(user)

            // Try to register with same email
            Assertions.assertThatThrownBy {
                authenticationService.registerUser("New User", email, "NewPassword123!")
            }.isInstanceOf(IllegalArgumentException::class.java)
                .hasMessageContaining("Email already exists")
        }

        @Test
        fun `should throw exception when registering with weak password`() {
            val email = uniqueEmail()

            // Try to register with weak password
            Assertions.assertThatThrownBy {
                authenticationService.registerUser("New User", email, "weak")
            }.isInstanceOf(IllegalArgumentException::class.java)
                .hasMessageContaining("Password must be at least 8 characters")
        }
    }

    @Nested
    @DisplayName("Remember Me Token Management")
    inner class RememberMeTokenManagementTests {
        @Test
        fun `should save remember me token successfully`() {
            val email = uniqueEmail()
            val password = "TestPassword123!"
            val user =
                SecurityTestDataBuilders.userWithPassword(
                    authenticationService = authenticationService,
                    email = email,
                    password = password,
                )
            saveUser(user)

            val token = "test-remember-me-token"
            val updatedUser = authenticationService.saveRememberMeToken(user.id, token)

            Assertions.assertThat(updatedUser.rememberMeToken).isEqualTo(token)
        }

        @Test
        fun `should throw exception when saving token for non-existent user`() {
            val nonExistentUserId = UUID.randomUUID()
            val token = "test-token"

            Assertions.assertThatThrownBy {
                authenticationService.saveRememberMeToken(nonExistentUserId, token)
            }.isInstanceOf(IllegalStateException::class.java)
                .hasMessageContaining("User not found")
        }

        @Test
        fun `should clear remember me token successfully`() {
            val email = uniqueEmail()
            val password = "TestPassword123!"
            val user =
                SecurityTestDataBuilders.userWithPassword(
                    authenticationService = authenticationService,
                    email = email,
                    password = password,
                )
            saveUser(user)

            // Set token first
            val token = "test-token"
            authenticationService.saveRememberMeToken(user.id, token)

            // Clear token
            val updatedUser = authenticationService.clearRememberMeToken(user.id)

            Assertions.assertThat(updatedUser.rememberMeToken).isNull()
        }

        @Test
        fun `should throw exception when clearing token for non-existent user`() {
            val nonExistentUserId = UUID.randomUUID()

            Assertions.assertThatThrownBy {
                authenticationService.clearRememberMeToken(nonExistentUserId)
            }.isInstanceOf(IllegalStateException::class.java)
                .hasMessageContaining("User not found")
        }
    }

    @Nested
    @DisplayName("Password Reset Request Edge Cases")
    inner class PasswordResetRequestEdgeCasesTests {
        @Test
        fun `should return true when requesting reset for non-existent email`() {
            val email = uniqueEmail()

            // Request password reset for non-existent email
            val result = authenticationService.requestPasswordReset(email, "en")
            Assertions.assertThat(result).isTrue()
        }

        @Test
        fun `should return true when requesting reset for user without password`() {
            val email = uniqueEmail()
            val user = SecurityTestDataBuilders.user(email = email, passwordHash = null)
            saveUser(user)

            // Request password reset for user without password (OAuth user)
            val result = authenticationService.requestPasswordReset(email, "en")
            Assertions.assertThat(result).isTrue()
        }

        @Test
        fun `should return true even when rate limited`() {
            val email = uniqueEmail()
            val password = "TestPassword123!"
            val user =
                SecurityTestDataBuilders.userWithPassword(
                    authenticationService = authenticationService,
                    email = email,
                    password = password,
                )
            saveUser(user)

            // Request password reset multiple times to trigger rate limiting
            for (i in 1..10) {
                authenticationService.requestPasswordReset(email, "en")
            }

            // Should still return true even when rate limited (security best practice)
            val result = authenticationService.requestPasswordReset(email, "en")
            Assertions.assertThat(result).isTrue()
        }
    }

    @Nested
    @DisplayName("Password Reset Edge Cases")
    inner class PasswordResetEdgeCasesTests {
        @Test
        fun `should throw exception when resetting with invalid token`() {
            val invalidToken = UUID.randomUUID().toString()
            val newPassword = "NewPassword123!"

            Assertions.assertThatThrownBy {
                authenticationService.resetPassword(invalidToken, newPassword)
            }.isInstanceOf(IllegalArgumentException::class.java)
                .hasMessageContaining("Invalid or expired reset token")
        }

        @Test
        fun `should throw exception when resetting with weak password`() {
            val email = uniqueEmail()
            val password = "TestPassword123!"
            val user =
                SecurityTestDataBuilders.userWithPassword(
                    authenticationService = authenticationService,
                    email = email,
                    password = password,
                )
            saveUser(user)

            // Request password reset
            entityManager.runTransaction {
                authenticationService.requestPasswordReset(email, "en")
                entityManager.flush()
            }

            val savedUser = userRepository.findByEmail(email)
            val token = savedUser!!.passwordResetToken!!

            // Try to reset with weak password
            Assertions.assertThatThrownBy {
                authenticationService.resetPassword(token, "weak")
            }.isInstanceOf(IllegalArgumentException::class.java)
                .hasMessageContaining("Password must be at least 8 characters")
        }
    }
}
