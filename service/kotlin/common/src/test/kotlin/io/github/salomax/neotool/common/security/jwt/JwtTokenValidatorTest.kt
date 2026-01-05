package io.github.salomax.neotool.common.security.jwt

import io.github.salomax.neotool.common.security.config.JwtConfig
import io.github.salomax.neotool.common.security.exception.AuthenticationRequiredException
import io.github.salomax.neotool.common.security.key.KeyManager
import io.github.salomax.neotool.common.security.key.KeyManagerFactory
import io.github.salomax.neotool.common.test.security.JwtTestFixture
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.UUID

@DisplayName("JwtTokenValidator Unit Tests")
class JwtTokenValidatorTest {
    private lateinit var keyManagerFactory: KeyManagerFactory
    private lateinit var keyManager: KeyManager
    private lateinit var jwtConfig: JwtConfig
    private lateinit var validator: JwtTokenValidator

    @BeforeEach
    fun setUp() {
        keyManagerFactory = mock()
        keyManager = mock()
        jwtConfig = JwtConfig(keyId = "kid-1")

        whenever(keyManagerFactory.getKeyManager()).thenReturn(keyManager)
        whenever(keyManager.getPublicKey(any())).thenReturn(JwtTestFixture.publicKey)

        validator = JwtTokenValidator(jwtConfig, keyManagerFactory)
    }

    @Nested
    @DisplayName("Token Validation")
    inner class TokenValidationTests {
        @Test
        fun `should validate token with valid RS256 signature`() {
            // Arrange
            val token = JwtTestFixture.createToken(subject = "test-user")

            // Act
            val claims = validator.validateToken(token)

            // Assert
            assertThat(claims.subject).isEqualTo("test-user")
        }

        @Test
        fun `should validate token with default key when no key ID in header`() {
            // Arrange
            val token = JwtTestFixture.createToken(subject = "test-user")

            // Act
            val claims = validator.validateToken(token)

            // Assert
            assertThat(claims).isNotNull
            assertThat(claims.subject).isEqualTo("test-user")
        }

        @Test
        fun `should throw when public key is missing`() {
            // Arrange
            whenever(keyManager.getPublicKey(any())).thenReturn(null)
            val token = JwtTestFixture.createToken()

            // Act & Assert
            val exception =
                assertThrows<AuthenticationRequiredException> {
                    validator.validateToken(token)
                }

            assertThat(exception.message).contains("JWT public key is missing")
        }

        @Test
        fun `should throw when token has invalid signature`() {
            // Arrange
            val token = JwtTestFixture.createMalformedToken("invalid-signature")

            // Act & Assert
            val exception =
                assertThrows<AuthenticationRequiredException> {
                    validator.validateToken(token)
                }

            assertThat(exception.message).contains("Token validation failed")
        }

        @Test
        fun `should throw when token is expired`() {
            // Arrange
            val token = JwtTestFixture.createExpiredToken(expirationSecondsAgo = 3600)

            // Act & Assert
            val exception =
                assertThrows<AuthenticationRequiredException> {
                    validator.validateToken(token)
                }

            assertThat(exception.message).contains("Token validation failed")
        }

        @Test
        fun `should throw when token is malformed with missing parts`() {
            // Arrange
            val token = JwtTestFixture.createMalformedToken("missing-parts")

            // Act & Assert
            val exception =
                assertThrows<AuthenticationRequiredException> {
                    validator.validateToken(token)
                }

            assertThat(exception.message).contains("Token validation failed")
        }

        @Test
        fun `should throw when token has invalid base64 encoding`() {
            // Arrange
            val token = JwtTestFixture.createMalformedToken("invalid-base64")

            // Act & Assert
            val exception =
                assertThrows<AuthenticationRequiredException> {
                    validator.validateToken(token)
                }

            assertThat(exception.message).contains("Token validation failed")
        }
    }

