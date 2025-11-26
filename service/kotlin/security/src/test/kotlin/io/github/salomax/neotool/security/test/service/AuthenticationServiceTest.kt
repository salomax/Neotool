package io.github.salomax.neotool.security.test.service

import io.github.salomax.neotool.security.config.JwtConfig
import io.github.salomax.neotool.security.model.UserEntity
import io.github.salomax.neotool.security.repo.PasswordResetAttemptRepository
import io.github.salomax.neotool.security.repo.UserRepository
import io.github.salomax.neotool.security.service.AuthenticationService
import io.github.salomax.neotool.security.service.EmailService
import io.github.salomax.neotool.security.service.JwtService
import io.github.salomax.neotool.security.service.OAuthProvider
import io.github.salomax.neotool.security.service.OAuthProviderRegistry
import io.github.salomax.neotool.security.service.OAuthUserClaims
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
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.Optional
import java.util.UUID

@DisplayName("AuthenticationService Unit Tests")
class AuthenticationServiceTest {
    private lateinit var userRepository: UserRepository
    private lateinit var jwtService: JwtService
    private lateinit var authenticationService: AuthenticationService
    private lateinit var oauthProvider: OAuthProvider
    private lateinit var oauthProviderRegistry: OAuthProviderRegistry

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
        val emailService: EmailService = mock()
        val passwordResetAttemptRepository: PasswordResetAttemptRepository = mock()
        val rateLimitService = RateLimitService(passwordResetAttemptRepository)
        oauthProvider = mock()
        whenever(oauthProvider.getProviderName()).thenReturn("google")
        oauthProviderRegistry = OAuthProviderRegistry(listOf(oauthProvider))
        authenticationService =
            AuthenticationService(
                userRepository,
                jwtService,
                emailService,
                rateLimitService,
                oauthProviderRegistry,
            )
    }

    @Nested
    @DisplayName("Password Hashing")
    inner class PasswordHashingTests {
        @Test
        fun `should hash password successfully`() {
            val password = "TestPassword123!"
            val hash = authenticationService.hashPassword(password)

            assertThat(hash).isNotBlank()
            assertThat(hash).isNotEqualTo(password)
            assertThat(hash).startsWith("\$argon2id\$") // BCrypt hash prefix
        }

        @Test
        fun `should generate different hashes for same password`() {
            val password = "TestPassword123!"
            val hash1 = authenticationService.hashPassword(password)
            val hash2 = authenticationService.hashPassword(password)

            assertThat(hash1).isNotEqualTo(hash2)
        }

        @Test
        fun `should hash empty password`() {
            val password = ""
            val hash = authenticationService.hashPassword(password)

            assertThat(hash).isNotBlank()
            assertThat(hash).startsWith("\$argon2id\$")
        }
    }

    @Nested
    @DisplayName("Password Verification")
    inner class PasswordVerificationTests {
        @Test
        fun `should verify correct password`() {
            val password = "TestPassword123!"
            val hash = authenticationService.hashPassword(password)

            val result = authenticationService.verifyPassword(password, hash)

            assertThat(result).isTrue()
        }

        @Test
        fun `should reject incorrect password`() {
            val password = "TestPassword123!"
            val wrongPassword = "WrongPassword123!"
            val hash = authenticationService.hashPassword(password)

            val result = authenticationService.verifyPassword(wrongPassword, hash)

            assertThat(result).isFalse()
        }

        @Test
        fun `should handle invalid hash format gracefully`() {
            val password = "TestPassword123!"
            val invalidHash = "invalid-hash-format"

            val result = authenticationService.verifyPassword(password, invalidHash)

            assertThat(result).isFalse()
        }

        @Test
        fun `should handle empty hash gracefully`() {
            val password = "TestPassword123!"

            val result = authenticationService.verifyPassword(password, "")

            assertThat(result).isFalse()
        }
    }

    @Nested
    @DisplayName("User Authentication")
    inner class UserAuthenticationTests {
        @Test
        fun `should authenticate user with correct credentials`() {
            val email = "test@example.com"
            val password = "TestPassword123!"
            val user =
                SecurityTestDataBuilders.userWithPassword(
                    authenticationService = authenticationService,
                    email = email,
                    password = password,
                )

            whenever(userRepository.findByEmail(email)).thenReturn(user)

            val result = authenticationService.authenticate(email, password)

            assertThat(result).isNotNull()
            assertThat(result?.email).isEqualTo(email)
            verify(userRepository).findByEmail(email)
        }

        @Test
        fun `should return null for non-existent user`() {
            val email = "nonexistent@example.com"
            val password = "TestPassword123!"

            whenever(userRepository.findByEmail(email)).thenReturn(null)

            val result = authenticationService.authenticate(email, password)

            assertThat(result).isNull()
            verify(userRepository).findByEmail(email)
        }

        @Test
        fun `should return null for user without password hash`() {
            val email = "test@example.com"
            val password = "TestPassword123!"
            val user =
                SecurityTestDataBuilders.user(
                    email = email,
                    passwordHash = null,
                )

            whenever(userRepository.findByEmail(email)).thenReturn(user)

            val result = authenticationService.authenticate(email, password)

            assertThat(result).isNull()
            verify(userRepository).findByEmail(email)
        }

        @Test
        fun `should return null for incorrect password`() {
            val email = "test@example.com"
            val correctPassword = "CorrectPassword123!"
            val wrongPassword = "WrongPassword123!"
            val user =
                SecurityTestDataBuilders.userWithPassword(
                    authenticationService = authenticationService,
                    email = email,
                    password = correctPassword,
                )

            whenever(userRepository.findByEmail(email)).thenReturn(user)

            val result = authenticationService.authenticate(email, wrongPassword)

            assertThat(result).isNull()
            verify(userRepository).findByEmail(email)
        }

        @Test
        fun `should reject empty password`() {
            val email = "test@example.com"
            val password = ""
            val user =
                SecurityTestDataBuilders.userWithPassword(
                    authenticationService = authenticationService,
                    email = email,
                    password = password,
                )

            whenever(userRepository.findByEmail(email)).thenReturn(user)

            val result = authenticationService.authenticate(email, password)

            assertThat(result).isNull()
            verify(userRepository, never()).findByEmail(email)
        }
    }

    @Nested
    @DisplayName("Remember Me Token")
    inner class RememberMeTokenTests {
        @Test
        fun `should generate remember me token`() {
            val user =
                SecurityTestDataBuilders.user(
                    id = UUID.randomUUID(),
                    email = "test@example.com",
                )
            val token = authenticationService.generateRefreshToken(user)

            assertThat(token).isNotBlank()
            assertThat(token.split(".")).hasSize(3) // JWT has 3 parts
        }

        @Test
        fun `should generate unique tokens`() {
            val token1 = authenticationService.generateRememberMeToken()
            Thread.sleep(1) // Ensure timestamp difference
            val token2 = authenticationService.generateRememberMeToken()

            assertThat(token1).isNotEqualTo(token2)
        }

        @Test
        fun `should save remember me token for user`() {
            val userId = UUID.randomUUID()
            val user =
                SecurityTestDataBuilders.userWithPassword(
                    authenticationService = authenticationService,
                    id = userId,
                    email = "test@example.com",
                    password = "password",
                )
            val token = "test-token-123"

            whenever(userRepository.findById(userId)).thenReturn(Optional.of(user))
            whenever(userRepository.save(any())).thenAnswer { it.arguments[0] as UserEntity }

            val result = authenticationService.saveRememberMeToken(userId, token)

            assertThat(result.rememberMeToken).isEqualTo(token)
            verify(userRepository).findById(userId)
            val captor = ArgumentCaptor.forClass(UserEntity::class.java)
            verify(userRepository).save(captor.capture())
            assertThat(captor.value.rememberMeToken).isEqualTo(token)
        }

        @Test
        fun `should throw exception when saving token for non-existent user`() {
            val userId = UUID.randomUUID()
            val token = "test-token-123"

            whenever(userRepository.findById(userId)).thenReturn(Optional.empty())

            assertThrows<IllegalStateException> {
                authenticationService.saveRememberMeToken(userId, token)
            }

            verify(userRepository).findById(userId)
            verify(userRepository, never()).save(any())
        }

        @Test
        fun `should authenticate user by remember me token`() {
            val token = "test-token-123"
            val user =
                SecurityTestDataBuilders.user(
                    email = "test@example.com",
                    rememberMeToken = token,
                )

            whenever(userRepository.findByRememberMeToken(token)).thenReturn(user)

            val result = authenticationService.authenticateByToken(token)

            assertThat(result).isNotNull()
            assertThat(result?.email).isEqualTo(user.email)
            assertThat(result?.rememberMeToken).isEqualTo(token)
            verify(userRepository).findByRememberMeToken(token)
        }

        @Test
        fun `should return null for invalid remember me token`() {
            val token = "invalid-token"

            whenever(userRepository.findByRememberMeToken(token)).thenReturn(null)

            val result = authenticationService.authenticateByToken(token)

            assertThat(result).isNull()
            verify(userRepository).findByRememberMeToken(token)
        }

        @Test
        fun `should clear remember me token for user`() {
            val userId = UUID.randomUUID()
            val user =
                SecurityTestDataBuilders.user(
                    id = userId,
                    email = "test@example.com",
                    rememberMeToken = "existing-token",
                )

            whenever(userRepository.findById(userId)).thenReturn(Optional.of(user))
            whenever(userRepository.save(any())).thenAnswer { it.arguments[0] as UserEntity }

            val result = authenticationService.clearRememberMeToken(userId)

            assertThat(result.rememberMeToken).isNull()
            verify(userRepository).findById(userId)
            val captor = ArgumentCaptor.forClass(UserEntity::class.java)
            verify(userRepository).save(captor.capture())
            assertThat(captor.value.rememberMeToken).isNull()
        }

        @Test
        fun `should throw exception when clearing token for non-existent user`() {
            val userId = UUID.randomUUID()

            whenever(userRepository.findById(userId)).thenReturn(Optional.empty())

            assertThrows<IllegalStateException> {
                authenticationService.clearRememberMeToken(userId)
            }

            verify(userRepository).findById(userId)
            verify(userRepository, never()).save(any())
        }
    }

    @Nested
    @DisplayName("JWT Token Generation")
    inner class JwtTokenGenerationTests {
        @Test
        fun `should generate access token for user`() {
            val user =
                SecurityTestDataBuilders.user(
                    id = UUID.randomUUID(),
                    email = "test@example.com",
                )

            val token = authenticationService.generateAccessToken(user)

            assertThat(token).isNotBlank()
            assertThat(token.split(".")).hasSize(3) // JWT has 3 parts
        }

        @Test
        fun `should generate refresh token for user`() {
            val user =
                SecurityTestDataBuilders.user(
                    id = UUID.randomUUID(),
                    email = "test@example.com",
                )

            val token = authenticationService.generateRefreshToken(user)

            assertThat(token).isNotBlank()
            assertThat(token.split(".")).hasSize(3) // JWT has 3 parts
        }

        @Test
        fun `should generate different tokens for same user`() {
            val user =
                SecurityTestDataBuilders.user(
                    id = UUID.randomUUID(),
                    email = "test@example.com",
                )

            val accessToken = authenticationService.generateAccessToken(user)
            val refreshToken = authenticationService.generateRefreshToken(user)

            assertThat(accessToken).isNotEqualTo(refreshToken)
        }
    }

    @Nested
    @DisplayName("JWT Access Token Validation")
    inner class JwtAccessTokenValidationTests {
        @Test
        fun `should validate valid access token and return user`() {
            val userId = UUID.randomUUID()
            val email = "test@example.com"
            val user =
                SecurityTestDataBuilders.user(
                    id = userId,
                    email = email,
                )

            val token = authenticationService.generateAccessToken(user)
            whenever(userRepository.findById(userId)).thenReturn(Optional.of(user))

            val result = authenticationService.validateAccessToken(token)

            assertThat(result).isNotNull()
            assertThat(result?.id).isEqualTo(userId)
            assertThat(result?.email).isEqualTo(email)
            verify(userRepository).findById(userId)
        }

        @Test
        fun `should return null for invalid access token`() {
            val invalidToken = "invalid.token.format"

            val result = authenticationService.validateAccessToken(invalidToken)

            assertThat(result).isNull()
            verify(userRepository, never()).findById(any())
        }

        @Test
        fun `should return null for refresh token used as access token`() {
            val userId = UUID.randomUUID()
            val user =
                SecurityTestDataBuilders.user(
                    id = userId,
                    email = "test@example.com",
                )

            val refreshToken = authenticationService.generateRefreshToken(user)

            val result = authenticationService.validateAccessToken(refreshToken)

            assertThat(result).isNull()
            verify(userRepository, never()).findById(any())
        }

        @Test
        fun `should return null when user not found for valid token`() {
            val userId = UUID.randomUUID()
            val user =
                SecurityTestDataBuilders.user(
                    id = userId,
                    email = "test@example.com",
                )

            val token = authenticationService.generateAccessToken(user)
            whenever(userRepository.findById(userId)).thenReturn(Optional.empty())

            val result = authenticationService.validateAccessToken(token)

            assertThat(result).isNull()
            verify(userRepository).findById(userId)
        }
    }

    @Nested
    @DisplayName("JWT Refresh Token Validation")
    inner class JwtRefreshTokenValidationTests {
        @Test
        fun `should validate valid refresh token and return user`() {
            val userId = UUID.randomUUID()
            val email = "test@example.com"
            val user =
                SecurityTestDataBuilders.user(
                    id = userId,
                    email = email,
                )

            val refreshToken = authenticationService.generateRefreshToken(user)
            user.rememberMeToken = refreshToken
            whenever(userRepository.findById(userId)).thenReturn(Optional.of(user))

            val result = authenticationService.validateRefreshToken(refreshToken)

            assertThat(result).isNotNull()
            assertThat(result?.id).isEqualTo(userId)
            assertThat(result?.email).isEqualTo(email)
            verify(userRepository).findById(userId)
        }

        @Test
        fun `should return null for invalid refresh token`() {
            val invalidToken = "invalid.token.format"

            val result = authenticationService.validateRefreshToken(invalidToken)

            assertThat(result).isNull()
            verify(userRepository, never()).findById(any())
        }

        @Test
        fun `should return null for access token used as refresh token`() {
            val userId = UUID.randomUUID()
            val user =
                SecurityTestDataBuilders.user(
                    id = userId,
                    email = "test@example.com",
                )

            val accessToken = authenticationService.generateAccessToken(user)

            val result = authenticationService.validateRefreshToken(accessToken)

            assertThat(result).isNull()
            verify(userRepository, never()).findById(any())
        }

        @Test
        fun `should return null when refresh token was revoked`() {
            val userId = UUID.randomUUID()
            val user =
                SecurityTestDataBuilders.user(
                    id = userId,
                    email = "test@example.com",
                    // Token was revoked
                    rememberMeToken = null,
                )

            val refreshToken = authenticationService.generateRefreshToken(user)
            whenever(userRepository.findById(userId)).thenReturn(Optional.of(user))

            val result = authenticationService.validateRefreshToken(refreshToken)

            assertThat(result).isNull()
            verify(userRepository).findById(userId)
        }

        @Test
        fun `should return null when refresh token does not match stored token`() {
            val userId = UUID.randomUUID()
            val user =
                SecurityTestDataBuilders.user(
                    id = userId,
                    email = "test@example.com",
                    rememberMeToken = "different-token",
                )

            val refreshToken = authenticationService.generateRefreshToken(user)
            whenever(userRepository.findById(userId)).thenReturn(Optional.of(user))

            val result = authenticationService.validateRefreshToken(refreshToken)

            assertThat(result).isNull()
            verify(userRepository).findById(userId)
        }

        @Test
        fun `should return null when user not found for valid refresh token`() {
            val userId = UUID.randomUUID()
            val user =
                SecurityTestDataBuilders.user(
                    id = userId,
                    email = "test@example.com",
                )

            val refreshToken = authenticationService.generateRefreshToken(user)
            whenever(userRepository.findById(userId)).thenReturn(Optional.empty())

            val result = authenticationService.validateRefreshToken(refreshToken)

            assertThat(result).isNull()
            verify(userRepository).findById(userId)
        }
    }

    @Nested
    @DisplayName("Password Strength Validation")
    inner class PasswordStrengthValidationTests {
        @Test
        fun `should validate password with all requirements`() {
            val password = "TestPassword123!"

            val result = authenticationService.validatePasswordStrength(password)

            assertThat(result).isTrue()
        }

        @Test
        fun `should reject password shorter than 8 characters`() {
            val password = "Short1!"

            val result = authenticationService.validatePasswordStrength(password)

            assertThat(result).isFalse()
        }

        @Test
        fun `should reject password without uppercase letter`() {
            val password = "testpassword123!"

            val result = authenticationService.validatePasswordStrength(password)

            assertThat(result).isFalse()
        }

        @Test
        fun `should reject password without lowercase letter`() {
            val password = "TESTPASSWORD123!"

            val result = authenticationService.validatePasswordStrength(password)

            assertThat(result).isFalse()
        }

        @Test
        fun `should reject password without number`() {
            val password = "TestPassword!"

            val result = authenticationService.validatePasswordStrength(password)

            assertThat(result).isFalse()
        }

        @Test
        fun `should reject password without special character`() {
            val password = "TestPassword123"

            val result = authenticationService.validatePasswordStrength(password)

            assertThat(result).isFalse()
        }
    }

    @Nested
    @DisplayName("User Registration")
    inner class UserRegistrationTests {
        @Test
        fun `should register user successfully with valid data`() {
            val name = "Test User"
            val email = "test@example.com"
            val password = "TestPassword123!"

            whenever(userRepository.findByEmail(email)).thenReturn(null)
            whenever(userRepository.save(any())).thenAnswer { it.arguments[0] as UserEntity }

            val result = authenticationService.registerUser(name, email, password)

            assertThat(result).isNotNull()
            assertThat(result.email).isEqualTo(email)
            assertThat(result.displayName).isEqualTo(name)
            assertThat(result.passwordHash).isNotBlank()
            assertThat(result.passwordHash).startsWith("\$argon2id\$")
            verify(userRepository).findByEmail(email)
            verify(userRepository).save(any())
        }

        @Test
        fun `should throw exception when email already exists`() {
            val name = "Test User"
            val email = "existing@example.com"
            val password = "TestPassword123!"
            val existingUser = SecurityTestDataBuilders.user(email = email)

            whenever(userRepository.findByEmail(email)).thenReturn(existingUser)

            assertThrows<IllegalArgumentException> {
                authenticationService.registerUser(name, email, password)
            }.also { exception ->
                assertThat(exception.message).contains("Email already exists")
            }

            verify(userRepository).findByEmail(email)
            verify(userRepository, never()).save(any())
        }

        @Test
        fun `should throw exception when password is too short`() {
            val name = "Test User"
            val email = "test@example.com"
            val password = "Short1!"

            whenever(userRepository.findByEmail(email)).thenReturn(null)

            assertThrows<IllegalArgumentException> {
                authenticationService.registerUser(name, email, password)
            }.also { exception ->
                assertThat(exception.message).contains("Password must be at least 8 characters")
            }

            verify(userRepository).findByEmail(email)
            verify(userRepository, never()).save(any())
        }

        @Test
        fun `should throw exception when password lacks uppercase`() {
            val name = "Test User"
            val email = "test@example.com"
            val password = "testpassword123!"

            whenever(userRepository.findByEmail(email)).thenReturn(null)

            assertThrows<IllegalArgumentException> {
                authenticationService.registerUser(name, email, password)
            }.also { exception ->
                assertThat(exception.message).contains("Password must be at least 8 characters")
            }

            verify(userRepository).findByEmail(email)
            verify(userRepository, never()).save(any())
        }

        @Test
        fun `should throw exception when password lacks lowercase`() {
            val name = "Test User"
            val email = "test@example.com"
            val password = "TESTPASSWORD123!"

            whenever(userRepository.findByEmail(email)).thenReturn(null)

            assertThrows<IllegalArgumentException> {
                authenticationService.registerUser(name, email, password)
            }.also { exception ->
                assertThat(exception.message).contains("Password must be at least 8 characters")
            }

            verify(userRepository).findByEmail(email)
            verify(userRepository, never()).save(any())
        }

        @Test
        fun `should throw exception when password lacks number`() {
            val name = "Test User"
            val email = "test@example.com"
            val password = "TestPassword!"

            whenever(userRepository.findByEmail(email)).thenReturn(null)

            assertThrows<IllegalArgumentException> {
                authenticationService.registerUser(name, email, password)
            }.also { exception ->
                assertThat(exception.message).contains("Password must be at least 8 characters")
            }

            verify(userRepository).findByEmail(email)
            verify(userRepository, never()).save(any())
        }

        @Test
        fun `should throw exception when password lacks special character`() {
            val name = "Test User"
            val email = "test@example.com"
            val password = "TestPassword123"

            whenever(userRepository.findByEmail(email)).thenReturn(null)

            assertThrows<IllegalArgumentException> {
                authenticationService.registerUser(name, email, password)
            }.also { exception ->
                assertThat(exception.message).contains("Password must be at least 8 characters")
            }

            verify(userRepository).findByEmail(email)
            verify(userRepository, never()).save(any())
        }

        @Test
        fun `should hash password during registration`() {
            val name = "Test User"
            val email = "test@example.com"
            val password = "TestPassword123!"

            whenever(userRepository.findByEmail(email)).thenReturn(null)
            whenever(userRepository.save(any())).thenAnswer { it.arguments[0] as UserEntity }

            val result = authenticationService.registerUser(name, email, password)

            assertThat(result.passwordHash).isNotBlank()
            assertThat(result.passwordHash).isNotEqualTo(password)
            assertThat(result.passwordHash).startsWith("\$argon2id\$")

            // Verify password can be verified
            val canVerify = authenticationService.verifyPassword(password, result.passwordHash)
            assertThat(canVerify).isTrue()
        }
    }

    @Nested
    @DisplayName("OAuth Authentication")
    inner class OAuthAuthenticationTests {
        @Test
        fun `should throw exception for unsupported OAuth provider`() {
            val provider = "microsoft"
            val idToken = "test-id-token"

            assertThrows<IllegalArgumentException> {
                authenticationService.authenticateWithOAuth(provider, idToken)
            }.also { exception ->
                assertThat(exception.message).contains("Unsupported OAuth provider")
            }
        }

        @Test
        fun `should throw exception for invalid OAuth token`() {
            val provider = "google"
            val idToken = "invalid-token"

            whenever(oauthProvider.validateAndExtractClaims(idToken)).thenReturn(null)

            assertThrows<IllegalArgumentException> {
                authenticationService.authenticateWithOAuth(provider, idToken)
            }.also { exception ->
                assertThat(exception.message).contains("Invalid OAuth token")
            }

            verify(oauthProvider).validateAndExtractClaims(idToken)
        }

        @Test
        fun `should create new user for OAuth authentication when user does not exist`() {
            val provider = "google"
            val idToken = "valid-google-id-token"
            val email = "oauth@example.com"
            val name = "OAuth User"
            val claims =
                OAuthUserClaims(
                    email = email,
                    name = name,
                    picture = null,
                    emailVerified = true,
                )

            whenever(oauthProvider.validateAndExtractClaims(idToken)).thenReturn(claims)
            whenever(userRepository.findByEmail(email)).thenReturn(null)
            whenever(userRepository.save(any())).thenAnswer { it.arguments[0] as UserEntity }

            val result = authenticationService.authenticateWithOAuth(provider, idToken)

            assertThat(result).isNotNull()
            assertThat(result?.email).isEqualTo(email)
            assertThat(result?.displayName).isEqualTo(name)
            assertThat(result?.passwordHash).isNull() // OAuth users don't have passwords

            verify(oauthProvider).validateAndExtractClaims(idToken)
            verify(userRepository).findByEmail(email)
            verify(userRepository).save(any())
        }

        @Test
        fun `should return existing user for OAuth authentication when user exists`() {
            val provider = "google"
            val idToken = "valid-google-id-token"
            val email = "existing@example.com"
            val name = "Existing User"
            val claims =
                OAuthUserClaims(
                    email = email,
                    name = name,
                    picture = null,
                    emailVerified = true,
                )
            val existingUser = SecurityTestDataBuilders.user(email = email, displayName = "Old Name")

            whenever(oauthProvider.validateAndExtractClaims(idToken)).thenReturn(claims)
            whenever(userRepository.findByEmail(email)).thenReturn(existingUser)
            whenever(userRepository.save(any())).thenAnswer { it.arguments[0] as UserEntity }

            val result = authenticationService.authenticateWithOAuth(provider, idToken)

            assertThat(result).isNotNull()
            assertThat(result?.email).isEqualTo(email)
            assertThat(result?.id).isEqualTo(existingUser.id)

            verify(oauthProvider).validateAndExtractClaims(idToken)
            verify(userRepository).findByEmail(email)
        }

        @Test
        fun `should update display name for existing OAuth user if not set`() {
            val provider = "google"
            val idToken = "valid-google-id-token"
            val email = "existing@example.com"
            val name = "New Name"
            val claims =
                OAuthUserClaims(
                    email = email,
                    name = name,
                    picture = null,
                    emailVerified = true,
                )
            val existingUser = SecurityTestDataBuilders.user(email = email, displayName = null)

            whenever(oauthProvider.validateAndExtractClaims(idToken)).thenReturn(claims)
            whenever(userRepository.findByEmail(email)).thenReturn(existingUser)
            whenever(userRepository.save(any())).thenAnswer { it.arguments[0] as UserEntity }

            val result = authenticationService.authenticateWithOAuth(provider, idToken)

            assertThat(result).isNotNull()
            assertThat(result?.displayName).isEqualTo(name)

            verify(userRepository).save(any())
        }
    }
}
