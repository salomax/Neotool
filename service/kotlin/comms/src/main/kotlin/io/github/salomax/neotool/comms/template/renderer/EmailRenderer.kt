package io.github.salomax.neotool.comms.template.renderer

import io.github.salomax.neotool.comms.template.domain.Channel
import io.github.salomax.neotool.comms.template.domain.TemplateDefinition
import io.github.salomax.neotool.comms.template.substitution.MissingVariableException
import io.github.salomax.neotool.comms.template.substitution.VariableSubstitutor
import jakarta.inject.Singleton
import mu.KotlinLogging
import org.jsoup.Jsoup
import java.util.Locale

/**
 * Email template renderer.
 *
 * Handles HTML email rendering with:
 * - CSS inlining for email client compatibility
 * - Multipart support (HTML + plain text)
 * - Link tracking (optional)
 * - Image embedding support
 */
@Singleton
class EmailRenderer(
    private val variableSubstitutor: VariableSubstitutor,
) : TemplateRenderer {
    private val logger = KotlinLogging.logger {}

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

        // Substitute variables in subject
        val renderedSubject =
            content.subject?.let {
                variableSubstitutor.substitute(it, variables)
            }

        // Substitute variables in body
        var renderedBody = variableSubstitutor.substitute(content.body, variables)

        // Get channel config
        val channelConfig = content.channelConfig
        val format = channelConfig["format"] as? String ?: "HTML"
        val inlineCss = channelConfig["inlineCss"] as? Boolean ?: true

        // Process HTML if format is HTML
        if (format == "HTML" || format == "MULTIPART") {
            if (inlineCss) {
                renderedBody = inlineCss(renderedBody)
            }
        }

        // Warn about unused variables
        val unusedVars = variableSubstitutor.findUnusedVariables(content.body, variables)
        if (unusedVars.isNotEmpty()) {
            logger.debug { "Unused variables in template ${template.key}: ${unusedVars.joinToString(", ")}" }
        }

        return RenderedTemplate(
            channel = Channel.EMAIL,
            subject = renderedSubject,
            body = renderedBody,
            metadata =
                mapOf(
                    "format" to format,
                    "locale" to resolvedLocale.toString(),
                ),
        )
    }

    override fun validate(template: TemplateDefinition): ValidationResult {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        // Validate channel
        if (template.channel != Channel.EMAIL) {
            errors.add("Template channel must be EMAIL")
        }

        // Validate locales have subjects
        template.locales.forEach { (locale, content) ->
            if (content.subject.isNullOrBlank()) {
                errors.add("Subject is required for locale: $locale")
            }
        }

        // Validate HTML structure if format is HTML
        template.locales.forEach { (locale, content) ->
            try {
                val doc = Jsoup.parse(content.body)
                // Check for common email issues
                val images = doc.select("img")
                images.forEach { img ->
                    if (img.attr("alt").isBlank()) {
                        warnings.add("Image without alt text in locale $locale")
                    }
                }
            } catch (e: Exception) {
                warnings.add("Could not parse HTML for locale $locale: ${e.message}")
            }
        }

        return ValidationResult(
            isValid = errors.isEmpty(),
            errors = errors,
            warnings = warnings,
        )
    }

    /**
     * Inline CSS styles into HTML elements for email client compatibility.
     */
    private fun inlineCss(html: String): String {
        return try {
            val doc = Jsoup.parse(html)
            val styleElements = doc.select("style")

            // Extract CSS from <style> tags
            val cssRules = mutableMapOf<String, String>()
            styleElements.forEach { styleElement ->
                val css = styleElement.html()
                // Parse CSS rules (simplified - ph-css can help with full parsing)
                parseCssRules(css, cssRules)
                styleElement.remove() // Remove <style> tag after inlining
            }

            // Apply inline styles
            cssRules.forEach { (selector, styles) ->
                doc.select(selector).forEach { element ->
                    val existingStyle = element.attr("style") ?: ""
                    element.attr("style", "$existingStyle; $styles")
                }
            }

            doc.html()
        } catch (e: Exception) {
            logger.error(e) { "Failed to inline CSS, returning original HTML" }
            html
        }
    }

    /**
     * Parse CSS rules from CSS text.
     * This is a simplified parser - for production, use ph-css library properly.
     */
    private fun parseCssRules(
        css: String,
        rules: MutableMap<String, String>,
    ) {
        // Simple CSS parsing - extract rules like ".button { background: #007bff; }"
        val rulePattern = """([^{]+)\{([^}]+)\}""".toRegex()
        rulePattern.findAll(css).forEach { matchResult ->
            val selector = matchResult.groupValues[1].trim()
            val styles = matchResult.groupValues[2].trim()

            // Skip pseudo-classes (:hover, :active, :focus, etc.) and pseudo-elements (::before, ::after)
            // These don't work in most email clients and jsoup can't parse them
            if (!selector.contains(":")) {
                rules[selector] = styles
            } else {
                logger.debug { "Skipping pseudo-class/pseudo-element selector: $selector (not supported in emails)" }
            }
        }
    }
}