    @Nested
    @DisplayName("User ID Extraction")
    inner class UserIdExtractionTests {
        @Test
        fun `should extract valid UUID from token subject`() {
            // Arrange
            val userId = UUID.randomUUID()
            val token = JwtTestFixture.createToken(subject = userId.toString())

            // Act
            val extractedUserId = validator.getUserIdFromToken(token)

            // Assert
            assertThat(extractedUserId).isEqualTo(userId)
        }

        @Test
        fun `should throw when token subject is not a valid UUID`() {
            // Arrange
            val token = JwtTestFixture.createToken(subject = "not-a-uuid")

            // Act & Assert
            val exception =
                assertThrows<AuthenticationRequiredException> {
                    validator.getUserIdFromToken(token)
                }

            assertThat(exception.message).contains("Invalid user ID in token subject")
        }

        @Test
        fun `should throw when token validation fails during user ID extraction`() {
            // Arrange
            val token = JwtTestFixture.createMalformedToken("invalid-signature")

            // Act & Assert
            assertThrows<AuthenticationRequiredException> {
                validator.getUserIdFromToken(token)
            }
        }
    }

    @Nested
    @DisplayName("Permissions Extraction")
    inner class PermissionsExtractionTests {
        @Test
        fun `should extract list of permissions from token`() {
            // Arrange
            val permissions = listOf("user:read", "user:write", "admin:access")
            val token = JwtTestFixture.createToken(permissions = permissions)

            // Act
            val extractedPermissions = validator.getPermissionsFromToken(token)

            // Assert
            assertThat(extractedPermissions).isEqualTo(permissions)
        }

        @Test
        fun `should return null when permissions claim is not present`() {
            // Arrange
            val token = JwtTestFixture.createToken(permissions = null)

            // Act
            val extractedPermissions = validator.getPermissionsFromToken(token)

            // Assert
            assertThat(extractedPermissions).isNull()
        }

        @Test
        fun `should return null when permissions claim is invalid type`() {
            // Arrange
            val token =
                JwtTestFixture.createToken(
                    customClaims = mapOf("permissions" to "not-a-list"),
                )

            // Act
            val extractedPermissions = validator.getPermissionsFromToken(token)

            // Assert
            assertThat(extractedPermissions).isNull()
        }

        @Test
        fun `should handle null permission values in list gracefully`() {
            // Arrange
            val permissions = listOf("user:read", "user:write")
            val token = JwtTestFixture.createToken(permissions = permissions)

            // Act
            val extractedPermissions = validator.getPermissionsFromToken(token)

            // Assert
            assertThat(extractedPermissions).isNotNull
            assertThat(extractedPermissions).doesNotContainNull()
        }

        @Test
        fun `should throw when token validation fails during permissions extraction`() {
            // Arrange
            val token = JwtTestFixture.createExpiredToken()

            // Act & Assert
            assertThrows<AuthenticationRequiredException> {
                validator.getPermissionsFromToken(token)
            }
        }
    }

    @Nested
    @DisplayName("Service Token Handling")
    inner class ServiceTokenTests {
        @Test
        fun `should return true for service token`() {
            // Arrange
            val token = JwtTestFixture.createServiceToken()

            // Act
            val isService = validator.isServiceToken(token)

            // Assert
            assertThat(isService).isTrue()
        }

        @Test
        fun `should return false for access token`() {
            // Arrange
            val token = JwtTestFixture.createToken(type = "access")

            // Act
            val isService = validator.isServiceToken(token)

            // Assert
            assertThat(isService).isFalse()
        }

        @Test
        fun `should extract service ID from service token`() {
            // Arrange
            val serviceId = UUID.randomUUID()
            val token = JwtTestFixture.createServiceToken(serviceId = serviceId)

            // Act
            val extractedServiceId = validator.getServiceIdFromToken(token)

            // Assert
            assertThat(extractedServiceId).isEqualTo(serviceId)
        }

        @Test
        fun `should return null service ID for non-service token`() {
            // Arrange
            val token = JwtTestFixture.createToken(type = "access")

            // Act
            val extractedServiceId = validator.getServiceIdFromToken(token)

            // Assert
            assertThat(extractedServiceId).isNull()
        }

        @Test
        fun `should extract user ID from service token with user context`() {
            // Arrange
            val userId = UUID.randomUUID()
            val token =
                JwtTestFixture.createServiceToken(
                    userId = userId,
                )

            // Act
            val extractedUserId = validator.getUserIdFromServiceToken(token)

            // Assert
            assertThat(extractedUserId).isEqualTo(userId)
        }

        @Test
        fun `should return null user ID from service token without user context`() {
            // Arrange
            val token = JwtTestFixture.createServiceToken(userId = null)

            // Act
            val extractedUserId = validator.getUserIdFromServiceToken(token)

            // Assert
            assertThat(extractedUserId).isNull()
        }

        @Test
        fun `should return null user ID for non-service token`() {
            // Arrange
            val token = JwtTestFixture.createToken(type = "access")

            // Act
            val extractedUserId = validator.getUserIdFromServiceToken(token)

            // Assert
            assertThat(extractedUserId).isNull()
        }

        @Test
        fun `should extract user permissions from service token with user context`() {
            // Arrange
            val userPermissions = listOf("user:read", "user:write")
            val token =
                JwtTestFixture.createServiceToken(
                    userId = UUID.randomUUID(),
                    userPermissions = userPermissions,
                )

            // Act
            val extractedPermissions = validator.getUserPermissionsFromServiceToken(token)

            // Assert
            assertThat(extractedPermissions).isEqualTo(userPermissions)
        }

        @Test
        fun `should return null user permissions for non-service token`() {
            // Arrange
            val token = JwtTestFixture.createToken(type = "access")

            // Act
            val extractedPermissions = validator.getUserPermissionsFromServiceToken(token)

            // Assert
            assertThat(extractedPermissions).isNull()
        }

        @Test
        fun `should return null user permissions from service token without user context`() {
            // Arrange
            val token = JwtTestFixture.createServiceToken(userPermissions = null)

            // Act
            val extractedPermissions = validator.getUserPermissionsFromServiceToken(token)

            // Assert
            assertThat(extractedPermissions).isNull()
        }
    }

