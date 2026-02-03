package io.github.salomax.neotool.comms.template.renderer

import io.github.salomax.neotool.comms.template.domain.Channel
import io.github.salomax.neotool.comms.template.domain.TemplateDefinition
import io.github.salomax.neotool.comms.template.substitution.MissingVariableException
import io.github.salomax.neotool.comms.template.substitution.VariableSubstitutor
import jakarta.inject.Singleton
import mu.KotlinLogging
import java.util.Locale

/**
 * Chat message renderer.
 *
 * Handles chat message rendering with:
 * - Plain text rendering
 * - Emoji preservation
 * - Message length limits
 */
@Singleton
class ChatRenderer(
    private val variableSubstitutor: VariableSubstitutor,
) : TemplateRenderer {
    private val logger = KotlinLogging.logger {}

    // Reasonable limit for chat messages
    private val maxMessageLength = 4000

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

        // Enforce length limit
        if (renderedBody.length > maxMessageLength) {
            logger.warn {
                "Chat message exceeds length limit ($maxMessageLength): ${renderedBody.length} chars. Truncating."
            }
            renderedBody = renderedBody.take(maxMessageLength)
        }

        // Emojis are preserved automatically (Unicode)

        // Chat doesn't use subjects
        return RenderedTemplate(
            channel = Channel.CHAT,
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

        if (template.channel != Channel.CHAT) {
            errors.add("Template channel must be CHAT")
        }

        template.locales.forEach { (locale, content) ->
            if (content.body.length > maxMessageLength) {
                warnings.add(
                    "Body exceeds chat limit ($maxMessageLength) for locale $locale: ${content.body.length} chars",
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
