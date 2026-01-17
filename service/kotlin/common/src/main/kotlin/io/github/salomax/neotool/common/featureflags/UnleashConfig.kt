package io.github.salomax.neotool.common.featureflags

import io.micronaut.context.annotation.ConfigurationProperties

/**
 * Configuration properties for Unleash feature flags
 */
@ConfigurationProperties("unleash")
data class UnleashConfig(
    // Required - must be set via UNLEASH_URL environment variable
    val url: String,
    val apiToken: String? = null,
    // Required - must be set via UNLEASH_APP_NAME environment variable
    val appName: String,
    val instanceId: String? = null,
    // Refresh every 15 seconds
    val refreshInterval: Long = 15,
    val disableMetrics: Boolean = false,
    val disableAutoStart: Boolean = false,
)
