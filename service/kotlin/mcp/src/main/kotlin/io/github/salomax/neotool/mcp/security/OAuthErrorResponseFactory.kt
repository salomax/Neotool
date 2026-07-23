package io.github.salomax.neotool.mcp.security

import io.micronaut.http.HttpHeaders
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.MutableHttpResponse
import jakarta.inject.Singleton

@Singleton
class OAuthErrorResponseFactory(
    private val resourceConfig: McpResourceConfig,
) {
    fun unauthorized(exception: McpAuthenticationException): MutableHttpResponse<Any> =
        HttpResponse
            .status<Any>(HttpStatus.UNAUTHORIZED)
            .header(HttpHeaders.WWW_AUTHENTICATE, wwwAuthenticate(exception))
            .body(OAuthError(exception.error, exception.message ?: "Authentication failed"))

    fun forbidden(exception: McpInsufficientScopeException): MutableHttpResponse<Any> =
        HttpResponse
            .status<Any>(HttpStatus.FORBIDDEN)
            .body(
                OAuthError(
                    error = "insufficient_scope",
                    error_description = "The token does not include required scope '${exception.requiredScope}'",
                ),
            )

    private fun wwwAuthenticate(exception: McpAuthenticationException): String =
        "Bearer error=\"${headerValue(exception.error)}\", " +
            "error_description=\"${headerValue(exception.message ?: "Authentication failed")}\", " +
            "resource_metadata=\"${headerValue(resourceConfig.metadataUrl)}\""

    private fun headerValue(value: String): String =
        value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
}
