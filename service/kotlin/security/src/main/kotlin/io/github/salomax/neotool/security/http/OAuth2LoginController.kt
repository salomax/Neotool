package io.github.salomax.neotool.security.http

import io.github.salomax.neotool.security.service.authentication.AuthenticationService
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
import jakarta.annotation.Nullable
import jakarta.inject.Singleton
import java.net.URI
import java.util.UUID

@Singleton
@Controller("/oauth2")
class OAuth2LoginController(
    private val authorizationService: OAuth2AuthorizationService,
    private val authenticationService: AuthenticationService,
    private val sessionService: OAuth2SessionService,
    private val templateRenderer: OAuth2TemplateRenderer,
) {
    @Get("/login")
    fun login(
        request: HttpRequest<*>,
        @QueryValue("request_id") requestId: UUID,
    ): HttpResponse<*> =
        renderLogin(
            request = request,
            requestId = requestId,
            error = null,
        )

    @Post("/login")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    fun submit(
        request: HttpRequest<*>,
        @Body body: Map<String, String>,
    ): HttpResponse<*> {
        val requestId =
            UUID.fromString(
                body["request_id"] ?: throw OAuth2InvalidRequestException("request_id is required"),
            )
        val email = body["email"].orEmpty()
        val password = body["password"].orEmpty()
        val user =
            authenticationService.authenticate(email, password)
                ?: return renderLogin(request, requestId, "Invalid credentials")
        val cookie = sessionService.createSessionCookie(requireNotNull(user.id))
        return HttpResponse
            .seeOther<Any>(URI.create("/oauth2/consent?request_id=$requestId"))
            .cookie(cookie)
    }

    private fun renderLogin(
        request: HttpRequest<*>,
        requestId: UUID,
        @Nullable error: String?,
    ): HttpResponse<*> =
        try {
            authorizationService.getPendingRequest(requestId)
            HttpResponse
                .ok(
                    templateRenderer.render(
                        "oauth2-login",
                        request.headers.get(HttpHeaders.ACCEPT_LANGUAGE),
                        mapOf(
                            "requestId" to requestId.toString(),
                            "error" to error,
                            "action" to "/oauth2/login",
                        ),
                    ),
                ).contentType(MediaType.TEXT_HTML)
        } catch (e: OAuth2InvalidRequestException) {
            HttpResponse.status<JsonError>(HttpStatus.BAD_REQUEST).body(JsonError(e.message ?: "Invalid request"))
        }
}
