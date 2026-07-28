package io.github.salomax.neotool.mcp.security

import io.micronaut.serde.annotation.Serdeable

@Serdeable
data class OAuthError(
    val error: String,
    val error_description: String,
)

enum class McpAuthenticationFailureCause {
    MISSING_BEARER_TOKEN,
    INVALID_TOKEN,
    INVALID_TOKEN_TYPE,
    AUDIENCE_MISMATCH,
    MISSING_CLIENT_ID,
    INVALID_SUBJECT,
    MISSING_CURRENT_ACCOUNT,
    REVOKED_CONNECTION,
    EXPIRED_TOKEN,
}

class McpAuthenticationException(
    val error: String,
    override val message: String,
    val failureCause: McpAuthenticationFailureCause,
) : RuntimeException(message)

class McpInsufficientScopeException(
    val requiredScope: String,
) : RuntimeException("Insufficient scope: $requiredScope")
