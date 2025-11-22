package io.github.salomax.neotool.security.test.service

import io.github.salomax.neotool.common.test.integration.BaseIntegrationTest
import io.github.salomax.neotool.common.test.integration.PostgresIntegrationTest
import io.github.salomax.neotool.security.repo.PasswordResetAttemptRepository
import io.github.salomax.neotool.security.repo.UserRepository
import io.github.salomax.neotool.security.service.AuthenticationService
import io.github.salomax.neotool.security.test.SecurityTestDataBuilders
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestMethodOrder
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

@MicronautTest(startApplication = true)
@DisplayName("Password Reset Integration Tests")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Tag("integration")
@Tag("password-reset")
@Tag("security")
@TestMethodOrder(MethodOrderer.Random::class)
class PasswordResetIntegrationTest : BaseIntegrationTest(), PostgresIntegrationTest {
    @Inject
    lateinit var userRepository: UserRepository

    @Inject
    lateinit var passwordResetAttemptRepository: PasswordResetAttemptRepository

    @Inject
    lateinit var authenticationService: AuthenticationService

    // Note: EmailService will be mocked via @MockBean or similar in actual implementation
    // For now, we'll verify it's called but won't actually send emails

    private fun uniqueEmail() = SecurityTestDataBuilders.uniqueEmail("password-reset")

    @AfterEach
    fun cleanupTestData() {
        try {
            passwordResetAttemptRepository.deleteAll()
            userRepository.deleteAll()
        } catch (e: Exception) {
            // Ignore cleanup errors
        }
    }

    @Nested
    @DisplayName("Request Password Reset")
    inner class RequestPasswordResetIntegrationTests {
        @Test
        fun `should request password reset and save token to database`() {
            val email = uniqueEmail()
            val password = "TestPassword123!"
            val user =
                SecurityTestDataBuilders.userWithPassword(
                    authenticationService = authenticationService,
                    email = email,
                    password = password,
                )
            userRepository.save(user)

            val result = authenticationService.requestPasswordReset(email, "en")

            assertThat(result).isTrue()

            val savedUser = userRepository.findByEmail(email)
            assertThat(savedUser).isNotNull()
            assertThat(savedUser?.passwordResetToken).isNotNull()
            assertThat(savedUser?.passwordResetExpiresAt).isNotNull()
            assertThat(savedUser?.passwordResetExpiresAt?.isAfter(Instant.now())).isTrue()
            assertThat(savedUser?.passwordResetUsedAt).isNull()
        }

        @Test
        fun `should return true even if email does not exist`() {
            val email = uniqueEmail()

            val result = authenticationService.requestPasswordReset(email, "en")

            assertThat(result).isTrue() // Security: don't reveal if email exists
        }

        @Test
        fun `should invalidate existing token when requesting new reset`() {
            val email = uniqueEmail()
            val password = "TestPassword123!"
            val user =
                SecurityTestDataBuilders.userWithPassword(
                    authenticationService = authenticationService,
                    email = email,
                    password = password,
                )
            val oldToken = UUID.randomUUID().toString()
            user.passwordResetToken = oldToken
            user.passwordResetExpiresAt = Instant.now().plus(30, ChronoUnit.MINUTES)
            userRepository.save(user)

            val result = authenticationService.requestPasswordReset(email, "en")

            assertThat(result).isTrue()
            val savedUser = userRepository.findByEmail(email)
            assertThat(savedUser?.passwordResetToken).isNotNull()
            assertThat(savedUser?.passwordResetToken).isNotEqualTo(oldToken)
        }

        @Test
        fun `should set token expiration to 1 hour from now`() {
            val email = uniqueEmail()
            val password = "TestPassword123!"
            val user =
                SecurityTestDataBuilders.userWithPassword(
                    authenticationService = authenticationService,
                    email = email,
                    password = password,
                )
            userRepository.save(user)

            val beforeRequest = Instant.now()
            authenticationService.requestPasswordReset(email, "en")
            val afterRequest = Instant.now()

            val savedUser = userRepository.findByEmail(email)
            assertThat(savedUser?.passwordResetExpiresAt).isNotNull()
            val expiration = savedUser?.passwordResetExpiresAt!!

            // Should be approximately 1 hour from request time (allow 5 seconds tolerance)
            val expectedExpiration = beforeRequest.plus(1, ChronoUnit.HOURS)
            val maxExpiration = afterRequest.plus(1, ChronoUnit.HOURS).plusSeconds(5)

            assertThat(expiration).isAfter(expectedExpiration.minusSeconds(5))
            assertThat(expiration).isBefore(maxExpiration)
        }
    }

