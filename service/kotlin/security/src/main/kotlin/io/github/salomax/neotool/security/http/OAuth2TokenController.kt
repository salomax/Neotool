package io.github.salomax.neotool.security.http

import io.github.salomax.neotool.common.security.exception.AuthenticationRequiredException
import io.github.salomax.neotool.security.http.dto.OAuth2ErrorResponse
import io.github.salomax.neotool.security.http.dto.OAuth2TokenRequest
import io.github.salomax.neotool.security.service.oauth2.OAuth2InvalidGrantException
import io.github.salomax.neotool.security.service.oauth2.OAuth2InvalidRequestException
import io.github.salomax.neotool.security.service.oauth2.OAuth2RateLimitException
import io.github.salomax.neotool.security.service.oauth2.OAuth2TokenService
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Consumes
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Post
import jakarta.inject.Singleton

@Singleton
@Controller("/oauth2")
class OAuth2TokenController(
    private val tokenService: OAuth2TokenService,
) {
    @Post("/token")
    @Consumes(MediaType.APPLICATION_JSON, MediaType.APPLICATION_FORM_URLENCODED)
    fun token(
        @Body request: OAuth2TokenRequest,
    ): HttpResponse<*> =
        try {
            HttpResponse.ok(tokenService.issue(request))
        } catch (e: OAuth2InvalidRequestException) {
            HttpResponse.status<OAuth2ErrorResponse>(HttpStatus.BAD_REQUEST)
                .body(OAuth2ErrorResponse(e.error, e.message ?: "Invalid request"))
        } catch (e: OAuth2InvalidGrantException) {
            HttpResponse.status<OAuth2ErrorResponse>(HttpStatus.BAD_REQUEST)
                .body(OAuth2ErrorResponse(e.error, e.message ?: "Invalid grant"))
        } catch (e: OAuth2RateLimitException) {
            HttpResponse.status<OAuth2ErrorResponse>(HttpStatus.TOO_MANY_REQUESTS)
                .body(OAuth2ErrorResponse(e.error, e.message ?: "Rate limit exceeded"))
        } catch (e: AuthenticationRequiredException) {
            HttpResponse.status<OAuth2ErrorResponse>(HttpStatus.BAD_REQUEST)
                .body(OAuth2ErrorResponse("invalid_grant", e.message ?: "Invalid refresh token"))
        }
}
