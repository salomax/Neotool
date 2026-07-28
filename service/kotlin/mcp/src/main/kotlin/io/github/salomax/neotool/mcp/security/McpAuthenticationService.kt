package io.github.salomax.neotool.mcp.security

import io.github.salomax.neotool.common.security.exception.AuthenticationFailureCause
import io.github.salomax.neotool.common.security.exception.AuthenticationRequiredException
import io.github.salomax.neotool.common.security.jwt.JwtTokenValidator
import io.github.salomax.neotool.common.security.principal.TokenPrincipalDecoder
import io.micronaut.http.HttpHeaders
import io.micronaut.http.HttpRequest
import jakarta.inject.Singleton
import java.util.UUID

@Singleton
class McpAuthenticationService(
    private val jwtTokenValidator: JwtTokenValidator,
    private val principalDecoder: TokenPrincipalDecoder,
    private val revocationStore: ConnectionRevocationStore,
    private val resourceConfig: McpResourceConfig,
) {
    fun authenticate(
        request: HttpRequest<*>,
        requiredScope: String? = null,
    ): McpPrincipal {
        val token = bearerToken(request)
        val claims =
            try {
                jwtTokenValidator.validateToken(token)
            } catch (e: AuthenticationRequiredException) {
                throw McpAuthenticationException(
                    error = "invalid_token",
                    message = "The access token is invalid or expired",
                    failureCause =
                        if (e.failureCause == AuthenticationFailureCause.TOKEN_EXPIRED) {
                            McpAuthenticationFailureCause.EXPIRED_TOKEN
                        } else {
                            McpAuthenticationFailureCause.INVALID_TOKEN
                        },
                )
            }

        if (claims["type"]?.toString() != "access") {
            throw McpAuthenticationException(
                "invalid_token",
                "The token must be an access token",
                McpAuthenticationFailureCause.INVALID_TOKEN_TYPE,
            )
        }

        if (claims.audience?.contains(resourceConfig.audience) != true) {
            throw McpAuthenticationException(
                "invalid_token",
                "The token audience is invalid",
                McpAuthenticationFailureCause.AUDIENCE_MISMATCH,
            )
        }

        val clientId =
            claims["client_id"]?.toString()?.takeIf { it.isNotBlank() }
                ?: throw McpAuthenticationException(
                    "invalid_token",
                    "The token is missing client_id",
                    McpAuthenticationFailureCause.MISSING_CLIENT_ID,
                )
        val userId =
            claims.subject?.let { parseUuid(it) }
                ?: throw McpAuthenticationException(
                    "invalid_token",
                    "The token subject is invalid",
                    McpAuthenticationFailureCause.INVALID_SUBJECT,
                )
        val accountId =
            claims["current_account"]?.toString()?.let { parseUuid(it) }
                ?: throw McpAuthenticationException(
                    "invalid_token",
                    "The token is missing current_account",
                    McpAuthenticationFailureCause.MISSING_CURRENT_ACCOUNT,
                )

        if (revocationStore.isRevoked(userId, clientId, accountId)) {
            throw McpAuthenticationException(
                "invalid_token",
                "The connection has been revoked",
                McpAuthenticationFailureCause.REVOKED_CONNECTION,
            )
        }

        val scopes = parseScopes(claims["scope"])
        if (requiredScope != null && requiredScope !in scopes) {
            throw McpInsufficientScopeException(requiredScope)
        }

        return McpPrincipal(
            principal = principalDecoder.fromToken(token),
            clientId = clientId,
            scopes = scopes,
        )
    }

    private fun bearerToken(request: HttpRequest<*>): String {
        val authorization =
            request.headers.get(HttpHeaders.AUTHORIZATION)
                ?: throw McpAuthenticationException(
                    "invalid_request",
                    "Bearer token is required",
                    McpAuthenticationFailureCause.MISSING_BEARER_TOKEN,
                )
        if (!authorization.startsWith("Bearer ", ignoreCase = true)) {
            throw McpAuthenticationException(
                "invalid_request",
                "Bearer token is required",
                McpAuthenticationFailureCause.MISSING_BEARER_TOKEN,
            )
        }
        return authorization.substringAfter(' ').trim().takeIf { it.isNotBlank() }
            ?: throw McpAuthenticationException(
                "invalid_request",
                "Bearer token is required",
                McpAuthenticationFailureCause.MISSING_BEARER_TOKEN,
            )
    }

    private fun parseUuid(value: String): UUID? =
        try {
            UUID.fromString(value)
        } catch (e: IllegalArgumentException) {
            null
        }

    private fun parseScopes(scopeClaim: Any?): Set<String> =
        when (scopeClaim) {
            is String -> scopeClaim.split(' ').mapNotNull { it.takeIf(String::isNotBlank) }.toSet()
            is Collection<*> -> scopeClaim.mapNotNull { it?.toString()?.takeIf(String::isNotBlank) }.toSet()
            else -> emptySet()
        }
}
