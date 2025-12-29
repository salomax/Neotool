package io.github.salomax.neotool.security.test.service.unit

import io.github.salomax.neotool.security.service.JwtPrincipalDecoder
import io.github.salomax.neotool.security.service.JwtService
import io.github.salomax.neotool.security.service.PrincipalType
import io.github.salomax.neotool.security.service.RequestPrincipal
import io.github.salomax.neotool.security.service.exception.AuthenticationRequiredException
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
    private lateinit var jwtService: JwtService
    private lateinit var decoder: JwtPrincipalDecoder

    @BeforeEach
    fun setUp() {
        jwtService = mock()
        decoder = JwtPrincipalDecoder(jwtService)
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

            whenever(jwtService.isServiceToken(token)).thenReturn(true)
            whenever(jwtService.getServiceIdFromToken(token)).thenReturn(serviceId)
            whenever(jwtService.getPermissionsFromToken(token)).thenReturn(servicePerms)
            whenever(jwtService.getUserIdFromServiceToken(token)).thenReturn(userId)
            whenever(jwtService.getUserPermissionsFromServiceToken(token)).thenReturn(userPerms)

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
            whenever(jwtService.isServiceToken(token)).thenReturn(true)
            whenever(jwtService.getServiceIdFromToken(token)).thenReturn(null)

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
            whenever(jwtService.isServiceToken(token)).thenReturn(false)
            whenever(jwtService.isAccessToken(token)).thenReturn(false)

            assertThatThrownBy { decoder.fromToken(token) }
                .isInstanceOf(AuthenticationRequiredException::class.java)
                .hasMessageContaining("Invalid or expired access token")
        }

        @Test
        fun `should throw when user id is missing`() {
            val token = "access-token"
            whenever(jwtService.isServiceToken(token)).thenReturn(false)
            whenever(jwtService.isAccessToken(token)).thenReturn(true)
            whenever(jwtService.getUserIdFromToken(token)).thenReturn(null)

            assertThatThrownBy { decoder.fromToken(token) }
                .isInstanceOf(AuthenticationRequiredException::class.java)
                .hasMessageContaining("missing user ID")
        }

        @Test
        fun `should build user principal`() {
            val token = "access-token"
            val userId = UUID.randomUUID()
            val perms = listOf("p1", "p2")

            whenever(jwtService.isServiceToken(token)).thenReturn(false)
            whenever(jwtService.isAccessToken(token)).thenReturn(true)
            whenever(jwtService.getUserIdFromToken(token)).thenReturn(userId)
            whenever(jwtService.getPermissionsFromToken(token)).thenReturn(perms)

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

            whenever(jwtService.isServiceToken(token)).thenReturn(false)
            whenever(jwtService.isAccessToken(token)).thenReturn(true)
            whenever(jwtService.getUserIdFromToken(token)).thenReturn(userId)
            whenever(jwtService.getPermissionsFromToken(token)).thenReturn(null)

            val principal = decoder.fromToken(token)

            assertThat(principal.permissionsFromToken).isEmpty()
        }
    }
}
