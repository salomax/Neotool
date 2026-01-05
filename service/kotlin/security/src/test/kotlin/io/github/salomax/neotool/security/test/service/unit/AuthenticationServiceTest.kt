package io.github.salomax.neotool.security.test.service.unit

import io.github.salomax.neotool.common.security.jwt.JwtTokenValidator
import io.github.salomax.neotool.common.security.principal.PrincipalType
import io.github.salomax.neotool.security.model.PrincipalEntity
import io.github.salomax.neotool.security.model.UserEntity
import io.github.salomax.neotool.security.repo.PasswordResetAttemptRepository
import io.github.salomax.neotool.security.repo.PrincipalRepository
import io.github.salomax.neotool.security.repo.UserRepository
import io.github.salomax.neotool.security.service.authentication.AuthenticationService
import io.github.salomax.neotool.security.service.email.EmailService
import io.github.salomax.neotool.security.service.jwt.JwtTokenIssuer
import io.github.salomax.neotool.security.service.jwt.RefreshTokenService
import io.github.salomax.neotool.security.service.oauth.OAuthProvider
import io.github.salomax.neotool.security.service.oauth.OAuthProviderRegistry
import io.github.salomax.neotool.security.service.oauth.OAuthUserClaims
import io.github.salomax.neotool.security.service.rate.RateLimitService
import io.github.salomax.neotool.security.test.SecurityTestDataBuilders
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.ArgumentCaptor
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.Optional
import java.util.UUID

@DisplayName("AuthenticationService Unit Tests")
class AuthenticationServiceTest {
    private lateinit var userRepository: UserRepository
    private lateinit var principalRepository: PrincipalRepository
    private lateinit var jwtTokenIssuer: JwtTokenIssuer
    private lateinit var jwtTokenValidator: JwtTokenValidator
    private lateinit var refreshTokenService: RefreshTokenService
    private lateinit var authenticationService: AuthenticationService
    private lateinit var oauthProvider: OAuthProvider
    private lateinit var oauthProviderRegistry: OAuthProviderRegistry

    @BeforeEach
    fun setUp() {
        userRepository = mock()
        principalRepository = mock()
        jwtTokenIssuer = mock()
        jwtTokenValidator = mock()
        refreshTokenService = mock()
        val emailService: EmailService = mock()
        val passwordResetAttemptRepository: PasswordResetAttemptRepository = mock()
        val rateLimitService = RateLimitService(passwordResetAttemptRepository)
        oauthProvider = mock()
        whenever(oauthProvider.getProviderName()).thenReturn("google")
        oauthProviderRegistry = OAuthProviderRegistry(listOf(oauthProvider))
        authenticationService =
            AuthenticationService(
                userRepository,
                principalRepository,
                jwtTokenIssuer,
                jwtTokenValidator,
                emailService,
                rateLimitService,
                oauthProviderRegistry,
                refreshTokenService,
            )
    }

    /**
     * Create a mock enabled principal for a user.
     * Reduces duplication in tests that check enabled user authentication.
     */
    private fun mockEnabledPrincipal(userId: UUID) {
        val enabledPrincipal =
            PrincipalEntity(
                id = UUID.randomUUID(),
                principalType = PrincipalType.USER,
                externalId = userId.toString(),
                enabled = true,
            )
        whenever(principalRepository.findByPrincipalTypeAndExternalId(PrincipalType.USER, userId.toString()))
            .thenReturn(Optional.of(enabledPrincipal))
    }

    /**
     * Create a mock disabled principal for a user.
     * Reduces duplication in tests that verify disabled users are rejected.
     */
    private fun mockDisabledPrincipal(userId: UUID) {
        val disabledPrincipal =
            PrincipalEntity(
                id = UUID.randomUUID(),
                principalType = PrincipalType.USER,
                externalId = userId.toString(),
                enabled = false,
            )
        whenever(principalRepository.findByPrincipalTypeAndExternalId(PrincipalType.USER, userId.toString()))
            .thenReturn(Optional.of(disabledPrincipal))
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
            assertThat(hash).startsWith("\$argon2id\$") // Argon2id hash prefix
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
            val userId = UUID.randomUUID()
            val user =
                SecurityTestDataBuilders.userWithPassword(
                    authenticationService = authenticationService,
                    id = userId,
                    email = email,
                    password = password,
                )

            whenever(userRepository.findByEmail(email)).thenReturn(user)
            mockEnabledPrincipal(userId)

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

            // No repository mocking needed - password validation happens before repository call

            val result = authenticationService.authenticate(email, password)

            assertThat(result).isNull()
            verify(userRepository, never()).findByEmail(email)
        }

