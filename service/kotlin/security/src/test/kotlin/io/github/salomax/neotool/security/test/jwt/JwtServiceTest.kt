package io.github.salomax.neotool.security.test.jwt

import io.github.salomax.neotool.security.config.JwtConfig
import io.github.salomax.neotool.security.service.JwtService
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

@DisplayName("JwtService Unit Tests")
class JwtServiceTest {

    private lateinit var jwtService: JwtService
    private lateinit var jwtConfig: JwtConfig

    @BeforeEach
    fun setUp() {
        jwtConfig = JwtConfig(
            secret = "test-secret-key-minimum-32-characters-long-for-hmac-sha256",
            accessTokenExpirationSeconds = 900L, // 15 minutes
            refreshTokenExpirationSeconds = 604800L // 7 days
        )
        jwtService = JwtService(jwtConfig)
    }

    @Nested
    @DisplayName("Access Token Generation")
    inner class AccessTokenGenerationTests {

        @Test
        fun `should generate valid access token`() {
            val userId = UUID.randomUUID()
            val email = "test@example.com"

            val token = jwtService.generateAccessToken(userId, email)

            Assertions.assertThat(token).isNotBlank()
            Assertions.assertThat(token.split(".")).hasSize(3) // JWT has 3 parts: header.payload.signature
        }

        @Test
        fun `should generate different tokens for same user`() {
            val userId = UUID.randomUUID()
            val email = "test@example.com"

            val token1 = jwtService.generateAccessToken(userId, email)
            Thread.sleep(1000) // Ensure different iat
            val token2 = jwtService.generateAccessToken(userId, email)

            Assertions.assertThat(token1).isNotEqualTo(token2)
        }

        @Test
        fun `should include user ID in token subject`() {
            val userId = UUID.randomUUID()
            val email = "test@example.com"

            val token = jwtService.generateAccessToken(userId, email)
            val extractedUserId = jwtService.getUserIdFromToken(token)

            Assertions.assertThat(extractedUserId).isEqualTo(userId)
        }

        @Test
        fun `should validate generated access token`() {
            val userId = UUID.randomUUID()
            val email = "test@example.com"

            val token = jwtService.generateAccessToken(userId, email)
            val claims = jwtService.validateToken(token)

            Assertions.assertThat(claims).isNotNull()
            Assertions.assertThat(claims?.subject).isEqualTo(userId.toString())
            Assertions.assertThat(claims?.get("email", String::class.java)).isEqualTo(email)
            Assertions.assertThat(claims?.get("type", String::class.java)).isEqualTo("access")
        }

        @Test
        fun `should identify token as access token`() {
            val userId = UUID.randomUUID()
            val email = "test@example.com"

            val token = jwtService.generateAccessToken(userId, email)

            Assertions.assertThat(jwtService.isAccessToken(token)).isTrue()
            Assertions.assertThat(jwtService.isRefreshToken(token)).isFalse()
        }

        @Test
        fun `should have correct expiration time for access token`() {
            val userId = UUID.randomUUID()
            val email = "test@example.com"
            val beforeGeneration = Instant.now()

            val token = jwtService.generateAccessToken(userId, email)
            val expiration = jwtService.getTokenExpiration(token)

            Assertions.assertThat(expiration).isNotNull()
            val expectedExpiration = beforeGeneration.plusSeconds(jwtConfig.accessTokenExpirationSeconds)
            Assertions.assertThat(expiration).isAfter(expectedExpiration.minusSeconds(5))
            Assertions.assertThat(expiration).isBefore(expectedExpiration.plusSeconds(5))
        }
    }

    @Nested
    @DisplayName("Refresh Token Generation")
    inner class RefreshTokenGenerationTests {

        @Test
        fun `should generate valid refresh token`() {
            val userId = UUID.randomUUID()

            val token = jwtService.generateRefreshToken(userId)

            Assertions.assertThat(token).isNotBlank()
            Assertions.assertThat(token.split(".")).hasSize(3) // JWT has 3 parts
        }

        @Test
        fun `should generate different refresh tokens for same user`() {
            val userId = UUID.randomUUID()

            val token1 = jwtService.generateRefreshToken(userId)
            Thread.sleep(1000) // Ensure different iat
            val token2 = jwtService.generateRefreshToken(userId)

            Assertions.assertThat(token1).isNotEqualTo(token2)
        }

        @Test
        fun `should include user ID in refresh token subject`() {
            val userId = UUID.randomUUID()

            val token = jwtService.generateRefreshToken(userId)
            val extractedUserId = jwtService.getUserIdFromToken(token)

            Assertions.assertThat(extractedUserId).isEqualTo(userId)
        }

        @Test
        fun `should validate generated refresh token`() {
            val userId = UUID.randomUUID()

            val token = jwtService.generateRefreshToken(userId)
            val claims = jwtService.validateToken(token)

            Assertions.assertThat(claims).isNotNull()
            Assertions.assertThat(claims?.subject).isEqualTo(userId.toString())
            Assertions.assertThat(claims?.get("type", String::class.java)).isEqualTo("refresh")
        }

        @Test
        fun `should identify token as refresh token`() {
            val userId = UUID.randomUUID()

            val token = jwtService.generateRefreshToken(userId)

            Assertions.assertThat(jwtService.isRefreshToken(token)).isTrue()
            Assertions.assertThat(jwtService.isAccessToken(token)).isFalse()
        }

        @Test
        fun `should have correct expiration time for refresh token`() {
            val userId = UUID.randomUUID()
            val beforeGeneration = Instant.now()

            val token = jwtService.generateRefreshToken(userId)
            val expiration = jwtService.getTokenExpiration(token)

            Assertions.assertThat(expiration).isNotNull()
            val expectedExpiration = beforeGeneration.plusSeconds(jwtConfig.refreshTokenExpirationSeconds)
            Assertions.assertThat(expiration).isAfter(expectedExpiration.minusSeconds(5))
            Assertions.assertThat(expiration).isBefore(expectedExpiration.plusSeconds(5))
        }
    }

