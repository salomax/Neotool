package io.github.salomax.neotool.security.test.service.unit

import io.github.salomax.neotool.common.security.config.JwtConfig
import io.github.salomax.neotool.common.security.exception.AuthenticationRequiredException
import io.github.salomax.neotool.common.security.principal.AuthContext
import io.github.salomax.neotool.common.security.principal.PrincipalType
import io.github.salomax.neotool.security.model.PrincipalEntity
import io.github.salomax.neotool.security.model.RefreshTokenEntity
import io.github.salomax.neotool.security.repo.PrincipalRepository
import io.github.salomax.neotool.security.repo.RefreshTokenRepository
import io.github.salomax.neotool.security.repo.UserRepository
import io.github.salomax.neotool.security.service.authentication.AuthContextFactory
import io.github.salomax.neotool.security.service.jwt.JwtTokenIssuer
import io.github.salomax.neotool.security.service.jwt.RefreshTokenService
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
    private lateinit var jwtTokenIssuer: JwtTokenIssuer
    private lateinit var authContextFactory: AuthContextFactory
    private lateinit var jwtConfig: JwtConfig
    private lateinit var refreshTokenService: RefreshTokenService

    @BeforeEach
    fun setUp() {
        refreshTokenRepository = mock()
        userRepository = mock()
        principalRepository = mock()
        jwtTokenIssuer = mock()
        jwtConfig =
            JwtConfig(
                secret = "test-secret-key-minimum-32-characters-long-for-hmac-sha256",
                accessTokenExpirationSeconds = 900L,
                refreshTokenExpirationSeconds = 604800L,
            )
        authContextFactory = mock()
        refreshTokenService =
            RefreshTokenService(
                refreshTokenRepository,
                userRepository,
                principalRepository,
                jwtTokenIssuer,
                authContextFactory,
                jwtConfig,
            )
    }

    private fun mockEnabledPrincipal(userId: UUID) {
        val principal =
            PrincipalEntity(
                id = UUID.randomUUID(),
                principalType = PrincipalType.USER,
                externalId = userId.toString(),
                enabled = true,
            )
        whenever(principalRepository.findByPrincipalTypeAndExternalId(PrincipalType.USER, userId.toString()))
            .thenReturn(Optional.of(principal))
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
        fun `should create refresh token for user`() {
            // Arrange
            val userId = UUID.randomUUID()
            val user = SecurityTestDataBuilders.user(id = userId, email = "test@example.com")
            val expectedToken = "refresh-token-123"
            whenever(userRepository.findById(userId)).thenReturn(Optional.of(user))
            whenever(jwtTokenIssuer.generateRefreshToken(userId)).thenReturn(expectedToken)
            whenever(refreshTokenRepository.save(any())).thenAnswer { it.arguments[0] as RefreshTokenEntity }

            // Act
            val refreshToken = refreshTokenService.createRefreshToken(userId)

            // Assert
            assertThat(refreshToken).isNotBlank()
            assertThat(refreshToken).isEqualTo(expectedToken)
            verify(userRepository).findById(userId)
            verify(jwtTokenIssuer).generateRefreshToken(userId)
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
            val token1Value = "refresh-token-1"
            val token2Value = "refresh-token-2"
            whenever(userRepository.findById(userId)).thenReturn(Optional.of(user))
            whenever(jwtTokenIssuer.generateRefreshToken(userId))
                .thenReturn(token1Value)
                .thenReturn(token2Value)
            whenever(refreshTokenRepository.save(any())).thenAnswer { it.arguments[0] as RefreshTokenEntity }

            // Act
            val token1 = refreshTokenService.createRefreshToken(userId)
            val token2 = refreshTokenService.createRefreshToken(userId)

            // Assert
            assertThat(token1).isNotEqualTo(token2)
            assertThat(token1).isEqualTo(token1Value)
            assertThat(token2).isEqualTo(token2Value)
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
            val oldRefreshToken = "old-refresh-token-123"
            whenever(jwtTokenIssuer.generateRefreshToken(userId)).thenReturn(oldRefreshToken)
            val tokenHash = hashToken(oldRefreshToken)
            val now = Instant.now()
            val expiresAt = now.plusSeconds(604800L)

            val oldTokenRecord =
                RefreshTokenEntity(
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
            whenever(jwtTokenIssuer.generateAccessToken(any<AuthContext>())).thenReturn("new-access-token")
            whenever(jwtTokenIssuer.generateRefreshToken(userId)).thenReturn("new-refresh-token-456")
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
        fun `should refresh OAuth token with granted scopes only`() {
            val userId = UUID.randomUUID()
            val accountId = UUID.randomUUID()
            val user = SecurityTestDataBuilders.user(id = userId, email = "test@example.com")
            val familyId = UUID.randomUUID()
            val oldRefreshToken = "oauth-refresh-token"
            val tokenHash = hashToken(oldRefreshToken)
            val grantedScopes = listOf("finance:cashflow:read")
            val oldTokenRecord =
                RefreshTokenEntity(
                    userId = userId,
                    tokenHash = tokenHash,
                    familyId = familyId,
                    clientId = "test-agent",
                    grantedScopes = grantedScopes.joinToString(" "),
                    resource = "https://api.invistus.test",
                    accountId = accountId,
                    issuedAt = Instant.now().minusSeconds(3600),
                    expiresAt = Instant.now().plusSeconds(604800L),
                )

            whenever(refreshTokenRepository.findByTokenHash(tokenHash)).thenReturn(oldTokenRecord)
            whenever(userRepository.findById(userId)).thenReturn(Optional.of(user))
            mockEnabledPrincipal(userId)
            whenever(
                jwtTokenIssuer.generateOAuthAccessToken(
                    userId = userId,
                    email = user.email,
                    clientId = "test-agent",
                    scopes = grantedScopes,
                    resource = "https://api.invistus.test",
                    currentAccountId = accountId,
                ),
            ).thenReturn("oauth-access-token")
            whenever(jwtTokenIssuer.generateRefreshToken(userId)).thenReturn("new-oauth-refresh-token")
            whenever(refreshTokenRepository.save(any())).thenAnswer { it.arguments[0] as RefreshTokenEntity }

            val result = refreshTokenService.refreshAccessToken(oldRefreshToken)

            assertThat(result.accessToken).isEqualTo("oauth-access-token")
            verify(authContextFactory, never()).build(any())
            verify(jwtTokenIssuer, never()).generateAccessToken(any<AuthContext>())
        }

        @Test
        fun `should preserve account binding across consecutive OAuth refresh rotations`() {
            val userId = UUID.randomUUID()
            val accountId = UUID.randomUUID()
            val user = SecurityTestDataBuilders.user(id = userId, email = "test@example.com")
            val familyId = UUID.randomUUID()
            val firstRefreshToken = "oauth-refresh-token-1"
            val secondRefreshToken = "oauth-refresh-token-2"
            val thirdRefreshToken = "oauth-refresh-token-3"
            val grantedScopes = listOf("finance:cashflow:read")
            val persistedTokens = mutableMapOf<String, RefreshTokenEntity>()
            val initialRecord =
                RefreshTokenEntity(
                    userId = userId,
                    tokenHash = hashToken(firstRefreshToken),
                    familyId = familyId,
                    clientId = "test-agent",
                    grantedScopes = grantedScopes.joinToString(" "),
                    resource = "https://api.invistus.test",
                    accountId = accountId,
                    issuedAt = Instant.now().minusSeconds(3600),
                    expiresAt = Instant.now().plusSeconds(604800L),
                )
            persistedTokens[initialRecord.tokenHash] = initialRecord

            whenever(refreshTokenRepository.findByTokenHash(any())).thenAnswer {
                persistedTokens[it.arguments[0] as String]
            }
            whenever(userRepository.findById(userId)).thenReturn(Optional.of(user))
            mockEnabledPrincipal(userId)
            whenever(
                jwtTokenIssuer.generateOAuthAccessToken(
                    userId = userId,
                    email = user.email,
                    clientId = "test-agent",
                    scopes = grantedScopes,
                    resource = "https://api.invistus.test",
                    currentAccountId = accountId,
                ),
            ).thenReturn("oauth-access-token-1", "oauth-access-token-2")
            whenever(jwtTokenIssuer.generateRefreshToken(userId))
                .thenReturn(secondRefreshToken, thirdRefreshToken)
            whenever(refreshTokenRepository.save(any())).thenAnswer {
                val saved = it.arguments[0] as RefreshTokenEntity
                persistedTokens[saved.tokenHash] = saved
                saved
            }

            refreshTokenService.refreshAccessToken(firstRefreshToken)
            refreshTokenService.refreshAccessToken(secondRefreshToken)

            assertThat(persistedTokens[hashToken(secondRefreshToken)]?.accountId).isEqualTo(accountId)
            assertThat(persistedTokens[hashToken(thirdRefreshToken)]?.accountId).isEqualTo(accountId)
            verify(authContextFactory, never()).build(any())
        }

        @Test
        fun `should throw when OAuth refresh token record has null accountId`() {
            val userId = UUID.randomUUID()
            val user = SecurityTestDataBuilders.user(id = userId, email = "test@example.com")
            val refreshToken = "oauth-refresh-token-missing-account"
            val tokenRecord =
                RefreshTokenEntity(
                    userId = userId,
                    tokenHash = hashToken(refreshToken),
                    familyId = UUID.randomUUID(),
                    clientId = "test-agent",
                    grantedScopes = "finance:cashflow:read",
                    resource = "https://api.invistus.test",
                    accountId = null,
                    issuedAt = Instant.now().minusSeconds(3600),
                    expiresAt = Instant.now().plusSeconds(604800L),
                )

            whenever(refreshTokenRepository.findByTokenHash(tokenRecord.tokenHash)).thenReturn(tokenRecord)
            whenever(userRepository.findById(userId)).thenReturn(Optional.of(user))
            mockEnabledPrincipal(userId)

            assertThrows<AuthenticationRequiredException> {
                refreshTokenService.refreshAccessToken(refreshToken)
            }
        }

        @Test
        fun `should throw when OAuth refresh token record has null resource`() {
            val userId = UUID.randomUUID()
            val user = SecurityTestDataBuilders.user(id = userId, email = "test@example.com")
            val refreshToken = "oauth-refresh-token-missing-resource"
            val tokenRecord =
                RefreshTokenEntity(
                    userId = userId,
                    tokenHash = hashToken(refreshToken),
                    familyId = UUID.randomUUID(),
                    clientId = "test-agent",
                    grantedScopes = "finance:cashflow:read",
                    resource = null,
                    accountId = UUID.randomUUID(),
                    issuedAt = Instant.now().minusSeconds(3600),
                    expiresAt = Instant.now().plusSeconds(604800L),
                )

            whenever(refreshTokenRepository.findByTokenHash(tokenRecord.tokenHash)).thenReturn(tokenRecord)
            whenever(userRepository.findById(userId)).thenReturn(Optional.of(user))
            mockEnabledPrincipal(userId)

            assertThrows<AuthenticationRequiredException> {
                refreshTokenService.refreshAccessToken(refreshToken)
            }
        }

        @Test
        fun `should throw when OAuth refresh token record has null granted scopes`() {
            val userId = UUID.randomUUID()
            val user = SecurityTestDataBuilders.user(id = userId, email = "test@example.com")
            val refreshToken = "oauth-refresh-token-missing-scopes"
            val tokenRecord =
                RefreshTokenEntity(
                    userId = userId,
                    tokenHash = hashToken(refreshToken),
                    familyId = UUID.randomUUID(),
                    clientId = "test-agent",
                    grantedScopes = null,
                    resource = "https://api.invistus.test",
                    accountId = UUID.randomUUID(),
                    issuedAt = Instant.now().minusSeconds(3600),
                    expiresAt = Instant.now().plusSeconds(604800L),
                )

            whenever(refreshTokenRepository.findByTokenHash(tokenRecord.tokenHash)).thenReturn(tokenRecord)
            whenever(userRepository.findById(userId)).thenReturn(Optional.of(user))
            mockEnabledPrincipal(userId)

            assertThrows<AuthenticationRequiredException> {
                refreshTokenService.refreshAccessToken(refreshToken)
            }
            verify(authContextFactory, never()).build(any())
            verify(jwtTokenIssuer, never()).generateAccessToken(any<AuthContext>())
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
            val oldRefreshToken = "old-refresh-token-replaced"
            val tokenHash = hashToken(oldRefreshToken)
            val familyId = UUID.randomUUID()
            val now = Instant.now()
            val replacedBy =
                RefreshTokenEntity(
                    userId = userId,
                    tokenHash = "replacement-hash",
                    familyId = familyId,
                    issuedAt = now,
                    expiresAt = now.plusSeconds(604800L),
                )

            val oldTokenRecord =
                RefreshTokenEntity(
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
            val refreshToken = "revoked-refresh-token"
            val tokenHash = hashToken(refreshToken)
            val now = Instant.now()

            val tokenRecord =
                RefreshTokenEntity(
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
            val refreshToken = "expired-refresh-token"
            val tokenHash = hashToken(refreshToken)
            val now = Instant.now()

            val tokenRecord =
                RefreshTokenEntity(
                    userId = userId,
                    tokenHash = tokenHash,
                    familyId = UUID.randomUUID(),
                    issuedAt = now.minusSeconds(604900L),
                    // Expired
                    expiresAt = now.minusSeconds(100),
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
            val refreshToken = "refresh-token-user-not-found"
            val tokenHash = hashToken(refreshToken)
            val now = Instant.now()

            val tokenRecord =
                RefreshTokenEntity(
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
            val refreshToken = "refresh-token-disabled-user"
            val tokenHash = hashToken(refreshToken)
            val now = Instant.now()

            val tokenRecord =
                RefreshTokenEntity(
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
            val tokenRecord =
                RefreshTokenEntity(
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
            val token1 =
                RefreshTokenEntity(
                    userId = userId,
                    tokenHash = "hash1",
                    familyId = UUID.randomUUID(),
                    issuedAt = now,
                    expiresAt = now.plusSeconds(604800L),
                )
            val token2 =
                RefreshTokenEntity(
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
        fun `should revoke only oauth refresh tokens for the matching connection triple`() {
            val userId = UUID.randomUUID()
            val accountId = UUID.randomUUID()
            val revokedAt = Instant.parse("2026-07-30T18:30:00Z")
            val matchingOne =
                RefreshTokenEntity(
                    userId = userId,
                    tokenHash = "hash-matching-1",
                    familyId = UUID.randomUUID(),
                    clientId = "agent-a",
                    accountId = accountId,
                    issuedAt = revokedAt.minusSeconds(600),
                    expiresAt = revokedAt.plusSeconds(600),
                )
            val matchingTwo =
                RefreshTokenEntity(
                    userId = userId,
                    tokenHash = "hash-matching-2",
                    familyId = UUID.randomUUID(),
                    clientId = "agent-a",
                    accountId = accountId,
                    issuedAt = revokedAt.minusSeconds(600),
                    expiresAt = revokedAt.plusSeconds(600),
                )

            whenever(
                refreshTokenRepository.findByUserIdAndClientIdAndAccountIdAndRevokedAtIsNull(
                    userId,
                    "agent-a",
                    accountId,
                ),
            ).thenReturn(listOf(matchingOne, matchingTwo))
            whenever(refreshTokenRepository.save(any())).thenAnswer { it.arguments[0] as RefreshTokenEntity }

            val revokedCount =
                refreshTokenService.revokeOAuthTokensForConnection(
                    userId = userId,
                    clientId = "agent-a",
                    accountId = accountId,
                    revokedAt = revokedAt,
                )

            assertThat(revokedCount).isEqualTo(2)
            assertThat(matchingOne.revokedAt).isEqualTo(revokedAt)
            assertThat(matchingTwo.revokedAt).isEqualTo(revokedAt)
            verify(refreshTokenRepository, times(2)).save(any())
        }

        @Test
        fun `should revoke entire token family`() {
            // Arrange
            val familyId = UUID.randomUUID()
            val now = Instant.now()
            val token1 =
                RefreshTokenEntity(
                    userId = UUID.randomUUID(),
                    tokenHash = "hash1",
                    familyId = familyId,
                    issuedAt = now,
                    expiresAt = now.plusSeconds(604800L),
                )
            val token2 =
                RefreshTokenEntity(
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
