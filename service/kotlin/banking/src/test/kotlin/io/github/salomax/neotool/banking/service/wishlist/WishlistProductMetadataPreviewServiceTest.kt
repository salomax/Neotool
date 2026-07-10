package io.github.salomax.neotool.banking.service.wishlist

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.github.salomax.neotool.banking.client.wishlist.WishlistProductMetadataHttpClient
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class WishlistProductMetadataPreviewServiceTest {
    private val httpClient: WishlistProductMetadataHttpClient = mock()
    private val service = WishlistProductMetadataPreviewService(httpClient, jacksonObjectMapper())

    @Test
    fun `extracts product metadata from schema org product`() {
        whenever(httpClient.fetch("https://shop.example.com/product")).thenReturn(
            """
            <html>
              <head>
                <script type="application/ld+json">
                  {
                    "@context": "https://schema.org",
                    "@type": "Product",
                    "name": "Nintendo Switch 2",
                    "image": ["/images/switch-2.png"],
                    "offers": {
                      "@type": "Offer",
                      "price": "4999.90",
                      "priceCurrency": "BRL"
                    }
                  }
                </script>
              </head>
            </html>
            """.trimIndent(),
        )

        val preview = service.preview("https://shop.example.com/product")

        assertThat(preview).isEqualTo(
            WishlistProductMetadataPreview(
                name = "Nintendo Switch 2",
                estimatedPrice = 4999.90,
                currencyCode = "BRL",
                imageUrl = "https://shop.example.com/images/switch-2.png",
            ),
        )
    }

    @Test
    fun `falls back to open graph when schema org product is unavailable`() {
        whenever(httpClient.fetch("https://shop.example.com/service")).thenReturn(
            """
            <html>
              <head>
                <meta property="og:title" content="Spotify Premium" />
                <meta property="og:image" content="https://cdn.example.com/spotify.png" />
                <meta property="product:price:amount" content="27.90" />
                <meta property="product:price:currency" content="BRL" />
              </head>
            </html>
            """.trimIndent(),
        )

        val preview = service.preview("https://shop.example.com/service")

        assertThat(preview).isEqualTo(
            WishlistProductMetadataPreview(
                name = "Spotify Premium",
                estimatedPrice = 27.90,
                currencyCode = "BRL",
                imageUrl = "https://cdn.example.com/spotify.png",
            ),
        )
    }

    @Test
    fun `returns null when metadata does not contain supported product fields`() {
        whenever(httpClient.fetch("https://shop.example.com/empty")).thenReturn(
            """
            <html>
              <head>
                <meta property="og:site_name" content="Example" />
              </head>
            </html>
            """.trimIndent(),
        )

        assertThat(service.preview("https://shop.example.com/empty")).isNull()
    }
}
