package io.github.salomax.neotool.mcp.security

import io.micronaut.http.HttpRequest
import io.micronaut.http.MutableHttpResponse
import io.micronaut.http.annotation.Filter
import io.micronaut.http.filter.HttpServerFilter
import io.micronaut.http.filter.ServerFilterChain
import jakarta.inject.Singleton
import org.reactivestreams.Publisher
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers

@Filter(patterns = ["/mcp", "/mcp/**"])
@Singleton
class McpAuthenticationFilter(
    private val authenticationService: McpAuthenticationService,
    private val errorResponseFactory: OAuthErrorResponseFactory,
) : HttpServerFilter {
    override fun doFilter(
        request: HttpRequest<*>,
        chain: ServerFilterChain,
    ): Publisher<MutableHttpResponse<*>> {
        // POST /mcp authenticates in the controller so rejected tools/call requests can
        // audit the requested tool name before the protocol service is reached.
        if (request.path == "/mcp" || isPublicMcpEndpoint(request.path)) {
            return chain.proceed(request)
        }

        return Mono
            .fromCallable {
                val principal = authenticationService.authenticate(request, requiredScope = requiredScope(request.path))
                request.setAttribute(PRINCIPAL_ATTRIBUTE, principal)
                request
            }.subscribeOn(Schedulers.boundedElastic())
            .flatMapMany { authenticatedRequest -> chain.proceed(authenticatedRequest) }
            .onErrorResume { error ->
                when (error) {
                    is McpAuthenticationException -> Flux.just(errorResponseFactory.unauthorized(error))
                    is McpInsufficientScopeException -> Flux.just(errorResponseFactory.forbidden(error))
                    else -> Flux.error(error)
                }
            }
    }

    private fun isPublicMcpEndpoint(path: String): Boolean =
        path == "/mcp/ready" ||
            path == "/mcp/.well-known/oauth-protected-resource" ||
            path == "/.well-known/oauth-protected-resource"

    private fun requiredScope(path: String): String? =
        when (path) {
            "/mcp/probe" -> "mcp:tools:call"
            else -> null
        }

    companion object {
        const val PRINCIPAL_ATTRIBUTE = "io.github.salomax.neotool.mcp.principal"
    }
}
