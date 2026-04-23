package io.github.salomax.neotool.banking.service.import.nfce

import io.github.salomax.neotool.banking.client.import.nfce.NfceInvalidException
import io.github.salomax.neotool.banking.client.import.nfce.NfceUnavailableException
import io.github.salomax.neotool.banking.client.import.nfce.sanitizeNfceUrl
import io.github.salomax.neotool.banking.domain.import.nfce.NfceData
import jakarta.inject.Singleton
import java.net.CookieManager
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

private val CHNFE_PATTERN = Regex("""\d{44}""")

private const val USER_AGENT =
    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
private const val ACCEPT =
    "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8"

/**
 * Fetches NFC-e data from São Paulo SEFAZ portal (nfce.fazenda.sp.gov.br).
 * Uses a 2-step HTTP flow:
 * 1. GET summary page → extract cookies + ASP.NET ViewState
 * 2. POST "Visualizar em Abas" postback → parse detailed HTML with NCM, EAN, etc.
 */
@Singleton
open class SpNfceFetcher : NfceFetcher {
    override fun supportedUfCodes(): Set<String> = setOf("35") // SP

    private val httpClient =
        HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .cookieHandler(CookieManager())
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build()

    override fun fetch(qrCodeUrl: String): NfceData {
        val chNFeFromUrl = CHNFE_PATTERN.find(qrCodeUrl)?.value

        // Step 1: GET summary page
        val summaryHtml = fetchSummaryPage(qrCodeUrl)

        // Step 2: POST "Visualizar em Abas" postback
        val detailedHtml = fetchDetailedPage(summaryHtml, qrCodeUrl)

        return SpNfceHtmlParser.parse(detailedHtml, chNFeFromUrl)
    }

    internal fun fetchSummaryPage(qrCodeUrl: String): String {
        val summaryPageUri =
            try {
                toSpSummaryPageUri(qrCodeUrl)
            } catch (e: IllegalArgumentException) {
                throw NfceUnavailableException("Invalid NFC-e summary URL: ${e.message}", e)
            }
        val scheme = summaryPageUri.scheme?.lowercase()
        if (scheme != "http" && scheme != "https") {
            throw NfceUnavailableException("Invalid NFC-e summary URL: unsupported or missing HTTP scheme")
        }
        val request = HttpRequest.newBuilder()
            .uri(summaryPageUri)
            .timeout(Duration.ofSeconds(10))
            .header("User-Agent", USER_AGENT)
            .header("Accept", ACCEPT)
            .header("Accept-Language", "pt-BR,pt;q=0.9,en-US;q=0.8")
            .GET()
            .build()

        val response = try {
            httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        } catch (e: Exception) {
            throw NfceUnavailableException("SP NFC-e fetch failed: ${e.message}", e)
        }

        if (response.statusCode() != 200) {
            throw NfceUnavailableException("SP NFC-e returned HTTP ${response.statusCode()}")
        }

        val body = response.body()
        if (body.isBlank()) {
            throw NfceInvalidException("SP NFC-e response body is empty")
        }

        return body
    }

    internal fun fetchDetailedPage(summaryHtml: String, originalUrl: String): String {
        val viewState = extractHiddenField(summaryHtml, "__VIEWSTATE")
            ?: throw NfceInvalidException("SP HTML: __VIEWSTATE not found")
        val viewStateGenerator = extractHiddenField(summaryHtml, "__VIEWSTATEGENERATOR") ?: ""
        val eventValidation = extractHiddenField(summaryHtml, "__EVENTVALIDATION") ?: ""

        // Derive the form action URL from the original URL
        val baseUri = toSpSummaryPageUri(originalUrl)
        val formAction = extractFormAction(summaryHtml)
        val postUri = if (formAction != null) {
            baseUri.resolve(formAction)
        } else {
            baseUri
        }

        val formData = buildFormData(
            "__EVENTTARGET" to "btnVisualizarAbas",
            "__EVENTARGUMENT" to "",
            "__VIEWSTATE" to viewState,
            "__VIEWSTATEGENERATOR" to viewStateGenerator,
            "__EVENTVALIDATION" to eventValidation,
        )

        val request = HttpRequest.newBuilder()
            .uri(postUri)
            .timeout(Duration.ofSeconds(15))
            .header("User-Agent", USER_AGENT)
            .header("Accept", ACCEPT)
            .header("Accept-Language", "pt-BR,pt;q=0.9,en-US;q=0.8")
            .header("Content-Type", "application/x-www-form-urlencoded")
            .header("Origin", "${baseUri.scheme}://${baseUri.host}")
            .header("Referer", baseUri.toString())
            .POST(HttpRequest.BodyPublishers.ofString(formData))
            .build()

        val response = try {
            httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        } catch (e: Exception) {
            throw NfceUnavailableException("SP NFC-e postback failed: ${e.message}", e)
        }

        if (response.statusCode() != 200) {
            throw NfceUnavailableException("SP NFC-e postback returned HTTP ${response.statusCode()}")
        }

        val body = response.body()
        if (body.isBlank()) {
            throw NfceInvalidException("SP NFC-e postback response is empty")
        }

        return body
    }

    private fun extractHiddenField(html: String, fieldName: String): String? {
        val pattern = Regex("""id=['"]$fieldName['"]\s+value=['"]([^'"]*)['"]""", RegexOption.IGNORE_CASE)
        return pattern.find(html)?.groupValues?.get(1)
    }

    private fun extractFormAction(html: String): String? {
        val pattern = Regex("""<form[^>]+action=['"]([^'"]+)['"]""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        return pattern.find(html)?.groupValues?.get(1)
    }

    private fun toSpSummaryPageUri(originalUrl: String): URI {
        val sourceUri = URI.create(sanitizeNfceUrl(originalUrl))
        val canonicalPath = "/NFCeConsultaPublica/Paginas/ConsultaQRCode.aspx"
        val needsCanonicalPath = sourceUri.path.equals("/qrcode", ignoreCase = true)
        val path = if (needsCanonicalPath) canonicalPath else sourceUri.path
        val query = sourceUri.rawQuery?.let { "?$it" } ?: ""
        return URI("${sourceUri.scheme}://${sourceUri.authority}$path$query")
    }

    private fun buildFormData(vararg pairs: Pair<String, String>): String =
        pairs.joinToString("&") { (key, value) ->
            "${URLEncoder.encode(key, Charsets.UTF_8)}=${URLEncoder.encode(value, Charsets.UTF_8)}"
        }
}
