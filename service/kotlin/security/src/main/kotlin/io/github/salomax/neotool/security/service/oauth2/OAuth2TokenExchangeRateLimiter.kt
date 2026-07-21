package io.github.salomax.neotool.security.service.oauth2

import io.micronaut.context.annotation.Value
import jakarta.inject.Singleton
import java.time.Clock
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

interface OAuth2TokenExchangeRateLimiter {
    fun check(serviceId: String)
}

@Singleton
class InMemoryOAuth2TokenExchangeRateLimiter(
    @param:Value("\${oauth.authorization-server.token-exchange.rate-limit-per-minute:60}")
    private val maxRequestsPerMinute: Int,
) : OAuth2TokenExchangeRateLimiter {
    private val clock: Clock = Clock.systemUTC()
    private val buckets = ConcurrentHashMap<String, MutableList<Instant>>()

    override fun check(serviceId: String) {
        if (maxRequestsPerMinute <= 0) {
            return
        }

        val now = Instant.now(clock)
        val cutoff = now.minusSeconds(60)
        val bucket = buckets.computeIfAbsent(serviceId) { mutableListOf() }
        synchronized(bucket) {
            bucket.removeIf { it.isBefore(cutoff) }
            if (bucket.size >= maxRequestsPerMinute) {
                throw OAuth2RateLimitException("Token exchange rate limit exceeded")
            }
            bucket.add(now)
        }
    }
}
