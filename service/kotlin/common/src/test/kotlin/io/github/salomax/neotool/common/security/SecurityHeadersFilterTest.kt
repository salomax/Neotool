package io.github.salomax.neotool.common.security

import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.MutableHttpHeaders
import io.micronaut.http.MutableHttpResponse
import io.micronaut.http.filter.ServerFilterChain
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.reactivestreams.Publisher
import org.reactivestreams.Subscriber
import org.reactivestreams.Subscription
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

@DisplayName("SecurityHeadersFilter Unit Tests")
class SecurityHeadersFilterTest {
    private lateinit var filter: SecurityHeadersFilter
    private lateinit var chain: ServerFilterChain
    private lateinit var request: HttpRequest<*>
    private lateinit var response: MutableHttpResponse<*>
    private lateinit var headers: MutableHttpHeaders

    private val customCspPolicy = "default-src 'self'; script-src 'self' 'unsafe-inline'"

    @BeforeEach
    fun setUp() {
        chain = mock()
        request = mock()
        response = mock()
        headers = mock()

        whenever(response.headers).thenReturn(headers)
        whenever(response.status).thenReturn(HttpStatus.OK)
    }

    @Nested
    @DisplayName("Header Addition")
    inner class HeaderAdditionTests {
        @Test
        fun `should add X-Frame-Options header`() {
            // Arrange
            filter = SecurityHeadersFilter(null)
            setupFilterChain()

            // Act
            executeFilter()

            // Assert
            verify(headers).add("X-Frame-Options", "DENY")
        }

        @Test
        fun `should add X-Content-Type-Options header`() {
            // Arrange
            filter = SecurityHeadersFilter(null)
            setupFilterChain()

            // Act
            executeFilter()

            // Assert
            verify(headers).add("X-Content-Type-Options", "nosniff")
        }

        @Test
        fun `should add X-XSS-Protection header`() {
            // Arrange
            filter = SecurityHeadersFilter(null)
            setupFilterChain()

            // Act
            executeFilter()

            // Assert
            verify(headers).add("X-XSS-Protection", "1; mode=block")
        }

        @Test
        fun `should add Referrer-Policy header`() {
            // Arrange
            filter = SecurityHeadersFilter(null)
            setupFilterChain()

            // Act
            executeFilter()

            // Assert
            verify(headers).add("Referrer-Policy", "strict-origin-when-cross-origin")
        }

        @Test
        fun `should add Permissions-Policy header`() {
            // Arrange
            filter = SecurityHeadersFilter(null)
            setupFilterChain()

            // Act
            executeFilter()

            // Assert
            val captor = argumentCaptor<String>()
            verify(headers).add(org.mockito.kotlin.eq("Permissions-Policy"), captor.capture())

            val policy = captor.firstValue
            assertThat(policy).contains("geolocation=()")
            assertThat(policy).contains("microphone=()")
            assertThat(policy).contains("camera=()")
        }

        @Test
        fun `should add default CSP header when not configured`() {
            // Arrange
            filter = SecurityHeadersFilter(null)
            setupFilterChain()

            // Act
            executeFilter()

            // Assert
            val captor = argumentCaptor<String>()
            verify(headers).add(org.mockito.kotlin.eq("Content-Security-Policy"), captor.capture())

            val csp = captor.firstValue
            assertThat(csp).contains("default-src 'self'")
            assertThat(csp).contains("script-src 'self'")
            assertThat(csp).contains("object-src 'none'")
        }

        @Test
        fun `should add custom CSP header when configured`() {
            // Arrange
            filter = SecurityHeadersFilter(customCspPolicy)
            setupFilterChain()

            // Act
            executeFilter()

            // Assert
            verify(headers).add("Content-Security-Policy", customCspPolicy)
        }
    }

    // HSTS header is tested in integration tests
    // Unit testing reactive filters with mock headers is complex
    // Integration tests provide better coverage for header behavior

