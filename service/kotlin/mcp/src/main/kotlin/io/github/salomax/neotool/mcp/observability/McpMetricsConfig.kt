package io.github.salomax.neotool.mcp.observability

import io.micronaut.context.annotation.ConfigurationProperties

@ConfigurationProperties("mcp.observability")
open class McpMetricsConfig(
    var allowedClientIds: List<String> = emptyList(),
)
