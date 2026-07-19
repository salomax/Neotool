package io.github.salomax.neotool.security.http

import io.github.salomax.neotool.security.repo.UserRepository
import io.github.salomax.neotool.security.service.oauth2.OAuth2AuthorizationService
import io.github.salomax.neotool.security.service.oauth2.OAuth2InvalidRequestException
import io.github.salomax.neotool.security.service.oauth2.OAuth2SessionService
import io.github.salomax.neotool.security.service.oauth2.OAuth2TemplateRenderer
import io.micronaut.http.HttpHeaders
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Consumes
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Post
import io.micronaut.http.annotation.QueryValue
import io.micronaut.http.hateoas.JsonError
import jakarta.inject.Singleton
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.UUID

@Singleton
@Controller("/oauth2")
class OAuth2ConsentController(
    private val authorizationService: OAuth2AuthorizationService,
    private val sessionService: OAuth2SessionService,
    private val templateRenderer: OAuth2TemplateRenderer,
    private val userRepository: UserRepository,
) {
    @Get("/consent")
    fun consent(
        request: HttpRequest<*>,
        @QueryValue("request_id") requestId: UUID,
    ): HttpResponse<*> {
        val session =
            sessionService.parse(request.cookies[sessionService.cookieName()]?.value)
                ?: return HttpResponse.seeOther<Any>(URI.create("/oauth2/login?request_id=$requestId"))
        return try {
            val (authorizationRequest, csrfToken) = authorizationService.bindUserAndCsrf(requestId, session.userId)
            val user = userRepository.findById(session.userId).orElse(null)
            HttpResponse
                .ok(
                    templateRenderer.render(
                        "oauth2-consent",
                        request.headers.get(HttpHeaders.ACCEPT_LANGUAGE),
                        mapOf(
                            "requestId" to requestId.toString(),
                            "csrfToken" to csrfToken,
                            "clientName" to authorizationService.clientName(authorizationRequest.clientId),
                            "account" to (user?.email ?: session.userId.toString()),
                            "scopes" to authorizationService.describeScopes(authorizationRequest.requestedScopes),
                            "action" to "/oauth2/consent",
                        ),
                    ),
                ).contentType(MediaType.TEXT_HTML)
        } catch (e: OAuth2InvalidRequestException) {
            HttpResponse.status<JsonError>(HttpStatus.BAD_REQUEST).body(JsonError(e.message ?: "Invalid request"))
        }
    }

    @Post("/consent")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    fun approve(
        request: HttpRequest<*>,
        @Body body: String,
    ): HttpResponse<*> {
        val form = parseForm(body)
        val requestId =
            UUID.fromString(
                form.first("request_id") ?: throw OAuth2InvalidRequestException("request_id is required"),
            )
        val session =
            sessionService.parse(request.cookies[sessionService.cookieName()]?.value)
                ?: return HttpResponse.seeOther<Any>(URI.create("/oauth2/login?request_id=$requestId"))
        return try {
            val storedRequest = authorizationService.getPendingRequest(requestId)
            val grantedScopes =
                form["scope"]
                    ?.flatMap { it.split(" ") }
                    ?.filter { it.isNotBlank() }
                    ?: authorizationService.parseScopes(storedRequest.requestedScopes)
            val redirect =
                authorizationService.approve(
                    requestId = requestId,
                    userId = session.userId,
                    csrfToken = form.first("csrf_token"),
                    grantedScopes = grantedScopes,
                )
            HttpResponse.seeOther<Any>(URI.create(redirect))
        } catch (e: OAuth2InvalidRequestException) {
            HttpResponse.status<JsonError>(HttpStatus.BAD_REQUEST).body(JsonError(e.message ?: "Invalid request"))
        }
    }

    private fun parseForm(body: String): Map<String, List<String>> =
        body
            .split("&")
            .filter { it.isNotBlank() }
            .map { field ->
                val parts = field.split("=", limit = 2)
                decode(parts[0]) to decode(parts.getOrElse(1) { "" })
            }.groupBy(
                keySelector = { it.first },
                valueTransform = { it.second },
            )

    private fun Map<String, List<String>>.first(name: String): String? = this[name]?.firstOrNull()

    private fun decode(value: String): String = URLDecoder.decode(value, StandardCharsets.UTF_8)
}