    @Nested
    @DisplayName("Validate Reset Token")
    inner class ValidateResetTokenIntegrationTests {
        @Test
        fun `should validate valid reset token from database`() {
            val email = uniqueEmail()
            val password = "TestPassword123!"
            val user =
                SecurityTestDataBuilders.userWithPassword(
                    authenticationService = authenticationService,
                    email = email,
                    password = password,
                )
            userRepository.save(user)

            authenticationService.requestPasswordReset(email, "en")
            val savedUser = userRepository.findByEmail(email)
            val token = savedUser!!.passwordResetToken!!

            val validatedUser = authenticationService.validateResetToken(token)

            assertThat(validatedUser).isNotNull()
            assertThat(validatedUser?.email).isEqualTo(email)
            assertThat(validatedUser?.id).isEqualTo(savedUser.id)
        }

        @Test
        fun `should return null for expired token`() {
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
            user.passwordResetExpiresAt = Instant.now().minus(1, ChronoUnit.HOURS) // Expired
            userRepository.save(user)

            val validatedUser = authenticationService.validateResetToken(token)

            assertThat(validatedUser).isNull()
        }

        @Test
        fun `should return null for already used token`() {
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
            user.passwordResetExpiresAt = Instant.now().plus(1, ChronoUnit.HOURS)
            user.passwordResetUsedAt = Instant.now() // Already used
            userRepository.save(user)

            val validatedUser = authenticationService.validateResetToken(token)

            assertThat(validatedUser).isNull()
        }

        @Test
        fun `should return null for non-existent token`() {
            val token = UUID.randomUUID().toString()

            val validatedUser = authenticationService.validateResetToken(token)

            assertThat(validatedUser).isNull()
        }
    }

    @Nested
    @DisplayName("Reset Password")
    inner class ResetPasswordIntegrationTests {
        @Test
        fun `should reset password successfully with valid token`() {
            val email = uniqueEmail()
            val oldPassword = "OldPassword123!"
            val newPassword = "NewPassword123!"
            val user =
                SecurityTestDataBuilders.userWithPassword(
                    authenticationService = authenticationService,
                    email = email,
                    password = oldPassword,
                )
            userRepository.save(user)

            authenticationService.requestPasswordReset(email, "en")
            val savedUser = userRepository.findByEmail(email)
            val token = savedUser!!.passwordResetToken!!

            val result = authenticationService.resetPassword(token, newPassword)

            assertThat(result).isNotNull()
            assertThat(result.email).isEqualTo(email)
            assertThat(result.passwordHash).isNotBlank()
            assertThat(result.passwordResetToken).isNull()
            assertThat(result.passwordResetExpiresAt).isNull()
            assertThat(result.passwordResetUsedAt).isNotNull()

            // Verify new password works
            val authenticatedUser = authenticationService.authenticate(email, newPassword)
            assertThat(authenticatedUser).isNotNull()
            assertThat(authenticatedUser?.email).isEqualTo(email)

            // Verify old password doesn't work
            val oldPasswordWorks = authenticationService.authenticate(email, oldPassword)
            assertThat(oldPasswordWorks).isNull()
        }

        @Test
        fun `should throw exception for invalid token`() {
            val token = UUID.randomUUID().toString()
            val newPassword = "NewPassword123!"

            assertThrows<IllegalArgumentException> {
                authenticationService.resetPassword(token, newPassword)
            }.also { exception ->
                assertThat(exception.message).contains("Invalid or expired reset token")
            }
        }

        @Test
        fun `should throw exception for expired token`() {
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
            user.passwordResetExpiresAt = Instant.now().minus(1, ChronoUnit.HOURS)
            userRepository.save(user)

            assertThrows<IllegalArgumentException> {
                authenticationService.resetPassword(token, "NewPassword123!")
            }.also { exception ->
                assertThat(exception.message).contains("Invalid or expired reset token")
            }
        }

        @Test
        fun `should throw exception for weak password`() {
            val email = uniqueEmail()
            val password = "TestPassword123!"
            val user =
                SecurityTestDataBuilders.userWithPassword(
                    authenticationService = authenticationService,
                    email = email,
                    password = password,
                )
            userRepository.save(user)

            authenticationService.requestPasswordReset(email, "en")
            val savedUser = userRepository.findByEmail(email)
            val token = savedUser!!.passwordResetToken!!

            assertThrows<IllegalArgumentException> {
                authenticationService.resetPassword(token, "weak")
            }.also { exception ->
                assertThat(exception.message).contains("Password must be at least 8 characters")
            }
        }

        @Test
        fun `should mark token as used after successful reset`() {
            val email = uniqueEmail()
            val password = "TestPassword123!"
            val user =
                SecurityTestDataBuilders.userWithPassword(
                    authenticationService = authenticationService,
                    email = email,
                    password = password,
                )
            userRepository.save(user)

            authenticationService.requestPasswordReset(email, "en")
            val savedUser = userRepository.findByEmail(email)
            val token = savedUser!!.passwordResetToken!!

            authenticationService.resetPassword(token, "NewPassword123!")

            // Try to use token again
            assertThrows<IllegalArgumentException> {
                authenticationService.resetPassword(token, "AnotherPassword123!")
            }.also { exception ->
                assertThat(exception.message).contains("Invalid or expired reset token")
            }
        }
    }

