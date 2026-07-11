package io.github.salomax.neotool.banking.client.wishlist

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.net.InetAddress
import java.net.http.HttpClient
import java.net.http.HttpHeaders
import java.net.http.HttpResponse

class WishlistProductMetadataHttpClientTest {
    private val httpClient: HttpClient = mock()

    @Test
    fun `follows safe redirects after revalidating the target host`() {
        val client =
            DefaultWishlistProductMetadataHttpClient(
                httpClient = httpClient,
                hostAddressResolver = { host ->
                    when (host) {
                        "shop.example.com" -> listOf(publicAddress(1, 1, 1, 1))
                        "cdn.example.com" -> listOf(publicAddress(8, 8, 8, 8))
                        else -> emptyList()
                    }
                },
            )
        val redirectResponse =
            response(
                statusCode = 302,
                headers = mapOf("Location" to "https://cdn.example.com/product"),
            )
        val successResponse =
            response(
                statusCode = 200,
                body = "<html>ok</html>",
                headers = mapOf("Content-Type" to "text/html"),
            )
        whenever(httpClient.send(any(), any<HttpResponse.BodyHandler<String>>()))
            .thenReturn(redirectResponse, successResponse)

        val body = client.fetch("https://shop.example.com/product")

        assertThat(body).isEqualTo("<html>ok</html>")
        verify(httpClient, times(2)).send(any(), any<HttpResponse.BodyHandler<String>>())
    }

    @Test
    fun `rejects redirects to blocked hosts`() {
        val client =
            DefaultWishlistProductMetadataHttpClient(
                httpClient = httpClient,
                hostAddressResolver = { host ->
                    when (host) {
                        "shop.example.com" -> listOf(publicAddress(1, 1, 1, 1))
                        "internal.example.com" -> listOf(loopbackAddress())
                        else -> emptyList()
                    }
                },
            )
        val redirectResponse =
            response(
                statusCode = 302,
                headers = mapOf("Location" to "https://internal.example.com/metadata"),
            )
        whenever(httpClient.send(any(), any<HttpResponse.BodyHandler<String>>()))
            .thenReturn(redirectResponse)

        val result =
            runCatching {
                client.fetch("https://shop.example.com/product")
            }

        assertThat(result.exceptionOrNull()).isInstanceOf(IllegalArgumentException::class.java)
        verify(httpClient, times(1)).send(any(), any<HttpResponse.BodyHandler<String>>())
    }

    private fun response(
        statusCode: Int,
        body: String = "",
        headers: Map<String, String> = emptyMap(),
    ): HttpResponse<String> {
        val response: HttpResponse<String> = mock()
        whenever(response.statusCode()).thenReturn(statusCode)
        whenever(response.body()).thenReturn(body)
        whenever(response.headers()).thenReturn(
            HttpHeaders.of(headers.mapValues { (_, value) -> listOf(value) }) { _, _ -> true },
        )
        return response
    }

    private fun publicAddress(
        a: Int,
        b: Int,
        c: Int,
        d: Int,
    ): InetAddress = InetAddress.getByAddress(byteArrayOf(a.toByte(), b.toByte(), c.toByte(), d.toByte()))

    private fun loopbackAddress(): InetAddress = InetAddress.getByAddress(byteArrayOf(127.toByte(), 0, 0, 1))
}
