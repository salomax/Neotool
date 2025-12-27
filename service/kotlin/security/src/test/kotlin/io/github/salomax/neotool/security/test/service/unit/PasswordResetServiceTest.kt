package io.github.salomax.neotool.security.test.service.unit

import io.github.salomax.neotool.security.config.JwtConfig
import io.github.salomax.neotool.security.model.UserEntity
import io.github.salomax.neotool.security.repo.PasswordResetAttemptRepository
import io.github.salomax.neotool.security.repo.PrincipalRepository
import io.github.salomax.neotool.security.repo.UserRepository
import io.github.salomax.neotool.security.service.AuthenticationService
import io.github.salomax.neotool.security.service.EmailService
import io.github.salomax.neotool.security.service.JwtService
import io.github.salomax.neotool.security.service.OAuthProvider
import io.github.salomax.neotool.security.service.OAuthProviderRegistry
import io.github.salomax.neotool.security.service.RateLimitService
import io.github.salomax.neotool.security.test.SecurityTestDataBuilders
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.ArgumentCaptor
import org.mockito.kotlin.any
import org.mockito.kotlin.doNothing
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

@DisplayName("Password Reset Service Unit Tests")
class PasswordResetServiceTest {
    private lateinit var userRepository: UserRepository
    private lateinit var jwtService: JwtService
    private lateinit var emailService: EmailService
    private lateinit var rateLimitService: RateLimitService
    private lateinit var authenticationService: AuthenticationService

    @BeforeEach
    fun setUp() {
        userRepository = mock()
        val jwtConfig =
            JwtConfig(
                secret = "test-secret-key-minimum-32-characters-long-for-hmac-sha256",
                accessTokenExpirationSeconds = 900L,
                refreshTokenExpirationSeconds = 604800L,
            )
        jwtService = JwtService(jwtConfig)
        emailService = mock()

        val passwordResetAttemptRepository: PasswordResetAttemptRepository = mock()
        rateLimitService = RateLimitService(passwordResetAttemptRepository)

        val oauthProvider: OAuthProvider = mock()
        whenever(oauthProvider.getProviderName()).thenReturn("google")
        val oauthProviderRegistry = OAuthProviderRegistry(listOf(oauthProvider))

        val principalRepository: PrincipalRepository = mock()
        authenticationService =
            AuthenticationService(
                userRepository,
                principalRepository,
                jwtService,
                emailService,
                rateLimitService,
                oauthProviderRegistry,
            )
    }

