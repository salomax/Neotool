package io.github.salomax.neotool.mcp.security

import io.micronaut.context.annotation.ConfigurationProperties

@ConfigurationProperties("mcp.resource")
data class McpResourceConfig(
    val audience: String,
    val metadataUrl: String,
)
