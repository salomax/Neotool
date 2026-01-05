package io.github.salomax.neotool.security.http

import io.github.salomax.neotool.common.security.exception.AuthorizationDeniedException
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.hateoas.JsonError
import io.micronaut.http.server.exceptions.ExceptionHandler
import jakarta.inject.Singleton

/**
 * Exception handler for [AuthorizationDeniedException] in REST endpoints.
 *
 * Returns HTTP 403 (Forbidden) with a JSON error message when the authenticated
 * user lacks the required permission.
 */
@Singleton
class AuthorizationDeniedExceptionHandler :
    ExceptionHandler<AuthorizationDeniedException, HttpResponse<JsonError>> {
    override fun handle(
        request: HttpRequest<*>,
        exception: AuthorizationDeniedException,
    ): HttpResponse<JsonError> {
        val error = JsonError("Permission denied")
        return HttpResponse.status<JsonError>(HttpStatus.FORBIDDEN).body(error)
    }
}
