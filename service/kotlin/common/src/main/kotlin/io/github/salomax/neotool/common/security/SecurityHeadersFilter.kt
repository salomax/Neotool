package io.github.salomax.neotool.common.security

import io.micronaut.core.annotation.Nullable
import io.micronaut.http.HttpRequest
import io.micronaut.http.MutableHttpResponse
import io.micronaut.http.annotation.Filter
import io.micronaut.http.filter.HttpServerFilter
import io.micronaut.http.filter.ServerFilterChain
import jakarta.inject.Singleton
import org.reactivestreams.Publisher
import org.reactivestreams.Subscriber
import org.reactivestreams.Subscription

/**
 * Security Headers HTTP Server Filter for adding security headers to all HTTP responses.
 *
 * This filter implements defense-in-depth security by adding standard security headers
 * to all HTTP responses, protecting against common web vulnerabilities:
 *
 * - **X-Frame-Options**: Prevents clickjacking attacks
 * - **X-Content-Type-Options**: Prevents MIME type sniffing
 * - **X-XSS-Protection**: Enables browser XSS protection
 * - **Content-Security-Policy**: Controls resource loading to prevent XSS
 * - **Referrer-Policy**: Controls referrer information leakage
 * - **Strict-Transport-Security**: Enforces HTTPS connections (HTTPS only)
 * - **Permissions-Policy**: Controls browser features and APIs
 *
 * The CSP policy can be configured via `security.headers.csp` in application.yml.
 * If not configured, a restrictive default policy is used.
 *
 * Security headers are excluded for internal/monitoring endpoints (health checks, metrics, etc.)
 * to optimize performance and avoid unnecessary overhead for service-to-service communication.
 *
 * @property cspPolicy The Content Security Policy string (configurable via application.yml)
 */
@Filter("/**")
@Singleton
class SecurityHeadersFilter(
    @Nullable
    @io.micronaut.context.annotation.Value("\${security.headers.csp:default-src 'self'; script-src 'self'; object-src 'none'; base-uri 'self'; form-action 'self'}")
    private val cspPolicy: String?,
) : HttpServerFilter {

    // Paths to exclude from security headers (internal/monitoring endpoints)
    private val excludedPaths =
        setOf(
            "/prometheus",
            "/metrics",
            "/health",
            "/actuator/health",
            "/actuator/prometheus",
            "/actuator/metrics",
        )

    override fun doFilter(
        request: HttpRequest<*>,
        chain: ServerFilterChain,
    ): Publisher<MutableHttpResponse<*>> {
        // Skip security headers for internal/monitoring endpoints
        if (shouldSkipSecurityHeaders(request)) {
            return chain.proceed(request)
        }
        return ResponseHeaderPublisher(chain.proceed(request), request)
    }

    private fun shouldSkipSecurityHeaders(request: HttpRequest<*>): Boolean {
        val path = request.path
        return excludedPaths.contains(path) || path.startsWith("/actuator/")
    }

    private inner class ResponseHeaderPublisher(
        private val source: Publisher<MutableHttpResponse<*>>,
        private val request: HttpRequest<*>,
    ) : Publisher<MutableHttpResponse<*>> {
        override fun subscribe(subscriber: Subscriber<in MutableHttpResponse<*>>) {
            source.subscribe(
                object : Subscriber<MutableHttpResponse<*>> {
                    override fun onSubscribe(s: Subscription) {
                        subscriber.onSubscribe(s)
                    }

                    override fun onNext(response: MutableHttpResponse<*>) {
                        subscriber.onNext(addSecurityHeaders(request, response))
                    }

                    override fun onError(t: Throwable) {
                        subscriber.onError(t)
                    }

                    override fun onComplete() {
                        subscriber.onComplete()
                    }
                },
            )
        }
    }

    private fun addSecurityHeaders(
        request: HttpRequest<*>,
        response: MutableHttpResponse<*>,
    ): MutableHttpResponse<*> {
        // Prevent clickjacking
        response.headers.add("X-Frame-Options", "DENY")

        // Prevent MIME sniffing
        response.headers.add("X-Content-Type-Options", "nosniff")

        // Enable XSS protection (legacy but still useful for older browsers)
        response.headers.add("X-XSS-Protection", "1; mode=block")

        // Content Security Policy
        val csp = cspPolicy ?: "default-src 'self'; script-src 'self'; object-src 'none'; base-uri 'self'; form-action 'self'"
        response.headers.add("Content-Security-Policy", csp)

        // Referrer policy
        response.headers.add("Referrer-Policy", "strict-origin-when-cross-origin")

        // HSTS for HTTPS only
        if (request.isSecure) {
            response.headers.add(
                "Strict-Transport-Security",
                "max-age=31536000; includeSubDomains; preload",
            )
        }

        // Permissions Policy (formerly Feature-Policy)
        response.headers.add(
            "Permissions-Policy",
            "geolocation=(), microphone=(), camera=(), payment=(), usb=(), magnetometer=(), gyroscope=(), accelerometer=()",
        )

        return response
    }
}

