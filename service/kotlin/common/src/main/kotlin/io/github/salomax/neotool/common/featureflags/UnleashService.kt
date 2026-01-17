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
     * @param context Context for targeting (userId, tenantId, etc.) - defaults to empty context
     * @return true if the flag is enabled, false otherwise
     */
    fun isEnabled(
        flagName: String,
        context: UnleashContext = UnleashContext.builder().build()
    ): Boolean {
        return try {
            unleash.isEnabled(flagName, context)
        } catch (e: Exception) {
            logger.error(e) { "Error evaluating feature flag: $flagName" }
            false // Default to disabled on error
        }
    }

    /**
     * Get variant of a feature flag
     * @param flagName The name of the feature flag
     * @param context Context for targeting - defaults to empty context
     * @return The variant or null if not enabled
     */
    fun getVariant(
        flagName: String,
        context: UnleashContext = UnleashContext.builder().build()
    ): io.getunleash.Variant? {
        return try {
            unleash.getVariant(flagName, context)
        } catch (e: Exception) {
            logger.error(e) { "Error getting variant for feature flag: $flagName" }
            null
        }
    }

    /**
     * Create a disabled Unleash instance (when configuration is missing)
     * Returns a fake implementation that doesn't try to connect
     */
    private fun createDisabledUnleash(): Unleash {
        return DisabledUnleash()
    }
}

/**
 * Fake Unleash implementation that always returns false
 * Used when Unleash is not configured to avoid connection attempts
 */
private class DisabledUnleash : Unleash {
    private val logger = KotlinLogging.logger {}

    init {
        logger.warn("Using disabled Unleash instance - all feature flags will return false")
    }

    override fun isEnabled(toggleName: String): Boolean = false

    override fun isEnabled(toggleName: String, context: UnleashContext): Boolean = false

    override fun isEnabled(toggleName: String, defaultSetting: Boolean): Boolean = defaultSetting

    override fun isEnabled(toggleName: String, context: UnleashContext, defaultSetting: Boolean): Boolean = defaultSetting

    override fun getVariant(toggleName: String): io.getunleash.Variant =
        io.getunleash.Variant("disabled", null, false)

    override fun getVariant(toggleName: String, context: UnleashContext): io.getunleash.Variant =
        io.getunleash.Variant("disabled", null, false)

    override fun getVariant(toggleName: String, defaultVariant: io.getunleash.Variant): io.getunleash.Variant =
        defaultVariant

    override fun getVariant(
        toggleName: String,
        context: UnleashContext,
        defaultVariant: io.getunleash.Variant
    ): io.getunleash.Variant = defaultVariant

    override fun getFeatureToggleNames(): List<String> = emptyList()

    override fun more(): io.getunleash.MoreOperations = DisabledMoreOperations()

    override fun shutdown() {
        // No-op
    }

    private class DisabledMoreOperations : io.getunleash.MoreOperations {
        override fun count(toggleName: String, enabled: Boolean) {
            // No-op
        }

        override fun countVariant(toggleName: String, variantName: String) {
            // No-op
        }

        override fun getFeatureToggleDefinition(toggleName: String): io.getunleash.FeatureToggle? = null

        override fun getFeatureToggleDefinitions(): List<io.getunleash.FeatureToggle> = emptyList()

        override fun evaluateAllToggles(): Map<String, Boolean> = emptyMap()

        override fun evaluateAllToggles(context: UnleashContext): Map<String, Boolean> = emptyMap()
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
