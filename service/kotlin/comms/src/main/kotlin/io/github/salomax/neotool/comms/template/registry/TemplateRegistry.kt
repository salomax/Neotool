package io.github.salomax.neotool.comms.template.registry

import io.github.salomax.neotool.comms.template.domain.Channel
import io.github.salomax.neotool.comms.template.domain.TemplateDefinition
import java.util.Locale

/**
 * Registry for template lookup and management.
 *
 * Provides template resolution by key, locale, and channel with locale fallback support.
 */
interface TemplateRegistry {
    /**
     * Resolve template by key, locale, and channel.
     * Implements locale fallback: specific -> language -> default.
     *
     * @param key Template key (e.g., "user.welcome")
     * @param locale Requested locale (e.g., Locale("pt", "BR"))
     * @param channel Communication channel
     * @return Template definition with resolved locale content, or null if not found
     */
    fun resolve(
        key: String,
        locale: Locale,
        channel: Channel,
    ): TemplateDefinition?

    /**
     * List all registered templates for a channel.
     *
     * @param channel Communication channel
     * @return List of template definitions for the channel
     */
    fun listByChannel(channel: Channel): List<TemplateDefinition>

    /**
     * Reload templates from source (for hot-reload in dev).
     * This operation may be expensive and should be used sparingly.
     */
    fun reload()
}