    @Nested
    @DisplayName("Request Password Reset")
    inner class RequestPasswordResetTests {
        @Test
        fun `should request password reset successfully`() {
            val email = "test@example.com"
            val user =
                SecurityTestDataBuilders.userWithPassword(
                    authenticationService = authenticationService,
                    email = email,
                    password = "TestPassword123!",
                )

            whenever(userRepository.findByEmail(email)).thenReturn(user)
            whenever(userRepository.save(any())).thenAnswer { it.arguments[0] as UserEntity }
            doNothing().whenever(emailService).sendPasswordResetEmail(any(), any(), any())

            val result = authenticationService.requestPasswordReset(email, "en")

            assertThat(result).isTrue()
            verify(userRepository).findByEmail(email)
            verify(userRepository).save(any())
            verify(emailService).sendPasswordResetEmail(eq(email), any(), eq("en"))

            val captor = ArgumentCaptor.forClass(UserEntity::class.java)
            verify(userRepository).save(captor.capture())
            val savedUser = captor.value
            assertThat(savedUser.passwordResetToken).isNotNull()
            assertThat(savedUser.passwordResetExpiresAt).isNotNull()
            assertThat(savedUser.passwordResetUsedAt).isNull()
        }

        @Test
        fun `should return true even if email does not exist`() {
            val email = "nonexistent@example.com"

            whenever(userRepository.findByEmail(email)).thenReturn(null)

            val result = authenticationService.requestPasswordReset(email, "en")

            assertThat(result).isTrue() // Security: don't reveal if email exists
            verify(userRepository).findByEmail(email)
            verify(userRepository, never()).save(any())
            verify(emailService, never()).sendPasswordResetEmail(any(), any(), any())
        }

        @Test
        fun `should return true even if user has no password`() {
            val email = "oauth@example.com"
            val user =
                SecurityTestDataBuilders.user(
                    email = email,
                    passwordHash = null,
                )

            whenever(userRepository.findByEmail(email)).thenReturn(user)

            val result = authenticationService.requestPasswordReset(email, "en")

            assertThat(result).isTrue() // Security: don't reveal if user has password
            verify(userRepository).findByEmail(email)
            verify(userRepository, never()).save(any())
            verify(emailService, never()).sendPasswordResetEmail(any(), any(), any())
        }

        @Test
        fun `should invalidate existing reset token when requesting new one`() {
            val email = "test@example.com"
            val user =
                SecurityTestDataBuilders.userWithPassword(
                    authenticationService = authenticationService,
                    email = email,
                    password = "TestPassword123!",
                )
            user.passwordResetToken = "old-token"
            user.passwordResetExpiresAt = Instant.now().plus(30, ChronoUnit.MINUTES)

            whenever(userRepository.findByEmail(email)).thenReturn(user)
            whenever(userRepository.save(any())).thenAnswer { it.arguments[0] as UserEntity }
            doNothing().whenever(emailService).sendPasswordResetEmail(any(), any(), any())

            val result = authenticationService.requestPasswordReset(email, "en")

            assertThat(result).isTrue()
            val captor = ArgumentCaptor.forClass(UserEntity::class.java)
            verify(userRepository).save(captor.capture())
            val savedUser = captor.value
            assertThat(savedUser.passwordResetToken).isNotEqualTo("old-token")
            assertThat(savedUser.passwordResetToken).isNotNull()
        }

        @Test
        fun `should handle email service failure gracefully`() {
            val email = "test@example.com"
            val user =
                SecurityTestDataBuilders.userWithPassword(
                    authenticationService = authenticationService,
                    email = email,
                    password = "TestPassword123!",
                )

            whenever(userRepository.findByEmail(email)).thenReturn(user)
            whenever(userRepository.save(any())).thenAnswer { it.arguments[0] as UserEntity }
            doThrow(RuntimeException("Email service unavailable")).whenever(emailService)
                .sendPasswordResetEmail(any(), any(), any())

            val result = authenticationService.requestPasswordReset(email, "en")

            assertThat(result).isTrue() // Should still return true even if email fails
            verify(emailService).sendPasswordResetEmail(any(), any(), any())
        }
    }

    @Nested
    @DisplayName("Validate Reset Token")
    inner class ValidateResetTokenTests {
        @Test
        fun `should validate valid reset token`() {
            val email = "test@example.com"
            val token = UUID.randomUUID().toString()
            val user = SecurityTestDataBuilders.user(email = email)
            user.passwordResetToken = token
            user.passwordResetExpiresAt = Instant.now().plus(1, ChronoUnit.HOURS)
            user.passwordResetUsedAt = null

            whenever(userRepository.findByPasswordResetToken(token)).thenReturn(user)

            val result = authenticationService.validateResetToken(token)

            assertThat(result).isNotNull()
            assertThat(result?.email).isEqualTo(email)
            verify(userRepository).findByPasswordResetToken(token)
        }

        @Test
        fun `should return null for non-existent token`() {
            val token = UUID.randomUUID().toString()

            whenever(userRepository.findByPasswordResetToken(token)).thenReturn(null)

            val result = authenticationService.validateResetToken(token)

            assertThat(result).isNull()
            verify(userRepository).findByPasswordResetToken(token)
        }

        @Test
        fun `should return null for expired token`() {
            val email = "test@example.com"
            val token = UUID.randomUUID().toString()
            val user = SecurityTestDataBuilders.user(email = email)
            user.passwordResetToken = token
            user.passwordResetExpiresAt = Instant.now().minus(1, ChronoUnit.HOURS) // Expired
            user.passwordResetUsedAt = null

            whenever(userRepository.findByPasswordResetToken(token)).thenReturn(user)

            val result = authenticationService.validateResetToken(token)

            assertThat(result).isNull()
        }

        @Test
        fun `should return null for already used token`() {
            val email = "test@example.com"
            val token = UUID.randomUUID().toString()
            val user = SecurityTestDataBuilders.user(email = email)
            user.passwordResetToken = token
            user.passwordResetExpiresAt = Instant.now().plus(1, ChronoUnit.HOURS)
            user.passwordResetUsedAt = Instant.now() // Already used

            whenever(userRepository.findByPasswordResetToken(token)).thenReturn(user)

            val result = authenticationService.validateResetToken(token)

            assertThat(result).isNull()
        }

        @Test
        fun `should return null for blank token`() {
            val result = authenticationService.validateResetToken("")

            assertThat(result).isNull()
            verify(userRepository, never()).findByPasswordResetToken(any())
        }
    }