    @Nested
    @DisplayName("Excluded Paths")
    inner class ExcludedPathsTests {
        @Test
        fun `should skip security headers for health endpoint`() {
            // Arrange
            filter = SecurityHeadersFilter(null)
            whenever(request.path).thenReturn("/health")
            val publisher: Publisher<MutableHttpResponse<*>> = TestPublisher<MutableHttpResponse<*>>(null)
            whenever(chain.proceed(request)).thenReturn(publisher)

            // Act
            filter.doFilter(request, chain)

            // Assert
            verify(headers, never()).add(any<String>(), any<String>())
        }

        @Test
        fun `should skip security headers for metrics endpoint`() {
            // Arrange
            filter = SecurityHeadersFilter(null)
            whenever(request.path).thenReturn("/metrics")
            val publisher: Publisher<MutableHttpResponse<*>> = TestPublisher<MutableHttpResponse<*>>(null)
            whenever(chain.proceed(request)).thenReturn(publisher)

            // Act
            filter.doFilter(request, chain)

            // Assert
            verify(headers, never()).add(any<String>(), any<String>())
        }

        @Test
        fun `should skip security headers for prometheus endpoint`() {
            // Arrange
            filter = SecurityHeadersFilter(null)
            whenever(request.path).thenReturn("/prometheus")
            val publisher: Publisher<MutableHttpResponse<*>> = TestPublisher<MutableHttpResponse<*>>(null)
            whenever(chain.proceed(request)).thenReturn(publisher)

            // Act
            filter.doFilter(request, chain)

            // Assert
            verify(headers, never()).add(any<String>(), any<String>())
        }

        @Test
        fun `should skip security headers for actuator health endpoint`() {
            // Arrange
            filter = SecurityHeadersFilter(null)
            whenever(request.path).thenReturn("/actuator/health")
            val publisher: Publisher<MutableHttpResponse<*>> = TestPublisher<MutableHttpResponse<*>>(null)
            whenever(chain.proceed(request)).thenReturn(publisher)

            // Act
            filter.doFilter(request, chain)

            // Assert
            verify(headers, never()).add(any<String>(), any<String>())
        }

        @Test
        fun `should skip security headers for all actuator endpoints`() {
            // Arrange
            filter = SecurityHeadersFilter(null)
            whenever(request.path).thenReturn("/actuator/prometheus")
            val publisher: Publisher<MutableHttpResponse<*>> = TestPublisher<MutableHttpResponse<*>>(null)
            whenever(chain.proceed(request)).thenReturn(publisher)

            // Act
            filter.doFilter(request, chain)

            // Assert
            verify(headers, never()).add(any<String>(), any<String>())
        }

        @Test
        fun `should apply security headers for regular endpoints`() {
            // Arrange
            filter = SecurityHeadersFilter(null)
            whenever(request.path).thenReturn("/api/users")
            setupFilterChain()

            // Act
            executeFilter()

            // Assert
            verify(headers).add("X-Frame-Options", "DENY")
            verify(headers).add("X-Content-Type-Options", "nosniff")
        }
    }

    /**
     * Setup filter chain with test publisher that emits the mock response.
     */
    private fun setupFilterChain() {
        whenever(request.path).thenReturn("/api/test")
        whenever(request.isSecure).thenReturn(false)
        val publisher: Publisher<MutableHttpResponse<*>> = TestPublisher<MutableHttpResponse<*>>(response)
        whenever(chain.proceed(request)).thenReturn(publisher)
    }

    /**
     * Execute filter and wait for response.
     */
    private fun executeFilter() {
        val latch = CountDownLatch(1)
        val resultRef = AtomicReference<MutableHttpResponse<*>>()

        val publisher = filter.doFilter(request, chain)
        publisher.subscribe(
            object : Subscriber<MutableHttpResponse<*>> {
                override fun onSubscribe(s: Subscription) {
                    s.request(1)
                }

                override fun onNext(t: MutableHttpResponse<*>) {
                    resultRef.set(t)
                }

                override fun onError(t: Throwable) {
                    latch.countDown()
                }

                override fun onComplete() {
                    latch.countDown()
                }
            },
        )

        assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue()
        assertThat(resultRef.get()).isNotNull
    }

    /**
     * Test publisher that emits a single response.
     */
    private class TestPublisher<T>(
        private val response: T?,
    ) : Publisher<T> {
        override fun subscribe(subscriber: Subscriber<in T>) {
            subscriber.onSubscribe(
                object : Subscription {
                    override fun request(n: Long) {
                        if (response != null) {
                            subscriber.onNext(response)
                        }
                        subscriber.onComplete()
                    }

                    override fun cancel() {
                        // No-op
                    }
                },
            )
        }
    }
}
