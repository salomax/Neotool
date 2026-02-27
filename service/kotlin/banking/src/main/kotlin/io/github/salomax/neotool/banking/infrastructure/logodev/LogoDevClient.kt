package io.github.salomax.neotool.banking.infrastructure.logodev

import io.micronaut.context.annotation.Property
import io.micronaut.context.annotation.Requires
import jakarta.inject.Singleton
import mu.KotlinLogging
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * HTTP client for logo.dev — fetches institution logos by domain.
 *
 * API: GET https://img.logo.dev/{domain}?token={token}&size=128&format=png
 * Returns PNG bytes; throws on 4xx/5xx so the caller can log and continue.
 *
 * Disabled when [banking.logodev.token] is not configured (e.g. local dev without a token).
 */
@Singleton
@Requires(property = "banking.logodev.token")
open class LogoDevClient(
    @Property(name = "banking.logodev.token") private val token: String,
) {
    private val logger = KotlinLogging.logger {}
    private val httpClient: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build()

    fun fetch(domain: String): ByteArray {
        val url = "https://img.logo.dev/$domain?token=$token&size=128&format=png"
        logger.debug { "LogoDev: fetching $url" }

        val request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .timeout(Duration.ofSeconds(15))
            .GET()
            .build()

        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray())

        check(response.statusCode() in 200..299) {
            "LogoDev returned HTTP ${response.statusCode()} for domain=$domain"
        }

        val bytes = response.body()
        check(bytes.isNotEmpty()) { "LogoDev returned empty body for domain=$domain" }

        logger.debug { "LogoDev: fetched ${bytes.size} bytes for $domain" }
        return bytes
    }
}
