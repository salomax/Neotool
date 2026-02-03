package io.github.salomax.neotool.comms.template.service

import io.github.salomax.neotool.comms.template.domain.Channel
import io.github.salomax.neotool.comms.template.domain.TemplateDefinition
import io.github.salomax.neotool.comms.template.registry.TemplateNotFoundException
import io.github.salomax.neotool.comms.template.registry.TemplateRegistry
import io.github.salomax.neotool.comms.template.renderer.RenderedTemplate
import io.github.salomax.neotool.comms.template.renderer.TemplateRendererFactory
import jakarta.inject.Singleton
import mu.KotlinLogging
import java.util.Locale

/**
 * Service for template rendering.
 *
 * Orchestrates template registry lookup and channel-specific rendering.
 */
@Singleton
class TemplateService(
    private val templateRegistry: TemplateRegistry,
    private val rendererFactory: TemplateRendererFactory,
) {
    private val logger = KotlinLogging.logger {}

    /**
     * Render a template with variables.
     *
     * @param templateKey Template key (e.g., "user.welcome")
     * @param locale Requested locale
     * @param channel Communication channel
     * @param variables Variable values for substitution
     * @return Rendered template
     * @throws TemplateNotFoundException if template not found
     */
    fun renderTemplate(
        templateKey: String,
        locale: Locale,
        channel: Channel,
        variables: Map<String, Any?>,
    ): RenderedTemplate {
        logger.debug { "Rendering template: key=$templateKey, locale=$locale, channel=$channel" }

        // Resolve template from registry
        val template =
            templateRegistry.resolve(templateKey, locale, channel)
                ?: throw TemplateNotFoundException(templateKey, locale, channel)

        // Get channel-specific renderer
        val renderer = rendererFactory.getRenderer(channel)

        // Render template
        return renderer.render(template, variables, locale)
    }

    /**
     * List all templates for a channel.
     *
     * @param channel Communication channel
     * @return List of template definitions
     */
    fun listTemplates(channel: Channel): List<TemplateDefinition> {
        return templateRegistry.listByChannel(channel)
    }

    /**
     * Get template definition by key and channel.
     *
     * @param templateKey Template key
     * @param channel Communication channel
     * @return Template definition, or null if not found
     */
    fun getTemplate(
        templateKey: String,
        channel: Channel,
    ): TemplateDefinition? {
        // Try with default locale first
        val defaultLocale = Locale.getDefault()
        return templateRegistry.resolve(templateKey, defaultLocale, channel)
    }
}
