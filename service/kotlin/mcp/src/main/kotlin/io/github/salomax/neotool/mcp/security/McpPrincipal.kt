package io.github.salomax.neotool.mcp.security

import io.github.salomax.neotool.common.security.principal.RequestPrincipal

data class McpPrincipal(
    val principal: RequestPrincipal,
    val clientId: String,
    val scopes: Set<String>,
)
