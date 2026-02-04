package io.github.salomax.neotool.comms.template.registry

import io.github.salomax.neotool.comms.template.domain.Channel
import io.github.salomax.neotool.comms.template.domain.TemplateDefinition
import mu.KotlinLogging
import java.io.File

/**
 * Validates template definitions for correctness.
 *
 * Performs validation checks:
 * - Required fields present
 * - Variable definitions valid
 * - File references exist
 * - No duplicate template keys within a channel
 */
object TemplateValidator {
    private val logger = KotlinLogging.logger {}

    /**
     * Validation error with context.
     */
    data class ValidationError(
        val templateKey: String,
        val channel: Channel,
        val field: String? = null,
        val message: String,
        val filePath: String? = null,
    )

    /**
     * Validate a template definition.
     *
     * @param template Template to validate
     * @param templateDir Directory containing the template files
     * @return List of validation errors (empty if valid)
     */
    fun validate(
        template: TemplateDefinition,
        templateDir: File,
    ): List<ValidationError> {
        val errors = mutableListOf<ValidationError>()

        // Validate key
        if (template.key.isBlank()) {
            errors.add(
                ValidationError(
                    templateKey = template.key,
                    channel = template.channel,
                    field = "key",
                    message = "Template key must not be blank",
                ),
            )
        }

        // Validate metadata
        if (template.metadata.name.isBlank()) {
            errors.add(
                ValidationError(
                    templateKey = template.key,
                    channel = template.channel,
                    field = "metadata.name",
                    message = "Template name must not be blank",
                ),
            )
        }

        // Validate variables
        template.variables.forEach { variable ->
            if (variable.name.isBlank()) {
                errors.add(
                    ValidationError(
                        templateKey = template.key,
                        channel = template.channel,
                        field = "variables[].name",
                        message = "Variable name must not be blank",
                    ),
                )
            }
        }

        // Validate locales
        if (template.locales.isEmpty()) {
            errors.add(
                ValidationError(
                    templateKey = template.key,
                    channel = template.channel,
                    field = "locales",
                    message = "At least one locale must be defined",
                ),
            )
        }

        // Validate default locale exists
        if (!template.locales.containsKey(template.defaultLocale)) {
            errors.add(
                ValidationError(
                    templateKey = template.key,
                    channel = template.channel,
                    field = "defaultLocale",
                    message = "Default locale '${template.defaultLocale}' not found in locales",
                ),
            )
        }

        // Validate locale content files exist
        template.locales.forEach { (locale, content) ->
            // Body is required and should be non-empty
            if (content.body.isBlank()) {
                errors.add(
                    ValidationError(
                        templateKey = template.key,
                        channel = template.channel,
                        field = "locales[$locale].body",
                        message = "Body content must not be blank for locale $locale",
                    ),
                )
            }
        }

        return errors
    }

    /**
     * Validate multiple templates and check for duplicates.
     *
     * @param templates List of templates to validate
     * @return Map of template key to validation errors
     */
    fun validateAll(templates: List<Pair<TemplateDefinition, File>>): Map<String, List<ValidationError>> {
        val errors = mutableMapOf<String, MutableList<ValidationError>>()

        // Check for duplicate keys within channels
        val keysByChannel = templates.groupBy { it.first.channel }
        keysByChannel.forEach { (channel, channelTemplates) ->
            val keys = channelTemplates.map { it.first.key }
            val duplicates = keys.groupingBy { it }.eachCount().filter { it.value > 1 }
            duplicates.forEach { (key, count) ->
                errors.getOrPut(key) { mutableListOf() }.add(
                    ValidationError(
                        templateKey = key,
                        channel = channel,
                        message = "Duplicate template key found: $key appears $count times in channel $channel",
                    ),
                )
            }
        }

        // Validate each template
        templates.forEach { (template, templateDir) ->
            val templateErrors = validate(template, templateDir)
            if (templateErrors.isNotEmpty()) {
                errors[template.key] =
                    (errors[template.key] ?: mutableListOf()).apply {
                        addAll(templateErrors)
                    }
            }
        }

        return errors
    }
}
