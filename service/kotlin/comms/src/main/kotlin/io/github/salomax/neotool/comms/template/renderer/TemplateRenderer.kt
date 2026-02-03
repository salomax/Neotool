package io.github.salomax.neotool.comms.template.renderer

import io.github.salomax.neotool.comms.template.domain.TemplateDefinition

/**
 * Rendered template output.
 */
data class RenderedTemplate(
    val channel: io.github.salomax.neotool.comms.template.domain.Channel,
    val subject: String?,
    val body: String,
    val metadata: Map<String, Any> = emptyMap(),
)

/**
 * Validation result for template validation.
 */
data class ValidationResult(
    val isValid: Boolean,
    val errors: List<String> = emptyList(),
    val warnings: List<String> = emptyList(),
)

/**
 * Interface for channel-specific template renderers.
 *
 * Each channel (Email, Push, WhatsApp, etc.) implements this interface
 * to provide channel-specific rendering logic.
 */
interface TemplateRenderer {
    /**
     * Render a template with variables.
     *
     * @param template Template definition
     * @param variables Variable values for substitution
     * @param locale Locale for rendering
     * @return Rendered template output
     */
    fun render(
        template: TemplateDefinition,
        variables: Map<String, Any?>,
        locale: java.util.Locale,
    ): RenderedTemplate

    /**
     * Validate a template definition.
     *
     * @param template Template to validate
     * @return Validation result
     */
    fun validate(template: TemplateDefinition): ValidationResult
}
