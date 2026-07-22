package io.github.salomax.neotool.mcp.security

import io.micronaut.http.HttpRequest
import io.micronaut.http.MutableHttpResponse
import io.micronaut.http.annotation.Filter
import io.micronaut.http.filter.HttpServerFilter
import io.micronaut.http.filter.ServerFilterChain
import jakarta.inject.Singleton
import org.reactivestreams.Publisher
import org.reactivestreams.Subscriber
import org.reactivestreams.Subscription

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
        if (isPublicMcpEndpoint(request.path)) {
            return chain.proceed(request)
        }

        return try {
            val principal = authenticationService.authenticate(request, requiredScope = requiredScope(request.path))
            request.setAttribute(PRINCIPAL_ATTRIBUTE, principal)
            chain.proceed(request)
        } catch (e: McpAuthenticationException) {
            SingleResponsePublisher(errorResponseFactory.unauthorized(e))
        } catch (e: McpInsufficientScopeException) {
            SingleResponsePublisher(errorResponseFactory.forbidden(e))
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

    private class SingleResponsePublisher(
        private val response: MutableHttpResponse<*>,
    ) : Publisher<MutableHttpResponse<*>> {
        override fun subscribe(subscriber: Subscriber<in MutableHttpResponse<*>>) {
            subscriber.onSubscribe(
                object : Subscription {
                    private var completed = false

                    override fun request(n: Long) {
                        if (!completed && n > 0) {
                            completed = true
                            subscriber.onNext(response)
                            subscriber.onComplete()
                        }
                    }

                    override fun cancel() {
                        completed = true
                    }
                },
            )
        }
    }

    companion object {
        const val PRINCIPAL_ATTRIBUTE = "io.github.salomax.neotool.mcp.principal"
    }
}
