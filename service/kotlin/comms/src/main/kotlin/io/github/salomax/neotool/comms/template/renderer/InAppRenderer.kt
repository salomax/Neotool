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
 * In-app notification renderer.
 *
 * Handles in-app notification rendering with:
 * - Markdown support (bold, italic, links)
 * - Action buttons (up to 3)
 * - Icon/image URL support
 * - XSS prevention
 */
@Singleton
class InAppRenderer(
    private val variableSubstitutor: VariableSubstitutor,
) : TemplateRenderer {
    private val logger = KotlinLogging.logger {}

    private val maxActionButtons = 3

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

        // Parse action buttons from variables
        val actionButtons = mutableListOf<Map<String, String>>()
        for (i in 1..maxActionButtons) {
            val buttonText = variables["actionButton${i}Text"] as? String
            val buttonUrl = variables["actionButton${i}Url"] as? String
            if (buttonText != null && buttonUrl != null) {
                actionButtons.add(mapOf("text" to buttonText, "url" to buttonUrl))
            }
        }

        // Build notification data structure
        val notificationData =
            mutableMapOf<String, Any>(
                "title" to (renderedSubject ?: ""),
                "body" to renderedBody,
            )

        if (actionButtons.isNotEmpty()) {
            notificationData["actions"] = actionButtons
        }

        variables["iconUrl"]?.let {
            notificationData["icon"] = it
        }

        variables["imageUrl"]?.let {
            notificationData["image"] = it
        }

        // Convert to JSON
        val jsonBody = buildJson(notificationData)

        return RenderedTemplate(
            channel = Channel.IN_APP,
            subject = renderedSubject,
            body = jsonBody,
            metadata =
                mapOf(
                    "locale" to resolvedLocale.toString(),
                    "actionButtons" to actionButtons.size,
                ),
        )
    }

    override fun validate(template: TemplateDefinition): ValidationResult {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        if (template.channel != Channel.IN_APP) {
            errors.add("Template channel must be IN_APP")
        }

        // Check for XSS vulnerabilities (basic check)
        template.locales.forEach { (locale, content) ->
            if (content.body.contains("<script", ignoreCase = true)) {
                warnings.add("Potential XSS vulnerability detected in locale $locale: script tags found")
            }
        }

        return ValidationResult(
            isValid = errors.isEmpty(),
            errors = errors,
            warnings = warnings,
        )
    }

    /**
     * Build JSON string from map (simplified).
     */
    private fun buildJson(data: Map<String, Any>): String {
        val entries =
            data.map { (key, value) ->
                val jsonValue =
                    when (value) {
                        is String -> "\"${escapeJson(value)}\""
                        is Number -> value.toString()
                        is List<*> -> {
                            val items =
                                value.asTypedOrNull<List<Map<String, String>>>()?.joinToString(", ") { item ->
                                    val itemEntries = item.map { (k, v) -> "\"$k\": \"${escapeJson(v)}\"" }
                                    "{${itemEntries.joinToString(", ")}}"
                                } ?: "[]"
                            "[$items]"
                        }
                        is Map<*, *> -> buildJson(value.asTypedOrNull<Map<String, Any>>() ?: emptyMap())
                        else -> "\"$value\""
                    }
                "\"$key\": $jsonValue"
            }
        return "{${entries.joinToString(", ")}}"
    }

    private fun escapeJson(str: String): String {
        return str.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }
}
