package io.github.salomax.neotool.banking.client.import.nfce

import jakarta.inject.Singleton
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

@Singleton
open class DefaultNfceHttpFetcher : NfceHttpFetcher {
    private val httpClient =
        HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build()

    override fun fetch(url: String): NfceHttpResponse {
        val sanitizedUrl = sanitizeNfceUrl(url)
        val request =
            HttpRequest.newBuilder()
                .uri(URI.create(sanitizedUrl))
                .timeout(Duration.ofSeconds(5))
                .GET()
                .build()
        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        return NfceHttpResponse(statusCode = response.statusCode(), body = response.body())
    }
}
