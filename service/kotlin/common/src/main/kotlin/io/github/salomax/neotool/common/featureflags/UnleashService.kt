package io.github.salomax.neotool.common.featureflags

import io.getunleash.Unleash
import io.getunleash.UnleashConfig
import io.getunleash.UnleashContext
import io.micronaut.context.annotation.Property
import jakarta.inject.Singleton
import mu.KotlinLogging
import java.util.UUID

/**
 * Service for evaluating Unleash feature flags
 * Provides server-side feature flag evaluation with context support
 */
@Singleton
class UnleashService(
    @Property(name = "unleash.url")
    private val unleashUrl: String?,
    @Property(name = "unleash.api-token")
    private val unleashApiToken: String?,
    @Property(name = "unleash.app-name")
    private val appName: String?,
    @Property(name = "unleash.instance-id")
    private val instanceId: String?,
    @Property(name = "unleash.refresh-interval", defaultValue = "15")
    private val refreshInterval: Long,
) {
    private val logger = KotlinLogging.logger {}

    private val unleash: Unleash by lazy {
        if (unleashUrl == null || unleashApiToken == null || appName == null) {
            logger.warn("Unleash not fully configured (url, api-token, or app-name missing), feature flags will be disabled")
            // Return a disabled Unleash instance
            return@lazy createDisabledUnleash()
        }

        val config = UnleashConfig.builder()
            .unleashAPI(unleashUrl)
            .apiKey(unleashApiToken)
            .appName(appName)
            .instanceId(instanceId ?: "${appName}-${UUID.randomUUID()}")
            .fetchTogglesInterval(refreshInterval)
            .disableMetrics(false)
            .build()

        Unleash.newInstance().unleashConfig(config).build()
    }

    /**
     * Check if a feature flag is enabled
     * @param flagName The name of the feature flag
     * @param context Optional context for targeting (userId, tenantId, etc.)
     * @return true if the flag is enabled, false otherwise
     */
    fun isEnabled(
        flagName: String,
        context: UnleashContext? = null
    ): Boolean {
        return try {
            if (context != null) {
                unleash.isEnabled(flagName, context)
            } else {
                unleash.isEnabled(flagName)
            }
        } catch (e: Exception) {
            logger.error(e) { "Error evaluating feature flag: $flagName" }
            false // Default to disabled on error
        }
    }

    /**
     * Get variant of a feature flag
     * @param flagName The name of the feature flag
     * @param context Optional context for targeting
     * @return The variant or null if not enabled
     */
    fun getVariant(
        flagName: String,
        context: UnleashContext? = null
    ): io.getunleash.Variant? {
        return try {
            if (context != null) {
                unleash.getVariant(flagName, context)
            } else {
                unleash.getVariant(flagName)
            }
        } catch (e: Exception) {
            logger.error(e) { "Error getting variant for feature flag: $flagName" }
            null
        }
    }

    /**
     * Create a disabled Unleash instance (when configuration is missing)
     */
    private fun createDisabledUnleash(): Unleash {
        val config = UnleashConfig.builder()
            .unleashAPI("http://localhost:4242")
            .apiKey("disabled")
            .appName(appName ?: "neotool-service-disabled")
            .instanceId(instanceId ?: "${appName ?: "neotool-service"}-${UUID.randomUUID()}")
            .fetchTogglesInterval(60)
            .disableMetrics(true)
            .build()

        return Unleash.newInstance().unleashConfig(config).build()
    }
}

/**
 * Builder for creating UnleashContext from request context
 */
object UnleashContextBuilder {
    /**
     * Build UnleashContext from user/request context
     */
    fun build(
        userId: String? = null,
        tenantId: String? = null,
        role: String? = null,
        plan: String? = null,
        region: String? = null,
        environment: String? = null,
        sessionId: String? = null,
        remoteAddress: String? = null,
    ): UnleashContext {
        val builder = UnleashContext.builder()
            .appName("neotool")

        userId?.let { builder.userId(it) }
        tenantId?.let { builder.properties("tenantId", it) }
        role?.let { builder.properties("role", it) }
        plan?.let { builder.properties("plan", it) }
        region?.let { builder.properties("region", it) }
        environment?.let { builder.properties("environment", it) }
        sessionId?.let { builder.sessionId(it) }
        remoteAddress?.let { builder.remoteAddress(it) }

        return builder.build()
    }
}
