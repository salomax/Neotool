package io.github.salomax.neotool.comms.template.test

import io.github.salomax.neotool.comms.template.domain.Channel
import io.github.salomax.neotool.comms.template.domain.TemplateDefinition
import io.github.salomax.neotool.comms.template.registry.LocaleResolver
import io.github.salomax.neotool.comms.template.registry.TemplateRegistry
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory template registry for testing.
 *
 * Allows tests to programmatically register templates without file system dependencies.
 */
class InMemoryTemplateRegistry : TemplateRegistry {
    private val templates = ConcurrentHashMap<Channel, MutableMap<String, TemplateDefinition>>()

    /**
     * Register a template for testing.
     */
    fun register(template: TemplateDefinition) {
        templates.getOrPut(template.channel) { mutableMapOf() }[template.key] = template
    }

    /**
     * Clear all registered templates.
     */
    fun clear() {
        templates.clear()
    }

    override fun resolve(
        key: String,
        locale: Locale,
        channel: Channel,
    ): TemplateDefinition? {
        val channelTemplates = templates[channel] ?: return null
        val template = channelTemplates[key] ?: return null

        // Resolve locale with fallback
        val resolvedLocale =
            LocaleResolver.resolve(locale, template.locales.keys, template.defaultLocale)
                ?: return null

        return template
    }

    override fun listByChannel(channel: Channel): List<TemplateDefinition> {
        return templates[channel]?.values?.toList() ?: emptyList()
    }

    override fun reload() {
        // No-op for in-memory registry
    }
}
