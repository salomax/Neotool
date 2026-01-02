package io.github.salomax.neotool.security.test.service.integration

import io.github.salomax.neotool.common.security.exception.AuthenticationRequiredException
import io.github.salomax.neotool.common.security.principal.PrincipalType
import io.github.salomax.neotool.common.test.integration.BaseIntegrationTest
import io.github.salomax.neotool.common.test.integration.PostgresIntegrationTest
import io.github.salomax.neotool.common.test.transaction.runTransaction
import io.github.salomax.neotool.security.model.PrincipalEntity
import io.github.salomax.neotool.security.model.UserEntity
import io.github.salomax.neotool.security.repo.PrincipalRepository
import io.github.salomax.neotool.security.repo.RefreshTokenRepository
import io.github.salomax.neotool.security.repo.UserRepository
import io.github.salomax.neotool.security.service.authentication.AuthenticationService
import io.github.salomax.neotool.security.service.jwt.RefreshTokenService
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
import java.security.MessageDigest

@MicronautTest(startApplication = true)
@DisplayName("RefreshTokenService Integration Tests")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Tag("integration")
@Tag("refresh-token")
@Tag("security")
@TestMethodOrder(MethodOrderer.Random::class)
class RefreshTokenServiceIntegrationTest :
    BaseIntegrationTest(),
    PostgresIntegrationTest {
    @Inject
    lateinit var refreshTokenRepository: RefreshTokenRepository

    @Inject
    lateinit var userRepository: UserRepository

    @Inject
    lateinit var principalRepository: PrincipalRepository

    @Inject
    lateinit var authenticationService: AuthenticationService

    @Inject
    lateinit var refreshTokenService: RefreshTokenService

    @Inject
    lateinit var entityManager: EntityManager

    private fun uniqueEmail() = SecurityTestDataBuilders.uniqueEmail("refresh-token")

    @BeforeEach
    fun cleanupTestDataBefore() {
        // Clean up before each test to ensure clean state
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

    @AfterEach
    fun cleanupTestData() {
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

    private fun createTestUser(): UserEntity {
        val user =
            SecurityTestDataBuilders.userWithPassword(
                authenticationService = authenticationService,
                email = uniqueEmail(),
                password = "TestPassword123!",
            )
        val savedUser = userRepository.save(user)
        val userId = savedUser.id!!

        // Create enabled principal
        val principal =
            PrincipalEntity(
                principalType = PrincipalType.USER,
                externalId = userId.toString(),
                enabled = true,
            )
        principalRepository.save(principal)

        return savedUser
    }

    private fun hashToken(token: String): String =
        MessageDigest
            .getInstance("SHA-256")
            .digest(token.toByteArray())
            .joinToString("") { "%02x".format(it) }

    @Nested
    @DisplayName("Create Refresh Token")
    inner class CreateRefreshTokenTests {
        @Test
        fun `should create refresh token and store in database`() {
            // Arrange
            val user = createTestUser()

            // Act
            val refreshToken = refreshTokenService.createRefreshToken(user.id!!)

            // Assert
            assertThat(refreshToken).isNotBlank()
            assertThat(refreshToken.split(".")).hasSize(3) // JWT format

            val tokenHash = hashToken(refreshToken)
            val savedToken = refreshTokenRepository.findByTokenHash(tokenHash)
            assertThat(savedToken).isNotNull()
            assertThat(savedToken?.userId).isEqualTo(user.id)
            assertThat(savedToken?.familyId).isNotNull()
        }

        @Test
        fun `should create unique token families for different tokens`() {
            // Arrange
            val user = createTestUser()

            // Act
            val token1 = refreshTokenService.createRefreshToken(user.id!!)
            val token2 = refreshTokenService.createRefreshToken(user.id!!)

            // Assert
            assertThat(token1).isNotEqualTo(token2)

            val hash1 = hashToken(token1)
            val hash2 = hashToken(token2)
            val savedToken1 = refreshTokenRepository.findByTokenHash(hash1)
            val savedToken2 = refreshTokenRepository.findByTokenHash(hash2)

            assertThat(savedToken1?.familyId).isNotEqualTo(savedToken2?.familyId)
        }
    }

    @Nested
    @DisplayName("Refresh Access Token with Rotation")
    inner class RefreshAccessTokenTests {
        @Test
        fun `should refresh access token and rotate refresh token`() {
            // Arrange
            val user = createTestUser()
            val originalRefreshToken = refreshTokenService.createRefreshToken(user.id!!)
            val originalHash = hashToken(originalRefreshToken)
            val originalTokenRecord = refreshTokenRepository.findByTokenHash(originalHash)!!

            // Act
            val tokenPair = refreshTokenService.refreshAccessToken(originalRefreshToken)

            // Assert
            assertThat(tokenPair.accessToken).isNotBlank()
            assertThat(tokenPair.refreshToken).isNotBlank()
            assertThat(tokenPair.refreshToken).isNotEqualTo(originalRefreshToken)

            // Verify old token is marked as replaced
            val updatedOldToken = refreshTokenRepository.findByTokenHash(originalHash)
            assertThat(updatedOldToken?.replacedBy).isNotNull()

            // Verify new token is in same family
            val newHash = hashToken(tokenPair.refreshToken)
            val newTokenRecord = refreshTokenRepository.findByTokenHash(newHash)
            assertThat(newTokenRecord?.familyId).isEqualTo(originalTokenRecord.familyId)
        }

        @Test
        fun `should throw exception when token already used`() {
            // Arrange
            val user = createTestUser()
            val refreshToken = refreshTokenService.createRefreshToken(user.id!!)

            // Use token once
            refreshTokenService.refreshAccessToken(refreshToken)

            // Act & Assert - Try to use same token again
            assertThrows<AuthenticationRequiredException> {
                refreshTokenService.refreshAccessToken(refreshToken)
            }
        }

        @Test
        fun `should revoke entire family when token reuse detected`() {
            // Arrange
            val user = createTestUser()
            val refreshToken = refreshTokenService.createRefreshToken(user.id!!)
            val originalHash = hashToken(refreshToken)
            val originalTokenRecord = refreshTokenRepository.findByTokenHash(originalHash)!!
            val familyId = originalTokenRecord.familyId

            // Use token once (creates new token in family)
            val firstPair = refreshTokenService.refreshAccessToken(refreshToken)
            val newTokenHash = hashToken(firstPair.refreshToken)

            // Try to use old token again (should trigger family revocation)
            assertThrows<AuthenticationRequiredException> {
                refreshTokenService.refreshAccessToken(refreshToken)
            }

            // Assert - All tokens in family should be revoked
            val familyTokens = refreshTokenRepository.findByFamilyId(familyId)
            assertThat(familyTokens).isNotEmpty()
            familyTokens.forEach { token ->
                assertThat(token.revokedAt).isNotNull()
            }
        }

        @Test
        fun `should throw exception for invalid token`() {
            // Act & Assert
            assertThrows<AuthenticationRequiredException> {
                refreshTokenService.refreshAccessToken("invalid.token.here")
            }
        }

        @Test
        fun `should throw exception when user disabled`() {
            // Arrange
            val user = createTestUser()
            val refreshToken = refreshTokenService.createRefreshToken(user.id!!)

            // Disable user
            val principal =
                principalRepository
                    .findByPrincipalTypeAndExternalId(
                        PrincipalType.USER,
                        user.id!!.toString(),
                    ).orElseThrow()
            principal.enabled = false
            principalRepository.save(principal)

            // Act & Assert
            assertThrows<AuthenticationRequiredException> {
                refreshTokenService.refreshAccessToken(refreshToken)
            }
        }
    }

    @Nested
    @DisplayName("Revoke Tokens")
    inner class RevokeTokenTests {
        @Test
        fun `should revoke refresh token by hash`() {
            // Arrange
            val user = createTestUser()
            val refreshToken = refreshTokenService.createRefreshToken(user.id!!)
            val tokenHash = hashToken(refreshToken)

            // Act
            refreshTokenService.revokeRefreshToken(tokenHash)

            // Assert
            val tokenRecord = refreshTokenRepository.findByTokenHash(tokenHash)
            assertThat(tokenRecord?.revokedAt).isNotNull()
        }

        @Test
        fun `should revoke all tokens for user`() {
            // Arrange
            val user = createTestUser()
            val token1 = refreshTokenService.createRefreshToken(user.id!!)
            val token2 = refreshTokenService.createRefreshToken(user.id!!)

            // Act
            refreshTokenService.revokeAllTokensForUser(user.id!!)

            // Assert
            val tokens = refreshTokenRepository.findByUserIdAndRevokedAtIsNull(user.id!!)
            assertThat(tokens).isEmpty()

            val hash1 = hashToken(token1)
            val hash2 = hashToken(token2)
            assertThat(refreshTokenRepository.findByTokenHash(hash1)?.revokedAt).isNotNull()
            assertThat(refreshTokenRepository.findByTokenHash(hash2)?.revokedAt).isNotNull()
        }

        @Test
        fun `should revoke entire token family`() {
            // Arrange
            val user = createTestUser()
            val refreshToken = refreshTokenService.createRefreshToken(user.id!!)
            val originalHash = hashToken(refreshToken)
            val originalTokenRecord = refreshTokenRepository.findByTokenHash(originalHash)!!
            val familyId = originalTokenRecord.familyId

            // Use token to create another in the family
            val tokenPair = refreshTokenService.refreshAccessToken(refreshToken)
            val newHash = hashToken(tokenPair.refreshToken)

            // Act
            refreshTokenService.revokeFamily(familyId)

            // Assert
            val familyTokens = refreshTokenRepository.findByFamilyId(familyId)
            assertThat(familyTokens).isNotEmpty()
            familyTokens.forEach { token ->
                assertThat(token.revokedAt).isNotNull()
            }
        }
    }

    @Nested
    @DisplayName("Token Rotation Chain")
    inner class TokenRotationChainTests {
        @Test
        fun `should maintain same family through multiple rotations`() {
            // Arrange
            val user = createTestUser()
            val originalToken = refreshTokenService.createRefreshToken(user.id!!)
            val originalHash = hashToken(originalToken)
            val originalTokenRecord = refreshTokenRepository.findByTokenHash(originalHash)!!
            val familyId = originalTokenRecord.familyId

            // Act - Rotate multiple times
            val pair1 = refreshTokenService.refreshAccessToken(originalToken)
            val pair2 = refreshTokenService.refreshAccessToken(pair1.refreshToken)
            val pair3 = refreshTokenService.refreshAccessToken(pair2.refreshToken)

            // Assert - All tokens should be in same family
            val hash1 = hashToken(pair1.refreshToken)
            val hash2 = hashToken(pair2.refreshToken)
            val hash3 = hashToken(pair3.refreshToken)

            assertThat(refreshTokenRepository.findByTokenHash(hash1)?.familyId).isEqualTo(familyId)
            assertThat(refreshTokenRepository.findByTokenHash(hash2)?.familyId).isEqualTo(familyId)
            assertThat(refreshTokenRepository.findByTokenHash(hash3)?.familyId).isEqualTo(familyId)
        }

        @Test
        fun `should allow only one use per token`() {
            // Arrange
            val user = createTestUser()
            val token1 = refreshTokenService.createRefreshToken(user.id!!)

            // Act - Use token
            val pair1 = refreshTokenService.refreshAccessToken(token1)

            // Assert - Original token cannot be used again
            assertThrows<AuthenticationRequiredException> {
                refreshTokenService.refreshAccessToken(token1)
            }

            // But new token can be used
            val pair2 = refreshTokenService.refreshAccessToken(pair1.refreshToken)
            assertThat(pair2.accessToken).isNotBlank()
        }
    }
}
