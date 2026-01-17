package io.github.salomax.neotool.common.featureflags

import io.micronaut.context.annotation.ConfigurationProperties

/**
 * Configuration properties for Unleash feature flags
 */
@ConfigurationProperties("unleash")
data class UnleashConfig(
    val url: String, // Required - must be set via UNLEASH_URL environment variable
    val apiToken: String? = null,
    val appName: String, // Required - must be set via UNLEASH_APP_NAME environment variable
    val instanceId: String? = null,
    val refreshInterval: Long = 15, // Refresh every 15 seconds
    val disableMetrics: Boolean = false,
    val disableAutoStart: Boolean = false,
)
