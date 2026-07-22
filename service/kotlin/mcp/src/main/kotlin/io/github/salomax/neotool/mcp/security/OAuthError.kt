package io.github.salomax.neotool.mcp.security

import io.micronaut.serde.annotation.Serdeable

@Serdeable
data class OAuthError(
    val error: String,
    val error_description: String,
)

class McpAuthenticationException(
    val error: String,
    override val message: String,
) : RuntimeException(message)

class McpInsufficientScopeException(
    val requiredScope: String,
) : RuntimeException("Insufficient scope: $requiredScope")
