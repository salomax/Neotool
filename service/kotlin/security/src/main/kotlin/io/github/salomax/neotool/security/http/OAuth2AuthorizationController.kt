package io.github.salomax.neotool.security.http

import io.github.salomax.neotool.security.service.oauth2.OAuth2AuthorizationService
import io.github.salomax.neotool.security.service.oauth2.OAuth2AuthorizeInput
import io.github.salomax.neotool.security.service.oauth2.OAuth2InvalidRequestException
import io.github.salomax.neotool.security.service.oauth2.OAuth2SessionService
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.QueryValue
import io.micronaut.http.hateoas.JsonError
import jakarta.annotation.Nullable
import jakarta.inject.Singleton
import java.net.URI

@Singleton
@Controller("/oauth2")
class OAuth2AuthorizationController(
    private val authorizationService: OAuth2AuthorizationService,
    private val sessionService: OAuth2SessionService,
) {
    @Get("/authorize")
    fun authorize(
        request: HttpRequest<*>,
        @Nullable @QueryValue("response_type") responseType: String?,
        @Nullable @QueryValue("client_id") clientId: String?,
        @Nullable @QueryValue("redirect_uri") redirectUri: String?,
        @Nullable @QueryValue("scope") scope: String?,
        @Nullable @QueryValue("state") state: String?,
        @Nullable @QueryValue("code_challenge") codeChallenge: String?,
        @Nullable @QueryValue("code_challenge_method") codeChallengeMethod: String?,
        @Nullable @QueryValue("resource") resource: String?,
    ): HttpResponse<*> =
        try {
            val authorizationRequest =
                authorizationService.createAuthorizationRequest(
                    OAuth2AuthorizeInput(
                        responseType = responseType,
                        clientId = clientId,
                        redirectUri = redirectUri,
                        scope = scope,
                        state = state,
                        codeChallenge = codeChallenge,
                        codeChallengeMethod = codeChallengeMethod,
                        resource = resource,
                    ),
                )
            val requestId = requireNotNull(authorizationRequest.id)
            val session = sessionService.parse(request.cookies[sessionService.cookieName()]?.value)
            val path = if (session == null) "/oauth2/login" else "/oauth2/consent"
            HttpResponse.seeOther<Any>(URI.create("$path?request_id=$requestId"))
        } catch (e: OAuth2InvalidRequestException) {
            HttpResponse.status<JsonError>(HttpStatus.BAD_REQUEST).body(JsonError(e.message ?: "Invalid request"))
        }
}
