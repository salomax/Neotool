package io.github.salomax.neotool.comms.template.renderer

import io.github.salomax.neotool.comms.template.domain.Channel
import io.github.salomax.neotool.comms.template.domain.TemplateDefinition
import io.github.salomax.neotool.comms.template.substitution.MissingVariableException
import io.github.salomax.neotool.comms.template.substitution.VariableSubstitutor
import jakarta.inject.Singleton
import mu.KotlinLogging
import java.util.Locale

@Suppress("UNCHECKED_CAST")
private fun <T> Any?.asTypedOrNull(): T? = this as? T

/**
 * Push notification renderer.
 *
 * Handles push notification rendering with:
 * - Title and body separation
 * - Character limits (iOS: 178, Android: ~240)
 * - Badge count support
 * - Deep link formatting
 */
@Singleton
class PushRenderer(
    private val variableSubstitutor: VariableSubstitutor,
) : TemplateRenderer {
    private val logger = KotlinLogging.logger {}

    // Character limits per platform
    private val iosTitleLimit = 50
    private val iosBodyLimit = 178
    private val androidTitleLimit = 65
    private val androidBodyLimit = 240

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
        val renderedSubject =
            content.subject?.let {
                variableSubstitutor.substitute(it, variables)
            }
        var renderedBody = variableSubstitutor.substitute(content.body, variables)

        // Apply character limits
        val truncatedTitle = renderedSubject?.take(iosTitleLimit) ?: ""
        val truncatedBody = renderedBody.take(iosBodyLimit)

        // Build JSON structure for push notification
        val pushData =
            mutableMapOf<String, Any>(
                "title" to truncatedTitle,
                "body" to truncatedBody,
            )

        // Add badge count if provided
        variables["badgeCount"]?.let {
            pushData["badge"] = it
        }

        // Add deep link if provided
        variables["deepLink"]?.let {
            pushData["data"] = mapOf("deepLink" to it)
        }

        // Convert to JSON string
        val jsonBody = buildJson(pushData)

        // Default platform, can be made configurable
        return RenderedTemplate(
            channel = Channel.PUSH,
            subject = truncatedTitle,
            body = jsonBody,
            metadata =
                mapOf(
                    "platform" to "ios",
                    "locale" to resolvedLocale.toString(),
                ),
        )
    }

    override fun validate(template: TemplateDefinition): ValidationResult {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        if (template.channel != Channel.PUSH) {
            errors.add("Template channel must be PUSH")
        }

        template.locales.forEach { (locale, content) ->
            if (content.subject.isNullOrBlank()) {
                errors.add("Subject (title) is required for locale: $locale")
            }

            // Check character limits
            content.subject?.let { subject ->
                if (subject.length > iosTitleLimit) {
                    warnings.add(
                        "Title exceeds iOS limit ($iosTitleLimit) for locale $locale: ${subject.length} chars",
                    )
                }
            }

            if (content.body.length > iosBodyLimit) {
                warnings.add(
                    "Body exceeds iOS limit ($iosBodyLimit) for locale $locale: ${content.body.length} chars",
                )
            }
        }

        return ValidationResult(
            isValid = errors.isEmpty(),
            errors = errors,
            warnings = warnings,
        )
    }

    /**
     * Build JSON string from map (simplified - in production use proper JSON library).
     */
    private fun buildJson(data: Map<String, Any>): String {
        val entries =
            data.map { (key, value) ->
                val jsonValue =
                    when (value) {
                        is String -> "\"$value\""
                        is Number -> value.toString()
                        is Map<*, *> -> buildJson(value.asTypedOrNull<Map<String, Any>>() ?: emptyMap())
                        else -> "\"$value\""
                    }
                "\"$key\": $jsonValue"
            }
        return "{${entries.joinToString(", ")}}"
    }
}
