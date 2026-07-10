package io.github.salomax.neotool.banking.service.wishlist

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import io.github.salomax.neotool.banking.client.wishlist.WishlistProductMetadataHttpClient
import jakarta.inject.Singleton
import mu.KotlinLogging
import java.math.BigDecimal
import java.net.URI

data class WishlistProductMetadataPreview(
    val name: String? = null,
    val estimatedPrice: Double? = null,
    val currencyCode: String? = null,
    val imageUrl: String? = null,
)

@Singleton
open class WishlistProductMetadataPreviewService(
    private val httpClient: WishlistProductMetadataHttpClient,
    private val objectMapper: ObjectMapper,
) {
    private val logger = KotlinLogging.logger {}

    open fun preview(url: String): WishlistProductMetadataPreview? =
        runCatching {
            val normalizedUrl = url.trim()
            if (normalizedUrl.isBlank()) {
                return null
            }

            val html = httpClient.fetch(normalizedUrl) ?: return null
            parseSchemaOrg(html, normalizedUrl) ?: parseOpenGraph(html, normalizedUrl)
        }.onFailure { error ->
            logger.debug(error) { "Wishlist product metadata preview failed for url=$url" }
        }.getOrNull()

    internal fun parseSchemaOrg(
        html: String,
        pageUrl: String,
    ): WishlistProductMetadataPreview? {
        val scriptRegex =
            Regex(
                """<script[^>]*type\s*=\s*['"]application/ld\+json['"][^>]*>(.*?)</script>""",
                setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
            )

        scriptRegex.findAll(html).forEach { match ->
            val payload = match.groupValues[1].trim()
            if (payload.isBlank()) {
                return@forEach
            }

            val root = runCatching { objectMapper.readTree(payload) }.getOrNull() ?: return@forEach
            findProductNodes(root).forEach { node ->
                val preview = node.toSchemaOrgPreview(pageUrl)
                if (preview.hasContent()) {
                    return preview
                }
            }
        }

        return null
    }

    internal fun parseOpenGraph(
        html: String,
        pageUrl: String,
    ): WishlistProductMetadataPreview? {
        val metaTagRegex = Regex("""<meta\b[^>]*>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        val attrRegex = Regex("""([a-zA-Z:_-]+)\s*=\s*(['"])(.*?)\2""", RegexOption.DOT_MATCHES_ALL)
        val metaByKey = linkedMapOf<String, String>()

        metaTagRegex.findAll(html).forEach { match ->
            val attrs =
                attrRegex.findAll(match.value).associate {
                    it.groupValues[1].lowercase() to it.groupValues[3].trim()
                }
            val key = attrs["property"] ?: attrs["name"] ?: return@forEach
            val content = attrs["content"]?.takeIf { it.isNotBlank() } ?: return@forEach
            metaByKey.putIfAbsent(key.lowercase(), content)
        }

        val preview =
            WishlistProductMetadataPreview(
                name = metaByKey["og:title"],
                estimatedPrice = parsePrice(metaByKey["product:price:amount"]),
                currencyCode = metaByKey["product:price:currency"]?.uppercase(),
                imageUrl = resolveUrl(pageUrl, metaByKey["og:image"]),
            )

        return preview.takeIf { it.hasContent() }
    }

    private fun findProductNodes(node: JsonNode): List<JsonNode> =
        when {
            node.isArray -> node.flatMap(::findProductNodes)
            node.isObject -> {
                val nested =
                    buildList {
                        node.get("@graph")?.let { addAll(findProductNodes(it)) }
                        node.get("mainEntity")?.let { addAll(findProductNodes(it)) }
                        node.get("itemListElement")?.let { addAll(findProductNodes(it)) }
                    }
                if (node.isProductNode()) {
                    listOf(node) + nested
                } else {
                    nested
                }
            }
            else -> emptyList()
        }

    private fun JsonNode.isProductNode(): Boolean {
        val typeNode = get("@type") ?: return false
        return when {
            typeNode.isTextual -> typeNode.asText().equals("Product", ignoreCase = true)
            typeNode.isArray -> typeNode.any { it.asText().equals("Product", ignoreCase = true) }
            else -> false
        }
    }

    private fun JsonNode.toSchemaOrgPreview(pageUrl: String): WishlistProductMetadataPreview {
        val offerNode = get("offers").firstMeaningfulNode()
        return WishlistProductMetadataPreview(
            name = get("name")?.asTrimmedText(),
            estimatedPrice = parsePrice(offerNode?.get("price")?.asTrimmedText()),
            currencyCode = offerNode?.get("priceCurrency")?.asTrimmedText()?.uppercase(),
            imageUrl = resolveUrl(pageUrl, get("image").firstTextValue()),
        )
    }

    private fun JsonNode?.firstMeaningfulNode(): JsonNode? =
        when {
            this == null || isNull -> null
            isArray -> firstOrNull()
            else -> this
        }

    private fun JsonNode?.firstTextValue(): String? =
        when {
            this == null || isNull -> null
            isTextual -> asTrimmedText()
            isArray -> firstNotNullOfOrNull { it.asTrimmedText() }
            isObject -> get("url")?.asTrimmedText()
            else -> null
        }

    private fun JsonNode.asTrimmedText(): String? = asText().trim().takeIf { it.isNotBlank() }

    private fun parsePrice(raw: String?): Double? {
        val normalized = raw?.trim()?.replace("""[^\d,.-]""".toRegex(), "")?.replace(",", ".") ?: return null
        return runCatching { BigDecimal(normalized).toDouble() }.getOrNull()
    }

    private fun resolveUrl(
        pageUrl: String,
        value: String?,
    ): String? {
        val raw = value?.trim()?.takeIf { it.isNotBlank() } ?: return null
        return runCatching { URI.create(pageUrl).resolve(raw).toString() }.getOrDefault(raw)
    }

    private fun WishlistProductMetadataPreview.hasContent(): Boolean =
        !name.isNullOrBlank() || estimatedPrice != null || !imageUrl.isNullOrBlank()
}
