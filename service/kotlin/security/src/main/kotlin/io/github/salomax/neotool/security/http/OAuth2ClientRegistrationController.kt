package io.github.salomax.neotool.security.http

import io.github.salomax.neotool.security.http.dto.OAuth2ClientRegistrationRequest
import io.github.salomax.neotool.security.http.dto.OAuth2ErrorResponse
import io.github.salomax.neotool.security.service.oauth2.OAuth2ClientRegistrationService
import io.github.salomax.neotool.security.service.oauth2.OAuth2InvalidRequestException
import io.micronaut.http.HttpRequest
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
class OAuth2ClientRegistrationController(
    private val registrationService: OAuth2ClientRegistrationService,
) {
    @Post("/register")
    @Consumes(MediaType.APPLICATION_JSON)
    fun register(
        httpRequest: HttpRequest<*>,
        @Body request: OAuth2ClientRegistrationRequest,
    ): HttpResponse<*> =
        try {
            HttpResponse
                .status<Any>(HttpStatus.CREATED)
                .body(registrationService.register(request, sourceIp(httpRequest)))
        } catch (e: OAuth2InvalidRequestException) {
            HttpResponse.status<OAuth2ErrorResponse>(HttpStatus.BAD_REQUEST)
                .body(OAuth2ErrorResponse(e.error, e.message ?: "Invalid request"))
        }

    private fun sourceIp(request: HttpRequest<*>): String? =
        request.remoteAddress.address?.hostAddress ?: request.remoteAddress.hostString
}
