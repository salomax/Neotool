package io.github.salomax.neotool.mcp.security

import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.server.exceptions.ExceptionHandler
import jakarta.inject.Singleton

@Singleton
class McpAuthenticationExceptionHandler(
    private val responseFactory: OAuthErrorResponseFactory,
) : ExceptionHandler<McpAuthenticationException, HttpResponse<Any>> {
    override fun handle(
        request: HttpRequest<*>?,
        exception: McpAuthenticationException,
    ): HttpResponse<Any> {
        return responseFactory.unauthorized(exception)
    }
}

@Singleton
class McpInsufficientScopeExceptionHandler(
    private val responseFactory: OAuthErrorResponseFactory,
) : ExceptionHandler<McpInsufficientScopeException, HttpResponse<Any>> {
    override fun handle(
        request: HttpRequest<*>?,
        exception: McpInsufficientScopeException,
    ): HttpResponse<Any> {
        return responseFactory.forbidden(exception)
    }
}
