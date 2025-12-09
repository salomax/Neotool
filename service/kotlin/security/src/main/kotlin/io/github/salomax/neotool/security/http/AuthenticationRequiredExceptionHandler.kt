package io.github.salomax.neotool.security.http

import io.github.salomax.neotool.security.service.exception.AuthenticationRequiredException
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.hateoas.JsonError
import io.micronaut.http.server.exceptions.ExceptionHandler
import jakarta.inject.Singleton

/**
 * Exception handler for [AuthenticationRequiredException] in REST endpoints.
 *
 * Returns HTTP 401 (Unauthorized) with a JSON error message when authentication
 * is required but not provided or invalid.
 */
@Singleton
class AuthenticationRequiredExceptionHandler :
    ExceptionHandler<AuthenticationRequiredException, HttpResponse<JsonError>> {
    override fun handle(
        request: HttpRequest<*>,
        exception: AuthenticationRequiredException,
    ): HttpResponse<JsonError> {
        val error = JsonError("Authentication required")
        return HttpResponse.status<JsonError>(HttpStatus.UNAUTHORIZED).body(error)
    }
}