    @Nested
    @DisplayName("Reset Password")
    inner class ResetPasswordTests {
        @Test
        fun `should reset password successfully`() {
            val email = "test@example.com"
            val token = UUID.randomUUID().toString()
            val oldPassword = "OldPassword123!"
            val newPassword = "NewPassword123!"
            val user =
                SecurityTestDataBuilders.userWithPassword(
                    authenticationService = authenticationService,
                    email = email,
                    password = oldPassword,
                )
            user.passwordResetToken = token
            user.passwordResetExpiresAt = Instant.now().plus(1, ChronoUnit.HOURS)
            user.passwordResetUsedAt = null

            whenever(userRepository.findByPasswordResetToken(token)).thenReturn(user)
            whenever(userRepository.save(any())).thenAnswer { it.arguments[0] as UserEntity }

            val result = authenticationService.resetPassword(token, newPassword)

            assertThat(result).isNotNull()
            assertThat(result.email).isEqualTo(email)
            assertThat(result.passwordHash).isNotBlank()
            assertThat(result.passwordResetToken).isNull()
            assertThat(result.passwordResetExpiresAt).isNull()
            assertThat(result.passwordResetUsedAt).isNotNull()

            // Verify new password works
            val canVerify = authenticationService.verifyPassword(newPassword, result.passwordHash!!)
            assertThat(canVerify).isTrue()

            // Verify old password doesn't work
            val oldPasswordWorks = authenticationService.verifyPassword(oldPassword, result.passwordHash!!)
            assertThat(oldPasswordWorks).isFalse()
        }

        @Test
        fun `should throw exception for invalid token`() {
            val token = UUID.randomUUID().toString()
            val newPassword = "NewPassword123!"

            whenever(userRepository.findByPasswordResetToken(token)).thenReturn(null)

            assertThrows<IllegalArgumentException> {
                authenticationService.resetPassword(token, newPassword)
            }.also { exception ->
                assertThat(exception.message).contains("Invalid or expired reset token")
            }
        }

        @Test
        fun `should throw exception for expired token`() {
            val email = "test@example.com"
            val token = UUID.randomUUID().toString()
            val user = SecurityTestDataBuilders.user(email = email)
            user.passwordResetToken = token
            user.passwordResetExpiresAt = Instant.now().minus(1, ChronoUnit.HOURS)
            user.passwordResetUsedAt = null

            whenever(userRepository.findByPasswordResetToken(token)).thenReturn(user)

            assertThrows<IllegalArgumentException> {
                authenticationService.resetPassword(token, "NewPassword123!")
            }.also { exception ->
                assertThat(exception.message).contains("Invalid or expired reset token")
            }
        }

        @Test
        fun `should throw exception for weak password`() {
            val email = "test@example.com"
            val token = UUID.randomUUID().toString()
            val user = SecurityTestDataBuilders.user(email = email)
            user.passwordResetToken = token
            user.passwordResetExpiresAt = Instant.now().plus(1, ChronoUnit.HOURS)
            user.passwordResetUsedAt = null

            whenever(userRepository.findByPasswordResetToken(token)).thenReturn(user)

            assertThrows<IllegalArgumentException> {
                authenticationService.resetPassword(token, "weak")
            }.also { exception ->
                assertThat(exception.message).contains("Password must be at least 8 characters")
            }
        }
    }
}
