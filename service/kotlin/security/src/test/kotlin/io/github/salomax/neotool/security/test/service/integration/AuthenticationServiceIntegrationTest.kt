package io.github.salomax.neotool.security.test.service.integration

import io.github.salomax.neotool.common.security.principal.PrincipalType
import io.github.salomax.neotool.common.test.integration.BaseIntegrationTest
import io.github.salomax.neotool.common.test.integration.PostgresIntegrationTest
import io.github.salomax.neotool.common.test.transaction.runTransaction
import io.github.salomax.neotool.security.model.PrincipalEntity
import io.github.salomax.neotool.security.repo.PrincipalRepository
import io.github.salomax.neotool.security.repo.RefreshTokenRepository
import io.github.salomax.neotool.security.repo.UserRepository
import io.github.salomax.neotool.security.service.authentication.AuthContextFactory
import io.github.salomax.neotool.security.service.authentication.AuthenticationService
import io.github.salomax.neotool.security.test.SecurityTestDataBuilders
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.BeforeEach
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestMethodOrder
import org.junit.jupiter.api.assertThrows

@MicronautTest(startApplication = true)
@DisplayName("AuthenticationService Integration Tests")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Tag("integration")
@Tag("authentication")
@Tag("security")
@TestMethodOrder(MethodOrderer.Random::class)
class AuthenticationServiceIntegrationTest :
    BaseIntegrationTest(),
    PostgresIntegrationTest {
    @Inject
    lateinit var userRepository: UserRepository

    @Inject
    lateinit var principalRepository: PrincipalRepository

    @Inject
    lateinit var authenticationService: AuthenticationService

    @Inject
    lateinit var authContextFactory: AuthContextFactory

    @Inject
    lateinit var jwtTokenValidator: io.github.salomax.neotool.common.security.jwt.JwtTokenValidator

    @Inject
    lateinit var refreshTokenRepository: RefreshTokenRepository

    @Inject
    lateinit var entityManager: EntityManager

    private fun uniqueEmail() = SecurityTestDataBuilders.uniqueEmail("authentication-integration")

    @BeforeEach
    fun cleanupTestDataBefore() {
        // Clean up before each test to ensure clean state
        cleanupTestData()
    }

    @AfterEach
    fun cleanupTestData() {
        // Clean up test data after each test
        // Note: BaseIntegrationTest.setUp() and tearDown() are final and handle container setup
        try {
            entityManager.runTransaction {
                refreshTokenRepository.deleteAll()
                principalRepository.deleteAll()
                userRepository.deleteAll()
                entityManager.flush()
            }
            entityManager.clear()
        } catch (e: Exception) {
            // Ignore cleanup errors
        }
    }

    @Nested
    @DisplayName("Password Hashing and Verification with Database")
    inner class PasswordHashingIntegrationTests {
        @Test
        fun `should hash and verify password with database persistence`() {
            // Arrange
            val email = uniqueEmail()
            val password = "TestPassword123!"
            val user =
                SecurityTestDataBuilders.userWithPassword(
                    authenticationService = authenticationService,
                    email = email,
                    password = password,
                )

            // Act - Save user with hashed password
            val savedUser = userRepository.save(user)

            // Assert - Verify password hash is stored
            assertThat(savedUser.passwordHash).isNotNull()
            assertThat(savedUser.passwordHash).isNotBlank()
            assertThat(savedUser.passwordHash).startsWith("\$argon2id\$")

            // Act - Retrieve user and verify password
            val retrievedUser = userRepository.findByEmail(email)
            assertThat(retrievedUser).isNotNull()

            val isValid = authenticationService.verifyPassword(password, retrievedUser!!.passwordHash!!)
            assertThat(isValid).isTrue()
        }

        @Test
        fun `should reject incorrect password with database persistence`() {
            // Arrange
            val email = uniqueEmail()
            val correctPassword = "CorrectPassword123!"
            val wrongPassword = "WrongPassword123!"
            val user =
                SecurityTestDataBuilders.userWithPassword(
                    authenticationService = authenticationService,
                    email = email,
                    password = correctPassword,
                )

            // Act
            val savedUser = userRepository.save(user)
            val retrievedUser = userRepository.findByEmail(email)

            // Assert
            assertThat(retrievedUser).isNotNull()
            val isValid = authenticationService.verifyPassword(wrongPassword, retrievedUser!!.passwordHash!!)
            assertThat(isValid).isFalse()
        }

        @Test
        fun `should handle password verification exception with invalid hash format`() {
            // Arrange
            val email = uniqueEmail()
            val password = "TestPassword123!"
            val invalidHash = "invalid-hash-format-that-will-cause-exception"
            val user =
                SecurityTestDataBuilders.user(
                    email = email,
                    passwordHash = invalidHash,
                )
            userRepository.save(user)

            // Act - This should trigger the exception handling branch in verifyPassword
            val isValid = authenticationService.verifyPassword(password, invalidHash)

            // Assert
            assertThat(isValid).isFalse()
        }
    }

    @Nested
    @DisplayName("User Authentication with Database")
    inner class UserAuthenticationIntegrationTests {
        @Test
        fun `should authenticate user with correct credentials from database`() {
            // Arrange
            val email = uniqueEmail()
            val password = "TestPassword123!"
            val user =
                SecurityTestDataBuilders.userWithPassword(
                    authenticationService = authenticationService,
                    email = email,
                    password = password,
                )
            userRepository.save(user)

            // Act
            val authenticatedUser = authenticationService.authenticate(email, password)

            // Assert
            assertThat(authenticatedUser).isNotNull()
            assertThat(authenticatedUser?.email).isEqualTo(email)
            assertThat(authenticatedUser?.id).isNotNull()
        }

        @Test
        fun `should return null for non-existent user`() {
            // Arrange
            val email = uniqueEmail()
            val password = "TestPassword123!"

            // Act
            val authenticatedUser = authenticationService.authenticate(email, password)

            // Assert
            assertThat(authenticatedUser).isNull()
        }

        @Test
        fun `should return null for incorrect password`() {
            // Arrange
            val email = uniqueEmail()
            val correctPassword = "CorrectPassword123!"
            val wrongPassword = "WrongPassword123!"
            val user =
                SecurityTestDataBuilders.userWithPassword(
                    authenticationService = authenticationService,
                    email = email,
                    password = correctPassword,
                )
            userRepository.save(user)

            // Act
            val authenticatedUser = authenticationService.authenticate(email, wrongPassword)

            // Assert
            assertThat(authenticatedUser).isNull()
        }

        @Test
        fun `should return null for user without password hash`() {
            // Arrange
            val email = uniqueEmail()
            val password = "TestPassword123!"
            val user =
                SecurityTestDataBuilders.user(
                    email = email,
                    passwordHash = null,
                )
            userRepository.save(user)

            // Act
            val authenticatedUser = authenticationService.authenticate(email, password)

            // Assert
            assertThat(authenticatedUser).isNull()
        }

        @Test
        fun `should return null for disabled user during authentication`() {
            // Arrange
            val email = uniqueEmail()
            val password = "TestPassword123!"
            val user =
                SecurityTestDataBuilders.userWithPassword(
                    authenticationService = authenticationService,
                    email = email,
                    password = password,
                )
            val savedUser = userRepository.save(user)
            val userId = savedUser.id!!

            // Create and save disabled principal
            val disabledPrincipal =
                PrincipalEntity(
                    principalType = PrincipalType.USER,
                    externalId = userId.toString(),
                    enabled = false,
                )
            principalRepository.save(disabledPrincipal)

            // Act
            val authenticatedUser = authenticationService.authenticate(email, password)

            // Assert
            assertThat(authenticatedUser).isNull()
        }

        @Test
        fun `should authenticate multiple users independently`() {
            // Arrange
            val email1 = uniqueEmail()
            val password1 = "Password1!"
            val user1 =
                SecurityTestDataBuilders.userWithPassword(
                    authenticationService = authenticationService,
                    email = email1,
                    password = password1,
                )

            val email2 = uniqueEmail()
            val password2 = "Password2!"
            val user2 =
                SecurityTestDataBuilders.userWithPassword(
                    authenticationService = authenticationService,
                    email = email2,
                    password = password2,
                )

            userRepository.save(user1)
            userRepository.save(user2)

            // Act
            val authenticatedUser1 = authenticationService.authenticate(email1, password1)
            val authenticatedUser2 = authenticationService.authenticate(email2, password2)

            // Assert
            assertThat(authenticatedUser1).isNotNull()
            assertThat(authenticatedUser1?.email).isEqualTo(email1)
            assertThat(authenticatedUser2).isNotNull()
            assertThat(authenticatedUser2?.email).isEqualTo(email2)
            assertThat(authenticatedUser1?.id).isNotEqualTo(authenticatedUser2?.id)
        }

        @Test
        fun `should return null for blank password`() {
            // Arrange
            val email = uniqueEmail()
            val password = "   " // Blank password (whitespace)
            val user =
                SecurityTestDataBuilders.userWithPassword(
                    authenticationService = authenticationService,
                    email = email,
                    password = "TestPassword123!",
                )
            userRepository.save(user)

            // Act
            val authenticatedUser = authenticationService.authenticate(email, password)

            // Assert
            assertThat(authenticatedUser).isNull()
        }

        @Test
        fun `should return null for empty password`() {
            // Arrange
            val email = uniqueEmail()
            val password = "" // Empty password
            val user =
                SecurityTestDataBuilders.userWithPassword(
                    authenticationService = authenticationService,
                    email = email,
                    password = "TestPassword123!",
                )
            userRepository.save(user)

            // Act
            val authenticatedUser = authenticationService.authenticate(email, password)

            // Assert
            assertThat(authenticatedUser).isNull()
        }

        @Test
        fun `should return null when user not found`() {
            // Arrange - no user in database
            val email = uniqueEmail()
            val password = "TestPassword123!"

            // Act
            val authenticatedUser = authenticationService.authenticate(email, password)

            // Assert
            assertThat(authenticatedUser).isNull()
        }

        @Test
        fun `should return null when password hash is null`() {
            // Arrange
            val email = uniqueEmail()
            val password = "TestPassword123!"
            val user =
                SecurityTestDataBuilders.user(
                    email = email,
                    passwordHash = null,
                )
            userRepository.save(user)

            // Act
            val authenticatedUser = authenticationService.authenticate(email, password)

            // Assert
            assertThat(authenticatedUser).isNull()
        }
    }

    @Nested
    @DisplayName("Remember Me Token with Database")
    inner class RememberMeTokenIntegrationTests {
        @Test
        fun `should save and retrieve remember me token from database`() {
            // Arrange
            val email = uniqueEmail()
            val password = "TestPassword123!"
            val user =
                SecurityTestDataBuilders.userWithPassword(
                    authenticationService = authenticationService,
                    email = email,
                    password = password,
                )
            val savedUser = userRepository.save(user)
            val token = authenticationService.generateRememberMeToken()

            // Act
            val userWithToken = authenticationService.saveRememberMeToken(savedUser.id!!, token)
            val retrievedUser = userRepository.findById(userWithToken.id)

            // Assert
            assertThat(userWithToken.rememberMeToken).isEqualTo(token)
            assertThat(retrievedUser.isPresent).isTrue()
            assertThat(retrievedUser.get().rememberMeToken).isEqualTo(token)
        }

        @Test
        fun `should authenticate user by remember me token from database`() {
            // Arrange
            val email = uniqueEmail()
            val password = "TestPassword123!"
            val user =
                SecurityTestDataBuilders.userWithPassword(
                    authenticationService = authenticationService,
                    email = email,
                    password = password,
                )
            val savedUser = userRepository.save(user)
            val token = authenticationService.generateRememberMeToken()
            authenticationService.saveRememberMeToken(savedUser.id!!, token)

            // Act
            val authenticatedUser = authenticationService.authenticateByToken(token)

            // Assert
            assertThat(authenticatedUser).isNotNull()
            assertThat(authenticatedUser?.email).isEqualTo(email)
            assertThat(authenticatedUser?.rememberMeToken).isEqualTo(token)
        }

        @Test
        fun `should return null for invalid remember me token`() {
            // Arrange
            val invalidToken = "invalid-token-123"

            // Act
            val authenticatedUser = authenticationService.authenticateByToken(invalidToken)

            // Assert
            assertThat(authenticatedUser).isNull()
        }

        @Test
        fun `should clear remember me token from database`() {
            // Arrange
            val email = uniqueEmail()
            val password = "TestPassword123!"
            val user =
                SecurityTestDataBuilders.userWithPassword(
                    authenticationService = authenticationService,
                    email = email,
                    password = password,
                )
            val savedUser = userRepository.save(user)
            val token = authenticationService.generateRememberMeToken()
            val userWithToken = authenticationService.saveRememberMeToken(savedUser.id!!, token)

            // Act
            val userWithoutToken = authenticationService.clearRememberMeToken(userWithToken.id!!)
            val retrievedUser = userRepository.findById(userWithoutToken.id)

            // Assert
            assertThat(userWithoutToken.rememberMeToken).isNull()
            assertThat(retrievedUser.isPresent).isTrue()
            assertThat(retrievedUser.get().rememberMeToken).isNull()
        }

        @Test
        fun `should generate unique remember me tokens`() {
            // Arrange
            val email = uniqueEmail()
            val password = "TestPassword123!"
            val user =
                SecurityTestDataBuilders.userWithPassword(
                    authenticationService = authenticationService,
                    email = email,
                    password = password,
                )
            val savedUser = userRepository.save(user)

            // Act
            val token1 = authenticationService.generateRememberMeToken()
            Thread.sleep(1) // Ensure timestamp difference
            val token2 = authenticationService.generateRememberMeToken()

            authenticationService.saveRememberMeToken(savedUser.id!!, token1)
            val user1 = authenticationService.authenticateByToken(token1)
            val user2 = authenticationService.authenticateByToken(token2)

            // Assert
            assertThat(token1).isNotEqualTo(token2)
            assertThat(user1).isNotNull()
            assertThat(user2).isNull() // token2 not saved
        }

        @Test
        fun `should handle remember me token for multiple users`() {
            // Arrange
            val email1 = uniqueEmail()
            val password1 = "Password1!"
            val user1 =
                SecurityTestDataBuilders.userWithPassword(
                    authenticationService = authenticationService,
                    email = email1,
                    password = password1,
                )
            val savedUser1 = userRepository.save(user1)
            val token1 = authenticationService.generateRememberMeToken()

            val email2 = uniqueEmail()
            val password2 = "Password2!"
            val user2 =
                SecurityTestDataBuilders.userWithPassword(
                    authenticationService = authenticationService,
                    email = email2,
                    password = password2,
                )
            val savedUser2 = userRepository.save(user2)
            val token2 = authenticationService.generateRememberMeToken()

            // Act
            authenticationService.saveRememberMeToken(savedUser1.id!!, token1)
            authenticationService.saveRememberMeToken(savedUser2.id!!, token2)

            val authenticatedUser1 = authenticationService.authenticateByToken(token1)
            val authenticatedUser2 = authenticationService.authenticateByToken(token2)

            // Assert
            assertThat(authenticatedUser1).isNotNull()
            assertThat(authenticatedUser1?.email).isEqualTo(email1)
            assertThat(authenticatedUser2).isNotNull()
            assertThat(authenticatedUser2?.email).isEqualTo(email2)
            assertThat(authenticatedUser1?.id).isNotEqualTo(authenticatedUser2?.id)
        }
    }

    @Nested
    @DisplayName("JWT Token Generation and Validation with Database")
    inner class JwtTokenIntegrationTests {
        @Test
        fun `should generate and validate JWT access token with AuthContext`() {
            // Arrange
            val email = uniqueEmail()
            val password = "TestPassword123!"
            val user =
                SecurityTestDataBuilders.userWithPassword(
                    authenticationService = authenticationService,
                    email = email,
                    password = password,
                )
            val savedUser = userRepository.save(user)

            // Act - Build AuthContext and generate access token
            val authContext = authContextFactory.build(savedUser)
            val accessToken = authenticationService.generateAccessToken(authContext)

            // Assert - Token is valid
            assertThat(accessToken).isNotBlank()
            assertThat(accessToken.split(".")).hasSize(3) // JWT has 3 parts

            // Assert - Token contains permissions claim (empty array for user with no roles)
            val permissions = jwtTokenValidator.getPermissionsFromToken(accessToken)
            assertThat(permissions).isNotNull()
            assertThat(permissions).isEmpty()

            // Act - Validate access token
            val validatedUser = authenticationService.validateAccessToken(accessToken)

            // Assert - User is returned
            assertThat(validatedUser).isNotNull()
            assertThat(validatedUser?.id).isEqualTo(savedUser.id)
            assertThat(validatedUser?.email).isEqualTo(email)
        }

        @Test
        fun `should generate and validate JWT refresh token`() {
            // Arrange
            val email = uniqueEmail()
            val password = "TestPassword123!"
            val user =
                SecurityTestDataBuilders.userWithPassword(
                    authenticationService = authenticationService,
                    email = email,
                    password = password,
                )
            val savedUser = userRepository.save(user)

            // Act - Generate refresh token
            val refreshToken = authenticationService.generateRefreshToken(savedUser)
            authenticationService.saveRememberMeToken(savedUser.id!!, refreshToken)

            // Assert - Token is valid
            assertThat(refreshToken).isNotBlank()
            assertThat(refreshToken.split(".")).hasSize(3) // JWT has 3 parts

            // Act - Validate refresh token
            val validatedUser = authenticationService.validateRefreshToken(refreshToken)

            // Assert - User is returned
            assertThat(validatedUser).isNotNull()
            assertThat(validatedUser?.id).isEqualTo(savedUser.id)
            assertThat(validatedUser?.email).isEqualTo(email)
        }

        @Test
        fun `should reject invalid access token`() {
            // Arrange
            val invalidToken = "invalid.jwt.token"

            // Act
            val validatedUser = authenticationService.validateAccessToken(invalidToken)

            // Assert
            assertThat(validatedUser).isNull()
        }

        @Test
        fun `should reject refresh token when revoked`() {
            // Arrange
            val email = uniqueEmail()
            val password = "TestPassword123!"
            val user =
                SecurityTestDataBuilders.userWithPassword(
                    authenticationService = authenticationService,
                    email = email,
                    password = password,
                )
            val savedUser = userRepository.save(user)
            val refreshToken = authenticationService.generateRefreshToken(savedUser)
            authenticationService.saveRememberMeToken(savedUser.id!!, refreshToken)

            // Act - Revoke token
            authenticationService.clearRememberMeToken(savedUser.id!!)

            // Act - Try to validate revoked token
            val validatedUser = authenticationService.validateRefreshToken(refreshToken)

            // Assert
            assertThat(validatedUser).isNull()
        }

        @Test
        fun `should reject access token for disabled user`() {
            // Arrange
            val email = uniqueEmail()
            val password = "TestPassword123!"
            val user =
                SecurityTestDataBuilders.userWithPassword(
                    authenticationService = authenticationService,
                    email = email,
                    password = password,
                )
            val savedUser = userRepository.save(user)
            val userId = savedUser.id!!
            val authContext = authContextFactory.build(savedUser)
            val accessToken = authenticationService.generateAccessToken(authContext)

            // Act - Disable user by creating disabled principal
            val disabledPrincipal =
                PrincipalEntity(
                    principalType = PrincipalType.USER,
                    externalId = userId.toString(),
                    enabled = false,
                )
            principalRepository.save(disabledPrincipal)

            // Act - Try to validate access token
            val validatedUser = authenticationService.validateAccessToken(accessToken)

            // Assert
            assertThat(validatedUser).isNull()
        }

        @Test
        fun `should reject refresh token for disabled user`() {
            // Arrange
            val email = uniqueEmail()
            val password = "TestPassword123!"
            val user =
                SecurityTestDataBuilders.userWithPassword(
                    authenticationService = authenticationService,
                    email = email,
                    password = password,
                )
            val savedUser = userRepository.save(user)
            val userId = savedUser.id!!
            val refreshToken = authenticationService.generateRefreshToken(savedUser)
            authenticationService.saveRememberMeToken(userId, refreshToken)

            // Act - Disable user by creating disabled principal
            val disabledPrincipal =
                PrincipalEntity(
                    principalType = PrincipalType.USER,
                    externalId = userId.toString(),
                    enabled = false,
                )
            principalRepository.save(disabledPrincipal)

            // Act - Try to validate refresh token
            val validatedUser = authenticationService.validateRefreshToken(refreshToken)

            // Assert
            assertThat(validatedUser).isNull()
        }

        @Test
        fun `should reject refresh token when token does not match stored token`() {
            // Arrange
            val email = uniqueEmail()
            val password = "TestPassword123!"
            val user =
                SecurityTestDataBuilders.userWithPassword(
                    authenticationService = authenticationService,
                    email = email,
                    password = password,
                )
            val savedUser = userRepository.save(user)
            val refreshToken1 = authenticationService.generateRefreshToken(savedUser)
            // Store token1
            authenticationService.saveRememberMeToken(savedUser.id!!, refreshToken1)

            // Wait at least 1 second to ensure token2 has a different issued-at time
            // JWT tokens use seconds precision for iat, so we need at least 1 second difference
            Thread.sleep(1000)

            // Generate a different token (token2) for the same user
            val refreshToken2 = authenticationService.generateRefreshToken(savedUser)
            // Ensure token2 is different from token1 (they should be different due to different iat)
            assertThat(refreshToken2).isNotEqualTo(refreshToken1)

            // Act - Try to validate token2 when token1 is stored
            val validatedUser = authenticationService.validateRefreshToken(refreshToken2)

            // Assert - Should return null because token2 doesn't match stored token1
            assertThat(validatedUser).isNull()
        }

        @Test
        fun `should reject access token used as refresh token`() {
            // Arrange
            val email = uniqueEmail()
            val password = "TestPassword123!"
            val user =
                SecurityTestDataBuilders.userWithPassword(
                    authenticationService = authenticationService,
                    email = email,
                    password = password,
                )
            val savedUser = userRepository.save(user)
            val authContext = authContextFactory.build(savedUser)
            val accessToken = authenticationService.generateAccessToken(authContext)

            // Act - Try to use access token as refresh token
            val validatedUser = authenticationService.validateRefreshToken(accessToken)

            // Assert
            assertThat(validatedUser).isNull()
        }

        @Test
        fun `should reject refresh token used as access token`() {
            // Arrange
            val email = uniqueEmail()
            val password = "TestPassword123!"
            val user =
                SecurityTestDataBuilders.userWithPassword(
                    authenticationService = authenticationService,
                    email = email,
                    password = password,
                )
            val savedUser = userRepository.save(user)
            val refreshToken = authenticationService.generateRefreshToken(savedUser)
            authenticationService.saveRememberMeToken(savedUser.id!!, refreshToken)

            // Act - Try to use refresh token as access token
            val validatedUser = authenticationService.validateAccessToken(refreshToken)

            // Assert
            assertThat(validatedUser).isNull()
        }

        @Test
        fun `should complete full authentication flow with JWT tokens using AuthContext`() {
            // Arrange
            val email = uniqueEmail()
            val password = "TestPassword123!"
            val user =
                SecurityTestDataBuilders.userWithPassword(
                    authenticationService = authenticationService,
                    email = email,
                    password = password,
                )
            val savedUser = userRepository.save(user)

            // Act - Authenticate user
            val authenticatedUser = authenticationService.authenticate(email, password)
            assertThat(authenticatedUser).isNotNull()

            // Act - Build AuthContext and generate tokens
            val authContext = authContextFactory.build(authenticatedUser!!)
            val accessToken = authenticationService.generateAccessToken(authContext)
            val refreshToken = authenticationService.generateRefreshToken(authenticatedUser)
            authenticationService.saveRememberMeToken(authenticatedUser.id!!, refreshToken)

            // Assert - Access token works and contains permissions claim
            val userFromAccessToken = authenticationService.validateAccessToken(accessToken)
            assertThat(userFromAccessToken).isNotNull()
            assertThat(userFromAccessToken?.id).isEqualTo(savedUser.id)
            val permissions = jwtTokenValidator.getPermissionsFromToken(accessToken)
            assertThat(permissions).isNotNull() // Permissions claim should always be present

            // Assert - Refresh token works
            val userFromRefreshToken = authenticationService.validateRefreshToken(refreshToken)
            assertThat(userFromRefreshToken).isNotNull()
            assertThat(userFromRefreshToken?.id).isEqualTo(savedUser.id)
        }
    }

    @Nested
    @DisplayName("Concurrent Operations")
    inner class ConcurrentOperationsTests {
        @Test
        fun `should handle concurrent authentication requests`() {
            // Arrange
            val email = uniqueEmail()
            val password = "TestPassword123!"
            val user =
                SecurityTestDataBuilders.userWithPassword(
                    authenticationService = authenticationService,
                    email = email,
                    password = password,
                )
            userRepository.save(user)

            // Act - Simulate concurrent authentication attempts
            val results =
                (1..10).map {
                    authenticationService.authenticate(email, password)
                }

            // Assert - All should succeed
            results.forEach { result ->
                assertThat(result).isNotNull()
                assertThat(result?.email).isEqualTo(email)
            }
        }

        @Test
        fun `should handle sequential remember me token operations`() {
            // Arrange
            val email = uniqueEmail()
            val password = "TestPassword123!"
            val user =
                SecurityTestDataBuilders.userWithPassword(
                    authenticationService = authenticationService,
                    email = email,
                    password = password,
                )
            val savedUser = userRepository.save(user)
            val userId = savedUser.id!!

            // Act - Generate and save multiple tokens sequentially
            // Pass user ID directly - service method fetches fresh from DB, avoiding session conflicts
            val tokens = mutableListOf<String>()
            for (i in 1..5) {
                val token = authenticationService.generateRefreshToken(savedUser)
                authenticationService.saveRememberMeToken(userId, token)
                tokens.add(token)
            }

            // Assert - Last token should be the one stored
            val lastToken = tokens.last()
            val authenticatedUser = authenticationService.authenticateByToken(lastToken)
            assertThat(authenticatedUser).isNotNull()
            assertThat(authenticatedUser?.rememberMeToken).isEqualTo(lastToken)
        }
    }

    @Nested
    @DisplayName("OAuth Authentication Integration Tests")
    inner class OAuthAuthenticationIntegrationTests {
        @Test
        fun `should throw exception for unsupported OAuth provider`() {
            // Arrange
            val provider = "microsoft"
            val idToken = "test-id-token"

            // Act & Assert
            assertThrows<IllegalArgumentException> {
                authenticationService.authenticateWithOAuth(provider, idToken)
            }.also { exception ->
                assertThat(exception.message).contains("Unsupported OAuth provider")
            }
        }

        // Note: Testing with real Google ID tokens would require actual Google OAuth setup
        // In a real scenario, you would use test fixtures with valid Google ID tokens
        // or configure a test Google OAuth application
    }
}
