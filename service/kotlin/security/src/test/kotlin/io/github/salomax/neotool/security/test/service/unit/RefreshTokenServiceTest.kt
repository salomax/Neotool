package io.github.salomax.neotool.security.test.service.unit

import io.github.salomax.neotool.security.config.JwtConfig
import io.github.salomax.neotool.security.model.PrincipalEntity
import io.github.salomax.neotool.security.model.RefreshTokenEntity
import io.github.salomax.neotool.security.model.UserEntity
import io.github.salomax.neotool.security.repo.PrincipalRepository
import io.github.salomax.neotool.security.repo.RefreshTokenRepository
import io.github.salomax.neotool.security.repo.UserRepository
import io.github.salomax.neotool.security.service.AuthContext
import io.github.salomax.neotool.security.service.AuthContextFactory
import io.github.salomax.neotool.security.service.JwtService
import io.github.salomax.neotool.security.service.PrincipalType
import io.github.salomax.neotool.security.service.RefreshTokenService
import io.github.salomax.neotool.security.service.exception.AuthenticationRequiredException
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
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.security.MessageDigest
import java.time.Instant
import java.util.Optional
import java.util.UUID

@DisplayName("RefreshTokenService Unit Tests")
class RefreshTokenServiceTest {
    private lateinit var refreshTokenRepository: RefreshTokenRepository
    private lateinit var userRepository: UserRepository
    private lateinit var principalRepository: PrincipalRepository
    private lateinit var jwtService: JwtService
    private lateinit var authContextFactory: AuthContextFactory
    private lateinit var jwtConfig: JwtConfig
    private lateinit var refreshTokenService: RefreshTokenService

    @BeforeEach
    fun setUp() {
        refreshTokenRepository = mock()
        userRepository = mock()
        principalRepository = mock()
        jwtConfig = JwtConfig(
            secret = "test-secret-key-minimum-32-characters-long-for-hmac-sha256",
            accessTokenExpirationSeconds = 900L,
            refreshTokenExpirationSeconds = 604800L,
        )
        jwtService = JwtService(jwtConfig)
        authContextFactory = mock()
        refreshTokenService = RefreshTokenService(
            refreshTokenRepository,
            userRepository,
            principalRepository,
            jwtService,
            authContextFactory,
            jwtConfig,
        )
    }

    private fun mockEnabledPrincipal(userId: UUID) {
        val principal = PrincipalEntity(
            id = UUID.randomUUID(),
            principalType = PrincipalType.USER,
            externalId = userId.toString(),
            enabled = true,
        )
        whenever(principalRepository.findByPrincipalTypeAndExternalId(PrincipalType.USER, userId.toString()))
            .thenReturn(Optional.of(principal))
    }

