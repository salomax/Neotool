package io.github.salomax.neotool.financialdata.rest.internal

import io.github.salomax.neotool.common.security.exception.AuthenticationRequiredException
import io.github.salomax.neotool.common.security.principal.PrincipalType
import io.github.salomax.neotool.common.security.principal.RequestPrincipalProvider
import io.micronaut.http.HttpHeaders
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.MutableHttpResponse
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Produces
import io.micronaut.http.annotation.QueryValue
import io.micronaut.http.exceptions.HttpStatusException
import io.micronaut.http.server.types.files.StreamedFile
import jakarta.inject.Singleton
import mu.KotlinLogging
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.time.Instant
import java.time.LocalDate

private const val NDJSON_MEDIA_TYPE = "application/x-ndjson"

private fun extractBearerToken(request: HttpRequest<*>): String? {
    val auth = request.headers.get(HttpHeaders.AUTHORIZATION) ?: return null
    if (!auth.startsWith("Bearer ", ignoreCase = true)) return null
    return auth.substring(7).trim().takeIf { it.isNotBlank() }
}

@Controller("/internal/market-data/fund-navs")
@Singleton
open class FundNavExportController(
    private val exportService: FundNavExportService,
    private val requestPrincipalProvider: RequestPrincipalProvider,
) {
    private val logger = KotlinLogging.logger {}

    @Produces(NDJSON_MEDIA_TYPE)
    @Get("/export")
    open fun export(
        @QueryValue from: LocalDate,
        @QueryValue to: LocalDate,
        @QueryValue updatedSince: Instant?,
        @QueryValue cnpjs: List<String>?,
        @QueryValue after: String?,
        request: HttpRequest<*>,
    ): MutableHttpResponse<StreamedFile> {
        val token = extractBearerToken(request) ?: throw AuthenticationRequiredException("Authentication required")
        val principal = requestPrincipalProvider.fromToken(token)
        if (principal.principalType != PrincipalType.SERVICE) {
            throw HttpStatusException(HttpStatus.FORBIDDEN, "Service token required")
        }

        val gzipEnabled = request.headers.get(HttpHeaders.ACCEPT_ENCODING)?.contains("gzip", ignoreCase = true) == true
        val input = PipedInputStream(64 * 1024)
        val output = PipedOutputStream(input)
        Thread.ofVirtual().name("fund-nav-export").start {
            try {
                output.use {
                    exportService.writeNdjson(
                        outputStream = it,
                        from = from,
                        to = to,
                        updatedSince = updatedSince,
                        cnpjs = cnpjs,
                        after = after,
                        gzipEnabled = gzipEnabled,
                    )
                }
            } catch (e: Exception) {
                logger.error(e) {
                    "Fund NAV export failed: from=$from to=$to cnpjs=${cnpjs?.size ?: 0} after=${after != null} gzip=$gzipEnabled"
                }
            }
        }
        val body = StreamedFile(input, MediaType.of(NDJSON_MEDIA_TYPE))

        return HttpResponse.ok(body)
            .apply {
                if (gzipEnabled) {
                    header(HttpHeaders.CONTENT_ENCODING, "gzip")
                }
            }
    }
}
