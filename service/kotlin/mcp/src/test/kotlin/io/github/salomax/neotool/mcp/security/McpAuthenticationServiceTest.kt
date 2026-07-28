package io.github.salomax.neotool.mcp.security

import io.github.salomax.neotool.common.security.exception.AuthenticationFailureCause
import io.github.salomax.neotool.common.security.exception.AuthenticationRequiredException
import io.github.salomax.neotool.common.security.jwt.JwtTokenValidator
import io.github.salomax.neotool.common.security.principal.PrincipalType
import io.github.salomax.neotool.common.security.principal.RequestPrincipal
import io.github.salomax.neotool.common.security.principal.TokenPrincipalDecoder
import io.jsonwebtoken.Claims
import io.jsonwebtoken.Jwts
import io.micronaut.http.HttpHeaders
import io.micronaut.http.HttpRequest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.UUID

class McpAuthenticationServiceTest {
    private lateinit var jwtTokenValidator: JwtTokenValidator
    private lateinit var principalDecoder: TokenPrincipalDecoder
    private lateinit var revocationStore: ConnectionRevocationStore
    private lateinit var service: McpAuthenticationService

    @BeforeEach
    fun setUp() {
        jwtTokenValidator = mock()
        principalDecoder = mock()
        revocationStore = mock()

        whenever(principalDecoder.fromToken(any())).thenReturn(
            RequestPrincipal(
                principalType = PrincipalType.USER,
                userId = UUID.randomUUID(),
                serviceId = null,
                token = "token",
                tokenId = "token-id",
                permissionsFromToken = emptyList(),
                currentAccountId = UUID.randomUUID(),
            ),
        )
        whenever(revocationStore.isRevoked(any(), any(), any())).thenReturn(false)

        service =
            McpAuthenticationService(
                jwtTokenValidator = jwtTokenValidator,
                principalDecoder = principalDecoder,
                revocationStore = revocationStore,
                resourceConfig =
                    McpResourceConfig(
                        audience = "https://mcp.example.test",
                        metadataUrl = "https://mcp.example.test/.well-known/oauth-protected-resource",
                    ),
            )
    }

    @Test
    fun `expired validator failure maps to expired token internal cause while preserving invalid_token wire code`() {
        whenever(jwtTokenValidator.validateToken(any())).thenThrow(
            AuthenticationRequiredException(
                "Token validation failed: expired",
                AuthenticationFailureCause.TOKEN_EXPIRED,
            ),
        )

        val exception = assertThrows<McpAuthenticationException> { service.authenticate(request()) }

        assertThat(exception.error).isEqualTo("invalid_token")
        assertThat(exception.failureCause).isEqualTo(McpAuthenticationFailureCause.EXPIRED_TOKEN)
    }

    @Test
    fun `invalid validator failure maps to invalid token internal cause while preserving invalid_token wire code`() {
        whenever(jwtTokenValidator.validateToken(any())).thenThrow(
            AuthenticationRequiredException(
                "Token validation failed: bad signature",
                AuthenticationFailureCause.TOKEN_INVALID_SIGNATURE,
            ),
        )

        val exception = assertThrows<McpAuthenticationException> { service.authenticate(request()) }

        assertThat(exception.error).isEqualTo("invalid_token")
        assertThat(exception.failureCause).isEqualTo(McpAuthenticationFailureCause.INVALID_TOKEN)
    }

    @Test
    fun `missing current account maps to dedicated internal cause`() {
        whenever(jwtTokenValidator.validateToken(any())).thenReturn(
            claims(currentAccount = null),
        )

        val exception = assertThrows<McpAuthenticationException> { service.authenticate(request()) }

        assertThat(exception.error).isEqualTo("invalid_token")
        assertThat(exception.failureCause).isEqualTo(McpAuthenticationFailureCause.MISSING_CURRENT_ACCOUNT)
    }

    @Test
    fun `audience mismatch maps to dedicated internal cause`() {
        whenever(jwtTokenValidator.validateToken(any())).thenReturn(
            claims(audience = setOf("https://other.example.test")),
        )

        val exception = assertThrows<McpAuthenticationException> { service.authenticate(request()) }

        assertThat(exception.error).isEqualTo("invalid_token")
        assertThat(exception.failureCause).isEqualTo(McpAuthenticationFailureCause.AUDIENCE_MISMATCH)
    }

    @Test
    fun `non access token maps to invalid token type internal cause`() {
        whenever(jwtTokenValidator.validateToken(any())).thenReturn(
            claims(type = "refresh"),
        )

        val exception = assertThrows<McpAuthenticationException> { service.authenticate(request()) }

        assertThat(exception.error).isEqualTo("invalid_token")
        assertThat(exception.failureCause).isEqualTo(McpAuthenticationFailureCause.INVALID_TOKEN_TYPE)
    }

    @Test
    fun `missing client id maps to dedicated internal cause`() {
        whenever(jwtTokenValidator.validateToken(any())).thenReturn(
            claims(clientId = ""),
        )

        val exception = assertThrows<McpAuthenticationException> { service.authenticate(request()) }

        assertThat(exception.error).isEqualTo("invalid_token")
        assertThat(exception.failureCause).isEqualTo(McpAuthenticationFailureCause.MISSING_CLIENT_ID)
    }

    @Test
    fun `invalid subject maps to dedicated internal cause`() {
        whenever(jwtTokenValidator.validateToken(any())).thenReturn(
            claims(subject = "not-a-uuid"),
        )

        val exception = assertThrows<McpAuthenticationException> { service.authenticate(request()) }

        assertThat(exception.error).isEqualTo("invalid_token")
        assertThat(exception.failureCause).isEqualTo(McpAuthenticationFailureCause.INVALID_SUBJECT)
    }

    @Test
    fun `revoked connection maps to dedicated internal cause`() {
        whenever(jwtTokenValidator.validateToken(any())).thenReturn(claims())
        whenever(revocationStore.isRevoked(any(), any(), any())).thenReturn(true)

        val exception = assertThrows<McpAuthenticationException> { service.authenticate(request()) }

        assertThat(exception.error).isEqualTo("invalid_token")
        assertThat(exception.failureCause).isEqualTo(McpAuthenticationFailureCause.REVOKED_CONNECTION)
    }

    @Test
    fun `missing bearer token maps to invalid request wire code and dedicated internal cause`() {
        val exception = assertThrows<McpAuthenticationException> { service.authenticate(HttpRequest.GET<Any>("/mcp")) }

        assertThat(exception.error).isEqualTo("invalid_request")
        assertThat(exception.failureCause).isEqualTo(McpAuthenticationFailureCause.MISSING_BEARER_TOKEN)
    }

    private fun request(): HttpRequest<Any> =
        HttpRequest.GET<Any>("/mcp")
            .header(HttpHeaders.AUTHORIZATION, "Bearer token")

    private fun claims(
        type: String = "access",
        audience: Set<String> = setOf("https://mcp.example.test"),
        clientId: String = "test-agent",
        subject: String = UUID.randomUUID().toString(),
        currentAccount: String? = UUID.randomUUID().toString(),
    ): Claims =
        Jwts
            .claims()
            .subject(subject)
            .add("type", type)
            .add("client_id", clientId)
            .also { builder ->
                audience.forEach { builder.audience().add(it).and() }
                currentAccount?.let { builder.add("current_account", it) }
            }.build()
}