    @Nested
    @DisplayName("Token Type and Metadata")
    inner class TokenTypeAndMetadataTests {
        @Test
        fun `should return true for access token`() {
            // Arrange
            val token = JwtTestFixture.createToken(type = "access")

            // Act
            val isAccess = validator.isAccessToken(token)

            // Assert
            assertThat(isAccess).isTrue()
        }

        @Test
        fun `should return false for refresh token when checking isAccessToken`() {
            // Arrange
            val token = JwtTestFixture.createToken(type = "refresh")

            // Act
            val isAccess = validator.isAccessToken(token)

            // Assert
            assertThat(isAccess).isFalse()
        }

        @Test
        fun `should return true for refresh token`() {
            // Arrange
            val token = JwtTestFixture.createToken(type = "refresh")

            // Act
            val isRefresh = validator.isRefreshToken(token)

            // Assert
            assertThat(isRefresh).isTrue()
        }

        @Test
        fun `should return false for access token when checking isRefreshToken`() {
            // Arrange
            val token = JwtTestFixture.createToken(type = "access")

            // Act
            val isRefresh = validator.isRefreshToken(token)

            // Assert
            assertThat(isRefresh).isFalse()
        }

        @Test
        fun `should extract token expiration time`() {
            // Arrange
            val token = JwtTestFixture.createToken(expirationSeconds = 900)

            // Act
            val expiration = validator.getTokenExpiration(token)

            // Assert
            assertThat(expiration).isNotNull
            assertThat(expiration!!.isAfter(java.time.Instant.now())).isTrue()
        }

        @Test
        fun `should extract token type string`() {
            // Arrange
            val token = JwtTestFixture.createToken(type = "access")

            // Act
            val tokenType = validator.getTokenType(token)

            // Assert
            assertThat(tokenType).isEqualTo("access")
        }

        @Test
        fun `should extract audience from token`() {
            // Arrange
            val token = JwtTestFixture.createToken(audience = "test-audience")

            // Act
            val audience = validator.getAudienceFromToken(token)

            // Assert
            assertThat(audience).isEqualTo("test-audience")
        }

        @Test
        fun `should return null audience when not present`() {
            // Arrange
            val token = JwtTestFixture.createToken(audience = null)

            // Act
            val audience = validator.getAudienceFromToken(token)

            // Assert
            assertThat(audience).isNull()
        }

        @Test
        fun `should extract email from token`() {
            // Arrange
            val token = JwtTestFixture.createToken(email = "test@example.com")

            // Act
            val email = validator.getEmailFromToken(token)

            // Assert
            assertThat(email).isEqualTo("test@example.com")
        }

        @Test
        fun `should return null email when not present`() {
            // Arrange
            val token = JwtTestFixture.createToken(email = null)

            // Act
            val email = validator.getEmailFromToken(token)

            // Assert
            assertThat(email).isNull()
        }
    }
}
