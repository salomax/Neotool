package io.github.salomax.neotool.common.security.service

import io.github.salomax.neotool.common.security.exception.AuthenticationRequiredException
import io.github.salomax.neotool.common.security.jwt.JwtTokenValidator
import io.github.salomax.neotool.common.security.principal.JwtPrincipalDecoder
import io.github.salomax.neotool.common.security.principal.PrincipalType
import io.github.salomax.neotool.common.security.principal.RequestPrincipal
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.UUID

@DisplayName("JwtPrincipalDecoder Unit Tests")
class JwtPrincipalDecoderTest {
    private lateinit var jwtTokenValidator: JwtTokenValidator
    private lateinit var decoder: JwtPrincipalDecoder

    @BeforeEach
    fun setUp() {
        jwtTokenValidator = mock()
        decoder = JwtPrincipalDecoder(jwtTokenValidator)
    }

    @Nested
    inner class ServiceTokens {
        @Test
        fun `should build service principal with optional user context`() {
            val token = "service-token"
            val serviceId = UUID.randomUUID()
            val userId = UUID.randomUUID()
            val servicePerms = listOf("svc:a")
            val userPerms = listOf("user:a")

            whenever(jwtTokenValidator.isServiceToken(token)).thenReturn(true)
            whenever(jwtTokenValidator.getServiceIdFromToken(token)).thenReturn(serviceId)
            whenever(jwtTokenValidator.getPermissionsFromToken(token)).thenReturn(servicePerms)
            whenever(jwtTokenValidator.getUserIdFromServiceToken(token)).thenReturn(userId)
            whenever(jwtTokenValidator.getUserPermissionsFromServiceToken(token)).thenReturn(userPerms)

            val principal = decoder.fromToken(token)

            assertThat(principal).isEqualTo(
                RequestPrincipal(
                    principalType = PrincipalType.SERVICE,
                    userId = userId,
                    serviceId = serviceId,
                    token = token,
                    permissionsFromToken = servicePerms,
                    userPermissions = userPerms,
                ),
            )
        }

        @Test
        fun `should throw when service token misses service id`() {
            val token = "service-token"
            whenever(jwtTokenValidator.isServiceToken(token)).thenReturn(true)
            whenever(jwtTokenValidator.getServiceIdFromToken(token)).thenReturn(null)

            assertThatThrownBy { decoder.fromToken(token) }
                .isInstanceOf(AuthenticationRequiredException::class.java)
                .hasMessageContaining("missing service ID")
        }
    }

    @Nested
    inner class AccessTokens {
        @Test
        fun `should throw when token is not access token`() {
            val token = "invalid-token"
            whenever(jwtTokenValidator.isServiceToken(token)).thenReturn(false)
            whenever(jwtTokenValidator.isAccessToken(token)).thenReturn(false)

            assertThatThrownBy { decoder.fromToken(token) }
                .isInstanceOf(AuthenticationRequiredException::class.java)
                .hasMessageContaining("Invalid or expired access token")
        }

        @Test
        fun `should throw when user id is missing`() {
            val token = "access-token"
            whenever(jwtTokenValidator.isServiceToken(token)).thenReturn(false)
            whenever(jwtTokenValidator.isAccessToken(token)).thenReturn(true)
            whenever(jwtTokenValidator.getUserIdFromToken(token)).thenReturn(null)

            assertThatThrownBy { decoder.fromToken(token) }
                .isInstanceOf(AuthenticationRequiredException::class.java)
                .hasMessageContaining("missing user ID")
        }

        @Test
        fun `should build user principal`() {
            val token = "access-token"
            val userId = UUID.randomUUID()
            val perms = listOf("p1", "p2")

            whenever(jwtTokenValidator.isServiceToken(token)).thenReturn(false)
            whenever(jwtTokenValidator.isAccessToken(token)).thenReturn(true)
            whenever(jwtTokenValidator.getUserIdFromToken(token)).thenReturn(userId)
            whenever(jwtTokenValidator.getPermissionsFromToken(token)).thenReturn(perms)

            val principal = decoder.fromToken(token)

            assertThat(principal).isEqualTo(
                RequestPrincipal(
                    principalType = PrincipalType.USER,
                    userId = userId,
                    serviceId = null,
                    token = token,
                    permissionsFromToken = perms,
                    userPermissions = null,
                ),
            )
        }

        @Test
        fun `should default permissions to empty list`() {
            val token = "access-token"
            val userId = UUID.randomUUID()

            whenever(jwtTokenValidator.isServiceToken(token)).thenReturn(false)
            whenever(jwtTokenValidator.isAccessToken(token)).thenReturn(true)
            whenever(jwtTokenValidator.getUserIdFromToken(token)).thenReturn(userId)
            whenever(jwtTokenValidator.getPermissionsFromToken(token)).thenReturn(null)

            val principal = decoder.fromToken(token)

            assertThat(principal.permissionsFromToken).isEmpty()
        }
    }
}
