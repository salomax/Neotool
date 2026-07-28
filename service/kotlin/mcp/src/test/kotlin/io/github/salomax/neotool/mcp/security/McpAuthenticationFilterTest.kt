package io.github.salomax.neotool.mcp.security

import io.github.salomax.neotool.mcp.observability.McpObservabilityLogger
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.MutableHttpResponse
import io.micronaut.http.filter.ServerFilterChain
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.isNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.reactivestreams.Publisher
import reactor.core.publisher.Flux

class McpAuthenticationFilterTest {
    @Test
    fun `mcp prefixed protected resource metadata path is no longer public`() {
        val authenticationService = mock<McpAuthenticationService>()
        val errorResponseFactory =
            OAuthErrorResponseFactory(
                McpResourceConfig(
                    audience = "https://mcp.example.test",
                    metadataUrl = "https://mcp.example.test/.well-known/oauth-protected-resource",
                ),
            )
        val observabilityLogger = mock<McpObservabilityLogger>()
        val filter = McpAuthenticationFilter(authenticationService, errorResponseFactory, observabilityLogger)
        val chain = RecordingServerFilterChain()

        whenever(authenticationService.authenticate(any(), isNull()))
            .thenThrow(
                McpAuthenticationException(
                    "invalid_request",
                    "Bearer token is required",
                    McpAuthenticationFailureCause.MISSING_BEARER_TOKEN,
                ),
            )

        val response =
            Flux
                .from(filter.doFilter(HttpRequest.GET<Any>("/mcp/.well-known/oauth-protected-resource"), chain))
                .blockFirst()

        assertThat(response?.status).isEqualTo(HttpStatus.UNAUTHORIZED)
        assertThat(chain.proceeded).isFalse()
        verify(authenticationService).authenticate(any(), isNull())
        verify(observabilityLogger)
            .recordAuthenticationFailure(
                "other",
                McpAuthenticationFailureCause.MISSING_BEARER_TOKEN,
                "invalid_request",
            )
    }

    @Test
    fun `unrouted mcp paths collapse to other authentication endpoint tag`() {
        val authenticationService = mock<McpAuthenticationService>()
        val errorResponseFactory =
            OAuthErrorResponseFactory(
                McpResourceConfig(
                    audience = "https://mcp.example.test",
                    metadataUrl = "https://mcp.example.test/.well-known/oauth-protected-resource",
                ),
            )
        val observabilityLogger = mock<McpObservabilityLogger>()
        val filter = McpAuthenticationFilter(authenticationService, errorResponseFactory, observabilityLogger)
        val chain = RecordingServerFilterChain()

        whenever(authenticationService.authenticate(any(), isNull()))
            .thenThrow(
                McpAuthenticationException(
                    "invalid_request",
                    "Bearer token is required",
                    McpAuthenticationFailureCause.MISSING_BEARER_TOKEN,
                ),
            )

        val response =
            Flux
                .from(filter.doFilter(HttpRequest.GET<Any>("/mcp/zz-attacker-chosen-9f3a"), chain))
                .blockFirst()

        assertThat(response?.status).isEqualTo(HttpStatus.UNAUTHORIZED)
        assertThat(chain.proceeded).isFalse()
        verify(observabilityLogger)
            .recordAuthenticationFailure(
                "other",
                McpAuthenticationFailureCause.MISSING_BEARER_TOKEN,
                "invalid_request",
            )
    }

    private class RecordingServerFilterChain : ServerFilterChain {
        var proceeded: Boolean = false

        override fun proceed(request: HttpRequest<*>): Publisher<MutableHttpResponse<*>> {
            proceeded = true
            return Flux.empty()
        }
    }
}