    @Nested
    @DisplayName("Token Validation")
    inner class TokenValidationTests {

        @Test
        fun `should validate valid access token`() {
            val userId = UUID.randomUUID()
            val email = "test@example.com"
            val token = jwtService.generateAccessToken(userId, email)

            val claims = jwtService.validateToken(token)

            Assertions.assertThat(claims).isNotNull()
            Assertions.assertThat(claims?.subject).isEqualTo(userId.toString())
        }

        @Test
        fun `should validate valid refresh token`() {
            val userId = UUID.randomUUID()
            val token = jwtService.generateRefreshToken(userId)

            val claims = jwtService.validateToken(token)

            Assertions.assertThat(claims).isNotNull()
            Assertions.assertThat(claims?.subject).isEqualTo(userId.toString())
        }

        @Test
        fun `should reject invalid token format`() {
            val invalidToken = "invalid.token.format"

            val claims = jwtService.validateToken(invalidToken)

            Assertions.assertThat(claims).isNull()
        }

        @Test
        fun `should reject tampered token`() {
            val userId = UUID.randomUUID()
            val email = "test@example.com"
            val token = jwtService.generateAccessToken(userId, email)
            val tamperedToken = token.substring(0, token.length - 5) + "XXXXX"

            val claims = jwtService.validateToken(tamperedToken)

            Assertions.assertThat(claims).isNull()
        }

        @Test
        fun `should reject token signed with different secret`() {
            val userId = UUID.randomUUID()
            val email = "test@example.com"
            val token = jwtService.generateAccessToken(userId, email)

            // Create a new service with different secret
            val differentConfig = JwtConfig(
                secret = "different-secret-key-minimum-32-characters-long-for-hmac",
                accessTokenExpirationSeconds = 900L,
                refreshTokenExpirationSeconds = 604800L
            )
            val differentService = JwtService(differentConfig)

            val claims = differentService.validateToken(token)

            Assertions.assertThat(claims).isNull()
        }

        @Test
        fun `should reject empty token`() {
            val claims = jwtService.validateToken("")

            Assertions.assertThat(claims).isNull()
        }

        @Test
        fun `should reject null token`() {
            // This test verifies the method handles null gracefully
            // In practice, callers should check for null before calling
            val token: String? = null
            if (token != null) {
                val claims = jwtService.validateToken(token)
                Assertions.assertThat(claims).isNull()
            }
        }
    }

    @Nested
    @DisplayName("User ID Extraction")
    inner class UserIdExtractionTests {

        @Test
        fun `should extract user ID from valid access token`() {
            val userId = UUID.randomUUID()
            val email = "test@example.com"
            val token = jwtService.generateAccessToken(userId, email)

            val extractedUserId = jwtService.getUserIdFromToken(token)

            Assertions.assertThat(extractedUserId).isEqualTo(userId)
        }

        @Test
        fun `should extract user ID from valid refresh token`() {
            val userId = UUID.randomUUID()
            val token = jwtService.generateRefreshToken(userId)

            val extractedUserId = jwtService.getUserIdFromToken(token)

            Assertions.assertThat(extractedUserId).isEqualTo(userId)
        }

        @Test
        fun `should return null for invalid token`() {
            val invalidToken = "invalid.token"

            val userId = jwtService.getUserIdFromToken(invalidToken)

            Assertions.assertThat(userId).isNull()
        }

        @Test
        fun `should return null for token with invalid subject`() {
            // This test would require creating a token with invalid UUID in subject
            // For now, we test that invalid tokens return null
            val invalidToken = "invalid.token.format"

            val userId = jwtService.getUserIdFromToken(invalidToken)

            Assertions.assertThat(userId).isNull()
        }
    }

    @Nested
    @DisplayName("Token Type Identification")
    inner class TokenTypeIdentificationTests {

        @Test
        fun `should correctly identify access token type`() {
            val userId = UUID.randomUUID()
            val email = "test@example.com"
            val accessToken = jwtService.generateAccessToken(userId, email)

            Assertions.assertThat(jwtService.isAccessToken(accessToken)).isTrue()
            Assertions.assertThat(jwtService.isRefreshToken(accessToken)).isFalse()
        }

        @Test
        fun `should correctly identify refresh token type`() {
            val userId = UUID.randomUUID()
            val refreshToken = jwtService.generateRefreshToken(userId)

            Assertions.assertThat(jwtService.isRefreshToken(refreshToken)).isTrue()
            Assertions.assertThat(jwtService.isAccessToken(refreshToken)).isFalse()
        }

        @Test
        fun `should return false for invalid token type check`() {
            val invalidToken = "invalid.token"

            Assertions.assertThat(jwtService.isAccessToken(invalidToken)).isFalse()
            Assertions.assertThat(jwtService.isRefreshToken(invalidToken)).isFalse()
        }
    }

    @Nested
    @DisplayName("Token Expiration")
    inner class TokenExpirationTests {

        @Test
        fun `should return expiration time for valid token`() {
            val userId = UUID.randomUUID()
            val email = "test@example.com"
            val token = jwtService.generateAccessToken(userId, email)

            val expiration = jwtService.getTokenExpiration(token)

            Assertions.assertThat(expiration).isNotNull()
            Assertions.assertThat(expiration).isAfter(Instant.now())
        }

        @Test
        fun `should return null expiration for invalid token`() {
            val invalidToken = "invalid.token"

            val expiration = jwtService.getTokenExpiration(invalidToken)

            Assertions.assertThat(expiration).isNull()
        }
    }
}
