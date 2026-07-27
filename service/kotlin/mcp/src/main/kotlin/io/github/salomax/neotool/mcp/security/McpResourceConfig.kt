package io.github.salomax.neotool.mcp.security

import io.micronaut.context.annotation.ConfigurationProperties

@ConfigurationProperties("mcp.resource")
data class McpResourceConfig(
    val audience: String,
    val metadataUrl: String,
    val metadata: MetadataConfig = MetadataConfig(),
) {
    @ConfigurationProperties("metadata")
    data class MetadataConfig(
        val cacheMaxAgeSeconds: Long = 3600,
    ) {
        fun validate() {
            require(cacheMaxAgeSeconds > 0) {
                "mcp.resource.metadata.cache-max-age-seconds must be positive"
            }
        }
    }
}