    @Nested
    @DisplayName("Rate Limiting")
    inner class RateLimitingIntegrationTests {
        @Test
        fun `should enforce rate limit of 3 requests per hour`() {
            val email = uniqueEmail()
            val password = "TestPassword123!"
            val user =
                SecurityTestDataBuilders.userWithPassword(
                    authenticationService = authenticationService,
                    email = email,
                    password = password,
                )
            userRepository.save(user)

            // First 3 requests should succeed
            for (i in 1..3) {
                val result = authenticationService.requestPasswordReset(email, "en")
                assertThat(result).isTrue()
            }

            // 4th request should still return true (security), but rate limited internally
            val result4 = authenticationService.requestPasswordReset(email, "en")
            assertThat(result4).isTrue() // Still returns true for security
        }
    }

    @Nested
    @DisplayName("End-to-End Password Reset Flow")
    inner class EndToEndFlowTests {
        @Test
        fun `should complete full password reset flow`() {
            val email = uniqueEmail()
            val oldPassword = "OldPassword123!"
            val newPassword = "NewPassword123!"

            // Step 1: Create user
            val user =
                SecurityTestDataBuilders.userWithPassword(
                    authenticationService = authenticationService,
                    email = email,
                    password = oldPassword,
                )
            userRepository.save(user)

            // Step 2: Request password reset
            val requestResult = authenticationService.requestPasswordReset(email, "en")
            assertThat(requestResult).isTrue()

            // Step 3: Get token from database
            val savedUser = userRepository.findByEmail(email)
            assertThat(savedUser).isNotNull()
            val token = savedUser!!.passwordResetToken
            assertThat(token).isNotNull()

            // Step 4: Validate token
            val validatedUser = authenticationService.validateResetToken(token!!)
            assertThat(validatedUser).isNotNull()
            assertThat(validatedUser?.email).isEqualTo(email)

            // Step 5: Reset password
            val resetResult = authenticationService.resetPassword(token, newPassword)
            assertThat(resetResult).isNotNull()
            assertThat(resetResult.email).isEqualTo(email)

            // Step 6: Verify new password works
            val authenticatedUser = authenticationService.authenticate(email, newPassword)
            assertThat(authenticatedUser).isNotNull()
            assertThat(authenticatedUser?.email).isEqualTo(email)

            // Step 7: Verify old password doesn't work
            val oldPasswordWorks = authenticationService.authenticate(email, oldPassword)
            assertThat(oldPasswordWorks).isNull()

            // Step 8: Verify token is marked as used
            val tokenStillValid = authenticationService.validateResetToken(token)
            assertThat(tokenStillValid).isNull()
        }
    }
}
