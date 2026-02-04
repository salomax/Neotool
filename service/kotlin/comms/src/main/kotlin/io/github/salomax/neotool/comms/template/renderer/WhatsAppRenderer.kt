package io.github.salomax.neotool.comms.template.renderer

import io.github.salomax.neotool.comms.template.domain.Channel
import io.github.salomax.neotool.comms.template.domain.TemplateDefinition
import io.github.salomax.neotool.comms.template.substitution.MissingVariableException
import io.github.salomax.neotool.comms.template.substitution.VariableSubstitutor
import jakarta.inject.Singleton
import mu.KotlinLogging
import java.util.Locale

/**
 * WhatsApp message renderer.
 *
 * Handles WhatsApp message rendering with:
 * - Character limit enforcement (4096 chars)
 * - WhatsApp markdown support (*bold*, _italic_, ~strikethrough~)
 * - Link formatting
 * - Emoji preservation
 */
@Singleton
class WhatsAppRenderer(
    private val variableSubstitutor: VariableSubstitutor,
) : TemplateRenderer {
    private val logger = KotlinLogging.logger {}

    private val whatsAppCharLimit = 4096

    override fun render(
        template: TemplateDefinition,
        variables: Map<String, Any?>,
        locale: Locale,
    ): RenderedTemplate {
        // Resolve locale
        val resolvedLocale =
            io.github.salomax.neotool.comms.template.registry.LocaleResolver.resolve(
                locale,
                template.locales.keys,
                template.defaultLocale,
            ) ?: throw io.github.salomax.neotool.comms.template.registry.TemplateNotFoundException(
                template.key,
                locale,
                template.channel,
            )

        val content =
            template.locales[resolvedLocale]
                ?: throw IllegalStateException("Content not found for resolved locale: $resolvedLocale")

        // Validate required variables
        val requiredVars = template.variables.filter { it.required }.map { it.name }.toSet()
        val missingVars = variableSubstitutor.validateRequiredVariables(content.body, variables, requiredVars)
        if (missingVars.isNotEmpty()) {
            throw MissingVariableException(missingVars)
        }

        // Substitute variables
        var renderedBody = variableSubstitutor.substitute(content.body, variables)

        // Enforce character limit
        if (renderedBody.length > whatsAppCharLimit) {
            logger.warn {
                val limit = whatsAppCharLimit
                "WhatsApp message exceeds character limit ($limit): ${renderedBody.length} chars. Truncating."
            }
            renderedBody = renderedBody.take(whatsAppCharLimit)
        }

        // WhatsApp markdown is already in the template, no conversion needed
        // Just ensure links are properly formatted
        // WhatsApp doesn't use subjects
        return RenderedTemplate(
            channel = Channel.WHATSAPP,
            subject = null,
            body = renderedBody,
            metadata =
                mapOf(
                    "locale" to resolvedLocale.toString(),
                    "length" to renderedBody.length,
                ),
        )
    }

    override fun validate(template: TemplateDefinition): ValidationResult {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        if (template.channel != Channel.WHATSAPP) {
            errors.add("Template channel must be WHATSAPP")
        }

        template.locales.forEach { (locale, content) ->
            if (content.body.length > whatsAppCharLimit) {
                val limit = whatsAppCharLimit
                warnings.add(
                    "Body exceeds WhatsApp limit ($limit) for locale $locale: ${content.body.length} chars",
                )
            }
        }

        return ValidationResult(
            isValid = errors.isEmpty(),
            errors = errors,
            warnings = warnings,
        )
    }
}
