package io.github.salomax.neotool.common.featureflags

import io.getunleash.DefaultUnleash
import io.getunleash.Unleash
import io.getunleash.UnleashContext
import io.getunleash.Variant
import io.getunleash.util.UnleashConfig
import io.micronaut.context.annotation.Property
import jakarta.inject.Singleton
import mu.KotlinLogging
import java.util.Optional
import java.util.UUID
import java.util.function.BiPredicate

/**
 * Service for evaluating Unleash feature flags
 * Provides server-side feature flag evaluation with context support
 */
@Singleton
class UnleashService(
    @param:Property(name = "unleash.url")
    private val unleashUrl: String?,
    @param:Property(name = "unleash.api-token")
    private val unleashApiToken: String?,
    @param:Property(name = "unleash.app-name")
    private val appName: String?,
    @param:Property(name = "unleash.instance-id")
    private val instanceId: String?,
    @param:Property(name = "unleash.refresh-interval", defaultValue = "15")
    private val refreshInterval: Long,
) {
    private val logger = KotlinLogging.logger {}

    private val unleash: Unleash by lazy {
        if (unleashUrl == null || unleashApiToken == null || appName == null) {
            logger.warn(
                "Unleash not fully configured (url, api-token, or app-name missing), feature flags will be disabled",
            )
            // Return a disabled Unleash instance
            return@lazy createDisabledUnleash()
        }

        val config =
            UnleashConfig.builder()
                .unleashAPI(unleashUrl)
                .apiKey(unleashApiToken)
                .appName(appName)
                .instanceId(instanceId ?: "$appName-${UUID.randomUUID()}")
                .fetchTogglesInterval(refreshInterval)
                .build()

        DefaultUnleash(config)
    }

    /**
     * Check if a feature flag is enabled
     * @param flagName The name of the feature flag
     * @param context Context for targeting (userId, tenantId, etc.) - defaults to empty context
     * @return true if the flag is enabled, false otherwise
     */
    fun isEnabled(
        flagName: String,
        context: UnleashContext = UnleashContext.builder().build(),
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
        context: UnleashContext = UnleashContext.builder().build(),
    ): Variant? {
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

    override fun isEnabled(
        toggleName: String,
        context: UnleashContext,
    ): Boolean = false

    override fun isEnabled(
        toggleName: String,
        defaultSetting: Boolean,
    ): Boolean = defaultSetting

    override fun isEnabled(
        toggleName: String,
        context: UnleashContext,
        defaultSetting: Boolean,
    ): Boolean = defaultSetting

    override fun isEnabled(
        toggleName: String,
        context: UnleashContext,
        fallbackAction: BiPredicate<String, UnleashContext>,
    ): Boolean = false

    override fun getVariant(toggleName: String): Variant = Variant("disabled", "", false)

    override fun getVariant(
        toggleName: String,
        context: UnleashContext,
    ): Variant = Variant("disabled", "", false)

    override fun getVariant(
        toggleName: String,
        defaultVariant: Variant,
    ): Variant = defaultVariant

    override fun getVariant(
        toggleName: String,
        context: UnleashContext,
        defaultVariant: Variant,
    ): Variant = defaultVariant

    @Deprecated("This method is deprecated in the Unleash API")
    override fun deprecatedGetVariant(
        toggleName: String,
        context: UnleashContext,
    ): Variant = Variant("disabled", "", false)

    @Deprecated("This method is deprecated in the Unleash API")
    override fun deprecatedGetVariant(
        toggleName: String,
        context: UnleashContext,
        defaultVariant: Variant,
    ): Variant = defaultVariant

    @Deprecated("This method is deprecated in the Unleash API")
    override fun getFeatureToggleNames(): List<String> = emptyList()

    override fun more(): io.getunleash.MoreOperations = DisabledMoreOperations()

    override fun shutdown() {
        // No-op
    }

    private class DisabledMoreOperations : io.getunleash.MoreOperations {
        override fun count(
            toggleName: String,
            enabled: Boolean,
        ) {
            // No-op
        }

        override fun countVariant(
            toggleName: String,
            variantName: String,
        ) {
            // No-op
        }

        override fun getFeatureToggleNames(): List<String> = emptyList()

        override fun getFeatureToggleDefinition(toggleName: String): Optional<io.getunleash.FeatureToggle> =
            Optional.empty()

        override fun evaluateAllToggles(): List<io.getunleash.EvaluatedToggle> = emptyList()

        override fun evaluateAllToggles(context: UnleashContext): List<io.getunleash.EvaluatedToggle> = emptyList()
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
        val builder =
            UnleashContext.builder()
                .appName("neotool")

        userId?.let { builder.userId(it) }
        // Note: Custom properties (tenantId, role, plan, region) are not supported in unleash-client-java 9.3.0
        // These would need to be handled via custom strategies on the Unleash server side
        // or by upgrading to a newer version of the SDK that supports addProperty()
        environment?.let { builder.environment(it) }
        sessionId?.let { builder.sessionId(it) }
        remoteAddress?.let { builder.remoteAddress(it) }

        return builder.build()
    }
}