        @Test
        fun `should return null for disabled user`() {
            val email = "test@example.com"
            val password = "TestPassword123!"
            val userId = UUID.randomUUID()
            val user =
                SecurityTestDataBuilders.userWithPassword(
                    authenticationService = authenticationService,
                    id = userId,
                    email = email,
                    password = password,
                )

            whenever(userRepository.findByEmail(email)).thenReturn(user)
            mockDisabledPrincipal(userId)

            val result = authenticationService.authenticate(email, password)

            assertThat(result).isNull()
            verify(userRepository).findByEmail(email)
        }
    }

    @Nested
    @DisplayName("Remember Me Token")
    inner class RememberMeTokenTests {
        @Test
        fun `should generate remember me token`() {
            val userId = UUID.randomUUID()
            val user =
                SecurityTestDataBuilders.user(
                    id = userId,
                    email = "test@example.com",
                )
            val expectedToken = "refresh-token-123"
            whenever(refreshTokenService.createRefreshToken(eq(userId))).thenReturn(expectedToken)

            val token = authenticationService.generateRefreshToken(user)

            assertThat(token).isNotBlank()
            assertThat(token).isEqualTo(expectedToken)
            verify(refreshTokenService).createRefreshToken(eq(userId))
        }

        @Test
        fun `should generate unique tokens`() {
            val token1 = authenticationService.generateRememberMeToken()
            val token2 = authenticationService.generateRememberMeToken()

            // Tokens should be unique due to random component (UUID or timestamp with nanos)
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
            val userId = UUID.randomUUID()
            val email = "test@example.com"
            val user =
                SecurityTestDataBuilders.user(
                    id = userId,
                    email = email,
                )
            val expectedToken = "access-token-123"
            whenever(
                jwtTokenIssuer.generateAccessToken(eq(userId), eq(email), anyOrNull<List<String>>()),
            ).thenReturn(expectedToken)

            val token = authenticationService.generateAccessToken(user)

            assertThat(token).isNotBlank()
            assertThat(token).isEqualTo(expectedToken)
            verify(jwtTokenIssuer).generateAccessToken(eq(userId), eq(email), anyOrNull<List<String>>())
        }

        @Test
        fun `should generate refresh token for user`() {
            val userId = UUID.randomUUID()
            val user =
                SecurityTestDataBuilders.user(
                    id = userId,
                    email = "test@example.com",
                )
            val expectedToken = "refresh-token-456"
            whenever(refreshTokenService.createRefreshToken(eq(userId))).thenReturn(expectedToken)

            val token = authenticationService.generateRefreshToken(user)

            assertThat(token).isNotBlank()
            assertThat(token).isEqualTo(expectedToken)
            verify(refreshTokenService).createRefreshToken(eq(userId))
        }

        @Test
        fun `should generate different tokens for same user`() {
            val userId = UUID.randomUUID()
            val email = "test@example.com"
            val user =
                SecurityTestDataBuilders.user(
                    id = userId,
                    email = email,
                )
            val accessTokenValue = "access-token-789"
            val refreshTokenValue = "refresh-token-789"
            whenever(
                jwtTokenIssuer.generateAccessToken(
                    eq(userId),
                    eq(email),
                    anyOrNull<List<String>>(),
                ),
            ).thenReturn(accessTokenValue)
            whenever(refreshTokenService.createRefreshToken(eq(userId))).thenReturn(refreshTokenValue)

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
            val token = "valid-access-token"
            whenever(jwtTokenValidator.getUserIdFromToken(token)).thenReturn(userId)
            whenever(jwtTokenValidator.isAccessToken(token)).thenReturn(true)
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
            whenever(jwtTokenValidator.getUserIdFromToken(invalidToken)).thenReturn(null)

            val result = authenticationService.validateAccessToken(invalidToken)

            assertThat(result).isNull()
            verify(userRepository, never()).findById(any())
        }

        @Test
        fun `should return null for refresh token used as access token`() {
            val refreshToken = "refresh-token-as-access"
            whenever(jwtTokenValidator.getUserIdFromToken(refreshToken)).thenReturn(UUID.randomUUID())
            whenever(jwtTokenValidator.isAccessToken(refreshToken)).thenReturn(false)

            val result = authenticationService.validateAccessToken(refreshToken)

            assertThat(result).isNull()
            verify(userRepository, never()).findById(any())
        }

        @Test
        fun `should return null when user not found for valid token`() {
            val userId = UUID.randomUUID()
            val token = "valid-access-token"
            whenever(jwtTokenValidator.getUserIdFromToken(token)).thenReturn(userId)
            whenever(jwtTokenValidator.isAccessToken(token)).thenReturn(true)
            whenever(userRepository.findById(userId)).thenReturn(Optional.empty())

            val result = authenticationService.validateAccessToken(token)

            assertThat(result).isNull()
            verify(userRepository).findById(userId)
        }

        @Test
        fun `should return null for disabled user when validating access token`() {
            val userId = UUID.randomUUID()
            val email = "test@example.com"
            val user =
                SecurityTestDataBuilders.user(
                    id = userId,
                    email = email,
                )
            val token = "valid-access-token"
            whenever(jwtTokenValidator.getUserIdFromToken(token)).thenReturn(userId)
            whenever(jwtTokenValidator.isAccessToken(token)).thenReturn(true)
            whenever(userRepository.findById(userId)).thenReturn(Optional.of(user))
            mockDisabledPrincipal(userId)

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
            val refreshToken = "valid-refresh-token"
            user.rememberMeToken = refreshToken
            whenever(jwtTokenValidator.getUserIdFromToken(refreshToken)).thenReturn(userId)
            whenever(jwtTokenValidator.isRefreshToken(refreshToken)).thenReturn(true)
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
            whenever(jwtTokenValidator.getUserIdFromToken(invalidToken)).thenReturn(null)

            val result = authenticationService.validateRefreshToken(invalidToken)

            assertThat(result).isNull()
            verify(userRepository, never()).findById(any())
        }

        @Test
        fun `should return null for access token used as refresh token`() {
            val accessToken = "access-token-as-refresh"
            whenever(jwtTokenValidator.getUserIdFromToken(accessToken)).thenReturn(UUID.randomUUID())
            whenever(jwtTokenValidator.isRefreshToken(accessToken)).thenReturn(false)

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
            val refreshToken = "revoked-refresh-token"
            whenever(jwtTokenValidator.getUserIdFromToken(refreshToken)).thenReturn(userId)
            whenever(jwtTokenValidator.isRefreshToken(refreshToken)).thenReturn(true)
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
            val refreshToken = "refresh-token-not-matching"
            whenever(jwtTokenValidator.getUserIdFromToken(refreshToken)).thenReturn(userId)
            whenever(jwtTokenValidator.isRefreshToken(refreshToken)).thenReturn(true)
            whenever(userRepository.findById(userId)).thenReturn(Optional.of(user))

            val result = authenticationService.validateRefreshToken(refreshToken)

            assertThat(result).isNull()
            verify(userRepository).findById(userId)
        }

        @Test
        fun `should return null when user not found for valid refresh token`() {
            val userId = UUID.randomUUID()
            val refreshToken = "valid-refresh-token"
            whenever(jwtTokenValidator.getUserIdFromToken(refreshToken)).thenReturn(userId)
            whenever(jwtTokenValidator.isRefreshToken(refreshToken)).thenReturn(true)
            whenever(userRepository.findById(userId)).thenReturn(Optional.empty())

            val result = authenticationService.validateRefreshToken(refreshToken)

            assertThat(result).isNull()
            verify(userRepository).findById(userId)
        }

        @Test
        fun `should return null for disabled user when validating refresh token`() {
            val userId = UUID.randomUUID()
            val email = "test@example.com"
            val user =
                SecurityTestDataBuilders.user(
                    id = userId,
                    email = email,
                )
            val refreshToken = "valid-refresh-token"
            user.rememberMeToken = refreshToken
            whenever(jwtTokenValidator.getUserIdFromToken(refreshToken)).thenReturn(userId)
            whenever(jwtTokenValidator.isRefreshToken(refreshToken)).thenReturn(true)
            whenever(userRepository.findById(userId)).thenReturn(Optional.of(user))
            mockDisabledPrincipal(userId)

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
            val userId = UUID.randomUUID()

            whenever(userRepository.findByEmail(email)).thenReturn(null)
            whenever(userRepository.save(any())).thenAnswer { invocation ->
                val savedUser = invocation.arguments[0] as UserEntity
                UserEntity(
                    id = userId,
                    email = savedUser.email,
                    displayName = savedUser.displayName,
                    passwordHash = savedUser.passwordHash,
                    createdAt = savedUser.createdAt,
                    updatedAt = savedUser.updatedAt,
                )
            }

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
            val userId = UUID.randomUUID()

            whenever(userRepository.findByEmail(email)).thenReturn(null)
            whenever(userRepository.save(any())).thenAnswer { invocation ->
                val savedUser = invocation.arguments[0] as UserEntity
                UserEntity(
                    id = userId,
                    email = savedUser.email,
                    displayName = savedUser.displayName,
                    passwordHash = savedUser.passwordHash,
                    createdAt = savedUser.createdAt,
                    updatedAt = savedUser.updatedAt,
                )
            }

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
            val userId = UUID.randomUUID()
            val claims =
                OAuthUserClaims(
                    email = email,
                    name = name,
                    picture = null,
                    emailVerified = true,
                )

            whenever(oauthProvider.validateAndExtractClaims(idToken)).thenReturn(claims)
            whenever(userRepository.findByEmail(email)).thenReturn(null)
            whenever(userRepository.save(any())).thenAnswer { invocation ->
                val savedUser = invocation.arguments[0] as UserEntity
                UserEntity(
                    id = userId,
                    email = savedUser.email,
                    displayName = savedUser.displayName,
                    passwordHash = savedUser.passwordHash,
                    createdAt = savedUser.createdAt,
                    updatedAt = savedUser.updatedAt,
                )
            }

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
            val userId = UUID.randomUUID()
            val claims =
                OAuthUserClaims(
                    email = email,
                    name = name,
                    picture = null,
                    emailVerified = true,
                )
            val existingUser = SecurityTestDataBuilders.user(id = userId, email = email, displayName = "Old Name")

            whenever(oauthProvider.validateAndExtractClaims(idToken)).thenReturn(claims)
            whenever(userRepository.findByEmail(email)).thenReturn(existingUser)
            mockEnabledPrincipal(userId)
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
            val userId = UUID.randomUUID()
            val claims =
                OAuthUserClaims(
                    email = email,
                    name = name,
                    picture = null,
                    emailVerified = true,
                )
            val existingUser = SecurityTestDataBuilders.user(id = userId, email = email, displayName = null)

            whenever(oauthProvider.validateAndExtractClaims(idToken)).thenReturn(claims)
            whenever(userRepository.findByEmail(email)).thenReturn(existingUser)
            mockEnabledPrincipal(userId)
            whenever(userRepository.save(any())).thenAnswer { it.arguments[0] as UserEntity }

            val result = authenticationService.authenticateWithOAuth(provider, idToken)

            assertThat(result).isNotNull()
            assertThat(result?.displayName).isEqualTo(name)

            verify(userRepository).save(any())
        }

        @Test
        fun `should return null for disabled OAuth user`() {
            val provider = "google"
            val idToken = "valid-google-id-token"
            val email = "existing@example.com"
            val name = "Existing User"
            val userId = UUID.randomUUID()
            val claims =
                OAuthUserClaims(
                    email = email,
                    name = name,
                    picture = null,
                    emailVerified = true,
                )
            val existingUser = SecurityTestDataBuilders.user(id = userId, email = email, displayName = "Old Name")

            whenever(oauthProvider.validateAndExtractClaims(idToken)).thenReturn(claims)
            whenever(userRepository.findByEmail(email)).thenReturn(existingUser)
            mockDisabledPrincipal(userId)

            val result = authenticationService.authenticateWithOAuth(provider, idToken)

            assertThat(result).isNull()
            verify(oauthProvider).validateAndExtractClaims(idToken)
            verify(userRepository).findByEmail(email)
            verify(userRepository, never()).save(any())
        }
    }
}
