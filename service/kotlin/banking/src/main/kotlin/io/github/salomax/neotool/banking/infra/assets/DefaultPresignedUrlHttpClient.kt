package io.github.salomax.neotool.banking.infra.assets

import jakarta.inject.Singleton
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

@Singleton
open class DefaultPresignedUrlHttpClient : PresignedUrlHttpClient {
    private val httpClient =
        HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build()

    override fun put(
        url: String,
        bytes: ByteArray,
        contentType: String,
    ) {
        val request =
            HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(60))
                .header("Content-Type", contentType)
                .PUT(HttpRequest.BodyPublishers.ofByteArray(bytes))
                .build()
        val response = httpClient.send(request, HttpResponse.BodyHandlers.discarding())
        if (response.statusCode() !in 200..299) {
            throw AssetsServiceException.transferFailed(
                operation = "PUT",
                statusCode = response.statusCode(),
            )
        }
    }

    override fun get(url: String): ByteArray {
        val request =
            HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(60))
                .GET()
                .build()
        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray())
        if (response.statusCode() !in 200..299) {
            throw AssetsServiceException.transferFailed(
                operation = "GET",
                statusCode = response.statusCode(),
            )
        }
        return response.body()
    }
}
