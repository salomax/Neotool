package io.github.salomax.neotool.common.security.principal

import io.github.salomax.neotool.common.security.exception.AuthenticationRequiredException
import io.github.salomax.neotool.common.security.jwt.JwtTokenValidator
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
            val accountId = UUID.randomUUID()
            val tokenId = UUID.randomUUID().toString()

            whenever(jwtTokenValidator.isServiceToken(token)).thenReturn(true)
            whenever(jwtTokenValidator.getServiceIdFromToken(token)).thenReturn(serviceId)
            whenever(jwtTokenValidator.getPermissionsFromToken(token)).thenReturn(servicePerms)
            whenever(jwtTokenValidator.getUserIdFromServiceToken(token)).thenReturn(userId)
            whenever(jwtTokenValidator.getUserPermissionsFromServiceToken(token)).thenReturn(userPerms)
            whenever(jwtTokenValidator.getCurrentAccountIdFromToken(token)).thenReturn(accountId)
            whenever(jwtTokenValidator.getJwtIdFromToken(token)).thenReturn(tokenId)

            val principal = decoder.fromToken(token)

            assertThat(principal).isEqualTo(
                RequestPrincipal(
                    principalType = PrincipalType.SERVICE,
                    userId = userId,
                    serviceId = serviceId,
                    token = token,
                    tokenId = tokenId,
                    permissionsFromToken = servicePerms,
                    userPermissions = userPerms,
                    currentAccountId = accountId,
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
            val tokenId = UUID.randomUUID().toString()

            whenever(jwtTokenValidator.isServiceToken(token)).thenReturn(false)
            whenever(jwtTokenValidator.isAccessToken(token)).thenReturn(true)
            whenever(jwtTokenValidator.getUserIdFromToken(token)).thenReturn(userId)
            whenever(jwtTokenValidator.getPermissionsFromToken(token)).thenReturn(perms)
            whenever(jwtTokenValidator.getCurrentAccountIdFromToken(token)).thenReturn(null)
            whenever(jwtTokenValidator.getAccountsFromToken(token)).thenReturn(null)
            whenever(jwtTokenValidator.getSessionVersionFromToken(token)).thenReturn(null)
            whenever(jwtTokenValidator.getJwtIdFromToken(token)).thenReturn(tokenId)

            val principal = decoder.fromToken(token)

            assertThat(principal.principalType).isEqualTo(PrincipalType.USER)
            assertThat(principal.userId).isEqualTo(userId)
            assertThat(principal.tokenId).isEqualTo(tokenId)
            assertThat(principal.permissionsFromToken).isEqualTo(perms)
            assertThat(principal.currentAccountId).isNull()
            assertThat(principal.accounts).isNull()
            assertThat(principal.accountIds).isEmpty()
            assertThat(principal.sessionVersion).isNull()
        }

        @Test
        fun `should map account claims when present in token`() {
            val token = "access-token"
            val userId = UUID.randomUUID()
            val accountId = UUID.randomUUID()
            val tokenId = UUID.randomUUID().toString()
            val accounts =
                listOf(
                    AccountSummary(
                        accountId,
                        "OWNER",
                    ),
                    AccountSummary(
                        UUID.randomUUID(),
                        "MEMBER",
                    ),
                )

            whenever(jwtTokenValidator.isServiceToken(token)).thenReturn(false)
            whenever(jwtTokenValidator.isAccessToken(token)).thenReturn(true)
            whenever(jwtTokenValidator.getUserIdFromToken(token)).thenReturn(userId)
            whenever(jwtTokenValidator.getPermissionsFromToken(token)).thenReturn(emptyList())
            whenever(jwtTokenValidator.getCurrentAccountIdFromToken(token)).thenReturn(accountId)
            whenever(jwtTokenValidator.getAccountsFromToken(token)).thenReturn(accounts)
            whenever(jwtTokenValidator.getSessionVersionFromToken(token)).thenReturn(1L)
            whenever(jwtTokenValidator.getJwtIdFromToken(token)).thenReturn(tokenId)

            val principal = decoder.fromToken(token)

            assertThat(principal.currentAccountId).isEqualTo(accountId)
            assertThat(principal.accounts).isEqualTo(accounts)
            assertThat(principal.accountIds).containsExactlyInAnyOrderElementsOf(accounts.map { it.accountId })
            assertThat(principal.sessionVersion).isEqualTo(1L)
            assertThat(principal.hasAccountContext()).isTrue()
            assertThat(principal.isMemberOfAccount(accountId)).isTrue()
        }

        @Test
        fun `should default permissions to empty list`() {
            val token = "access-token"
            val userId = UUID.randomUUID()
            val tokenId = UUID.randomUUID().toString()

            whenever(jwtTokenValidator.isServiceToken(token)).thenReturn(false)
            whenever(jwtTokenValidator.isAccessToken(token)).thenReturn(true)
            whenever(jwtTokenValidator.getUserIdFromToken(token)).thenReturn(userId)
            whenever(jwtTokenValidator.getPermissionsFromToken(token)).thenReturn(null)
            whenever(jwtTokenValidator.getJwtIdFromToken(token)).thenReturn(tokenId)

            val principal = decoder.fromToken(token)

            assertThat(principal.permissionsFromToken).isEmpty()
        }
    }
}
