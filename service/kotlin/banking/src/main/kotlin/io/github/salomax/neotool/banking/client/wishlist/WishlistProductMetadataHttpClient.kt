package io.github.salomax.neotool.banking.client.wishlist

import jakarta.inject.Singleton
import java.net.InetAddress
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

interface WishlistProductMetadataHttpClient {
    fun fetch(url: String): String?
}

@Singleton
open class DefaultWishlistProductMetadataHttpClient(
    private val httpClient: HttpClient =
        HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build(),
    private val hostAddressResolver: (String) -> List<InetAddress> = { host ->
        InetAddress.getAllByName(host).toList()
    },
) : WishlistProductMetadataHttpClient {
    private val maxRedirects = 3

    override fun fetch(url: String): String? {
        var currentUri = validateUrl(url)

        repeat(maxRedirects + 1) { redirectCount ->
            val request =
                HttpRequest.newBuilder()
                    .uri(currentUri)
                    .timeout(Duration.ofSeconds(8))
                    .header("Accept", "text/html,application/xhtml+xml")
                    .header(
                        "User-Agent",
                        "Mozilla/5.0 (compatible; InvistusWishlistMetadataBot/1.0; +https://invistus.com.br)",
                    )
                    .GET()
                    .build()
            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())

            if (response.statusCode() in 300..399) {
                if (redirectCount == maxRedirects) {
                    return null
                }

                val redirectLocation = response.headers().firstValue("Location").orElse("").trim()
                if (redirectLocation.isBlank()) {
                    return null
                }

                currentUri = validateUrl(currentUri.resolve(redirectLocation).toString())
                return@repeat
            }

            if (response.statusCode() !in 200..299) {
                return null
            }
            val contentType = response.headers().firstValue("Content-Type").orElse("").lowercase()
            if (contentType.isNotBlank() && !contentType.contains("html")) {
                return null
            }
            return response.body().takeIf { it.isNotBlank() }
        }

        return null
    }

    private fun validateUrl(url: String): URI {
        val uri = URI.create(url.trim())
        val scheme = uri.scheme?.lowercase()
        require(scheme == "http" || scheme == "https") { "unsupported scheme" }
        val host = uri.host?.trim()?.lowercase() ?: throw IllegalArgumentException("missing host")
        require(!isBlockedHost(host)) { "blocked host" }
        return uri
    }

    private fun isBlockedHost(host: String): Boolean {
        if (host == "localhost" || host.endsWith(".localhost") || host.endsWith(".local")) {
            return true
        }

        return runCatching { hostAddressResolver(host) }
            .getOrDefault(emptyList())
            .any { address ->
                address.isAnyLocalAddress ||
                    address.isLoopbackAddress ||
                    address.isLinkLocalAddress ||
                    address.isSiteLocalAddress ||
                    address.isMulticastAddress
            }
    }
}