    private fun hashToken(token: String): String {
        return MessageDigest.getInstance("SHA-256")
            .digest(token.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }

    @Nested
    @DisplayName("Create Refresh Token")
    inner class CreateRefreshTokenTests {
        @Test
        fun `should create refresh token for user`() {
            // Arrange
            val userId = UUID.randomUUID()
            val user = SecurityTestDataBuilders.user(id = userId, email = "test@example.com")
            whenever(userRepository.findById(userId)).thenReturn(Optional.of(user))
            whenever(refreshTokenRepository.save(any())).thenAnswer { it.arguments[0] as RefreshTokenEntity }

            // Act
            val refreshToken = refreshTokenService.createRefreshToken(userId)

            // Assert
            assertThat(refreshToken).isNotBlank()
            assertThat(refreshToken.split(".")).hasSize(3) // JWT has 3 parts
            verify(userRepository).findById(userId)
            verify(refreshTokenRepository).save(any())
        }

        @Test
        fun `should throw exception when user not found`() {
            // Arrange
            val userId = UUID.randomUUID()
            whenever(userRepository.findById(userId)).thenReturn(Optional.empty())

            // Act & Assert
            assertThrows<IllegalStateException> {
                refreshTokenService.createRefreshToken(userId)
            }
            verify(refreshTokenRepository, never()).save(any())
        }

        @Test
        fun `should create unique token families`() {
            // Arrange
            val userId = UUID.randomUUID()
            val user = SecurityTestDataBuilders.user(id = userId, email = "test@example.com")
            whenever(userRepository.findById(userId)).thenReturn(Optional.of(user))
            whenever(refreshTokenRepository.save(any())).thenAnswer { it.arguments[0] as RefreshTokenEntity }

            // Act
            val token1 = refreshTokenService.createRefreshToken(userId)
            val token2 = refreshTokenService.createRefreshToken(userId)

            // Assert
            assertThat(token1).isNotEqualTo(token2)
            val captor = ArgumentCaptor.forClass(RefreshTokenEntity::class.java)
            verify(refreshTokenRepository, times(2)).save(captor.capture())
            val savedTokens = captor.allValues
            assertThat(savedTokens[0].familyId).isNotEqualTo(savedTokens[1].familyId)
        }
    }

    @Nested
    @DisplayName("Refresh Access Token")
    inner class RefreshAccessTokenTests {
        @Test
        fun `should refresh access token and rotate refresh token`() {
            // Arrange
            val userId = UUID.randomUUID()
            val user = SecurityTestDataBuilders.user(id = userId, email = "test@example.com")
            val familyId = UUID.randomUUID()
            val oldRefreshToken = jwtService.generateRefreshToken(userId)
            val tokenHash = hashToken(oldRefreshToken)
            val now = Instant.now()
            val expiresAt = now.plusSeconds(604800L)

            val oldTokenRecord = RefreshTokenEntity(
                userId = userId,
                tokenHash = tokenHash,
                familyId = familyId,
                issuedAt = now.minusSeconds(3600),
                expiresAt = expiresAt,
            )

            val authContext: AuthContext = mock()
            whenever(refreshTokenRepository.findByTokenHash(tokenHash)).thenReturn(oldTokenRecord)
            whenever(userRepository.findById(userId)).thenReturn(Optional.of(user))
            mockEnabledPrincipal(userId)
            whenever(authContextFactory.build(user)).thenReturn(authContext)
            whenever(jwtService.generateAccessToken(any<AuthContext>())).thenReturn("new-access-token")
            whenever(refreshTokenRepository.save(any())).thenAnswer { it.arguments[0] as RefreshTokenEntity }

            // Act
            val result = refreshTokenService.refreshAccessToken(oldRefreshToken)

            // Assert
            assertThat(result.accessToken).isEqualTo("new-access-token")
            assertThat(result.refreshToken).isNotBlank()
            assertThat(result.refreshToken).isNotEqualTo(oldRefreshToken)

            val captor = ArgumentCaptor.forClass(RefreshTokenEntity::class.java)
            verify(refreshTokenRepository, times(2)).save(captor.capture())
            val savedTokens = captor.allValues
            assertThat(savedTokens[0].familyId).isEqualTo(familyId) // New token same family
            assertThat(savedTokens[1].replacedBy).isNotNull() // Old token marked as replaced
        }

        @Test
        fun `should throw exception for invalid token hash`() {
            // Arrange
            val invalidToken = "invalid.token.here"
            val tokenHash = hashToken(invalidToken)
            whenever(refreshTokenRepository.findByTokenHash(tokenHash)).thenReturn(null)

            // Act & Assert
            assertThrows<AuthenticationRequiredException> {
                refreshTokenService.refreshAccessToken(invalidToken)
            }
        }

        @Test
        fun `should throw exception when token already replaced`() {
            // Arrange
            val userId = UUID.randomUUID()
            val oldRefreshToken = jwtService.generateRefreshToken(userId)
            val tokenHash = hashToken(oldRefreshToken)
            val familyId = UUID.randomUUID()
            val now = Instant.now()
            val replacedBy = RefreshTokenEntity(
                userId = userId,
                tokenHash = "replacement-hash",
                familyId = familyId,
                issuedAt = now,
                expiresAt = now.plusSeconds(604800L),
            )

            val oldTokenRecord = RefreshTokenEntity(
                userId = userId,
                tokenHash = tokenHash,
                familyId = familyId,
                issuedAt = now.minusSeconds(3600),
                expiresAt = now.plusSeconds(604800L),
                replacedBy = replacedBy,
            )

            whenever(refreshTokenRepository.findByTokenHash(tokenHash)).thenReturn(oldTokenRecord)
            whenever(refreshTokenRepository.findByFamilyId(familyId)).thenReturn(listOf(oldTokenRecord, replacedBy))
            whenever(refreshTokenRepository.save(any())).thenAnswer { it.arguments[0] as RefreshTokenEntity }

            // Act & Assert
            assertThrows<AuthenticationRequiredException> {
                refreshTokenService.refreshAccessToken(oldRefreshToken)
            }
            verify(refreshTokenRepository).findByFamilyId(familyId)
        }

        @Test
        fun `should throw exception when token revoked`() {
            // Arrange
            val userId = UUID.randomUUID()
            val refreshToken = jwtService.generateRefreshToken(userId)
            val tokenHash = hashToken(refreshToken)
            val now = Instant.now()

            val tokenRecord = RefreshTokenEntity(
                userId = userId,
                tokenHash = tokenHash,
                familyId = UUID.randomUUID(),
                issuedAt = now.minusSeconds(3600),
                expiresAt = now.plusSeconds(604800L),
                revokedAt = now.minusSeconds(1800),
            )

            whenever(refreshTokenRepository.findByTokenHash(tokenHash)).thenReturn(tokenRecord)

            // Act & Assert
            assertThrows<AuthenticationRequiredException> {
                refreshTokenService.refreshAccessToken(refreshToken)
            }
        }

        @Test
        fun `should throw exception when token expired`() {
            // Arrange
            val userId = UUID.randomUUID()
            val refreshToken = jwtService.generateRefreshToken(userId)
            val tokenHash = hashToken(refreshToken)
            val now = Instant.now()

            val tokenRecord = RefreshTokenEntity(
                userId = userId,
                tokenHash = tokenHash,
                familyId = UUID.randomUUID(),
                issuedAt = now.minusSeconds(604900L),
                expiresAt = now.minusSeconds(100), // Expired
            )

            whenever(refreshTokenRepository.findByTokenHash(tokenHash)).thenReturn(tokenRecord)

            // Act & Assert
            assertThrows<AuthenticationRequiredException> {
                refreshTokenService.refreshAccessToken(refreshToken)
            }
        }

        @Test
        fun `should throw exception when user not found`() {
            // Arrange
            val userId = UUID.randomUUID()
            val refreshToken = jwtService.generateRefreshToken(userId)
            val tokenHash = hashToken(refreshToken)
            val now = Instant.now()

            val tokenRecord = RefreshTokenEntity(
                userId = userId,
                tokenHash = tokenHash,
                familyId = UUID.randomUUID(),
                issuedAt = now,
                expiresAt = now.plusSeconds(604800L),
            )

            whenever(refreshTokenRepository.findByTokenHash(tokenHash)).thenReturn(tokenRecord)
            whenever(userRepository.findById(userId)).thenReturn(Optional.empty())

            // Act & Assert
            assertThrows<AuthenticationRequiredException> {
                refreshTokenService.refreshAccessToken(refreshToken)
            }
        }

        @Test
        fun `should throw exception when user disabled`() {
            // Arrange
            val userId = UUID.randomUUID()
            val user = SecurityTestDataBuilders.user(id = userId, email = "test@example.com")
            val refreshToken = jwtService.generateRefreshToken(userId)
            val tokenHash = hashToken(refreshToken)
            val now = Instant.now()

            val tokenRecord = RefreshTokenEntity(
                userId = userId,
                tokenHash = tokenHash,
                familyId = UUID.randomUUID(),
                issuedAt = now,
                expiresAt = now.plusSeconds(604800L),
            )

            whenever(refreshTokenRepository.findByTokenHash(tokenHash)).thenReturn(tokenRecord)
            whenever(userRepository.findById(userId)).thenReturn(Optional.of(user))
            whenever(principalRepository.findByPrincipalTypeAndExternalId(PrincipalType.USER, userId.toString()))
                .thenReturn(Optional.empty()) // No principal = disabled

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
            val tokenHash = "test-token-hash"
            val now = Instant.now()
            val tokenRecord = RefreshTokenEntity(
                userId = UUID.randomUUID(),
                tokenHash = tokenHash,
                familyId = UUID.randomUUID(),
                issuedAt = now,
                expiresAt = now.plusSeconds(604800L),
            )

            whenever(refreshTokenRepository.findByTokenHash(tokenHash)).thenReturn(tokenRecord)
            whenever(refreshTokenRepository.save(any())).thenAnswer { it.arguments[0] as RefreshTokenEntity }

            // Act
            refreshTokenService.revokeRefreshToken(tokenHash)

            // Assert
            verify(refreshTokenRepository).save(any())
            val captor = ArgumentCaptor.forClass(RefreshTokenEntity::class.java)
            verify(refreshTokenRepository).save(captor.capture())
            assertThat(captor.value.revokedAt).isNotNull()
        }

        @Test
        fun `should revoke all tokens for user`() {
            // Arrange
            val userId = UUID.randomUUID()
            val now = Instant.now()
            val token1 = RefreshTokenEntity(
                userId = userId,
                tokenHash = "hash1",
                familyId = UUID.randomUUID(),
                issuedAt = now,
                expiresAt = now.plusSeconds(604800L),
            )
            val token2 = RefreshTokenEntity(
                userId = userId,
                tokenHash = "hash2",
                familyId = UUID.randomUUID(),
                issuedAt = now,
                expiresAt = now.plusSeconds(604800L),
            )

            whenever(refreshTokenRepository.findByUserIdAndRevokedAtIsNull(userId))
                .thenReturn(listOf(token1, token2))
            whenever(refreshTokenRepository.save(any())).thenAnswer { it.arguments[0] as RefreshTokenEntity }

            // Act
            refreshTokenService.revokeAllTokensForUser(userId)

            // Assert
            verify(refreshTokenRepository, times(2)).save(any())
        }

        @Test
        fun `should revoke entire token family`() {
            // Arrange
            val familyId = UUID.randomUUID()
            val now = Instant.now()
            val token1 = RefreshTokenEntity(
                userId = UUID.randomUUID(),
                tokenHash = "hash1",
                familyId = familyId,
                issuedAt = now,
                expiresAt = now.plusSeconds(604800L),
            )
            val token2 = RefreshTokenEntity(
                userId = UUID.randomUUID(),
                tokenHash = "hash2",
                familyId = familyId,
                issuedAt = now,
                expiresAt = now.plusSeconds(604800L),
            )

            whenever(refreshTokenRepository.findByFamilyId(familyId))
                .thenReturn(listOf(token1, token2))
            whenever(refreshTokenRepository.save(any())).thenAnswer { it.arguments[0] as RefreshTokenEntity }

            // Act
            refreshTokenService.revokeFamily(familyId)

            // Assert
            verify(refreshTokenRepository, times(2)).save(any())
        }
    }
}

