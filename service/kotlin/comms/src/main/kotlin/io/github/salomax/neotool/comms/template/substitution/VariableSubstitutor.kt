package io.github.salomax.neotool.comms.template.substitution

import com.github.mustachejava.DefaultMustacheFactory
import com.github.mustachejava.MustacheFactory
import mu.KotlinLogging
import java.io.StringReader
import java.io.StringWriter

/**
 * Variable substitution engine using Mustache templating.
 *
 * Provides safe variable substitution with HTML escaping by default
 * to prevent XSS attacks.
 */
@jakarta.inject.Singleton
class VariableSubstitutor {
    private val logger = KotlinLogging.logger {}

    private val mustacheFactory: MustacheFactory = DefaultMustacheFactory()

    /**
     * Substitute variables in template content.
     *
     * @param templateContent Template content with Mustache syntax (e.g., {{variable}})
     * @param variables Map of variable names to values
     * @return Rendered content with variables substituted
     */
    fun substitute(
        templateContent: String,
        variables: Map<String, Any?>,
    ): String {
        return try {
            val mustache = mustacheFactory.compile(StringReader(templateContent), "template")
            val writer = StringWriter()
            mustache.execute(writer, variables).flush()
            writer.toString()
        } catch (e: Exception) {
            logger.error(e) { "Failed to substitute variables in template" }
            throw IllegalStateException("Variable substitution failed: ${e.message}", e)
        }
    }

    /**
     * Validate that all required variables are present.
     *
     * @param templateContent Template content to analyze
     * @param variables Provided variables
     * @param requiredVariableNames Set of required variable names
     * @return List of missing required variable names
     */
    fun validateRequiredVariables(
        templateContent: String,
        variables: Map<String, Any?>,
        requiredVariableNames: Set<String>,
    ): List<String> {
        val missing = mutableListOf<String>()
        requiredVariableNames.forEach { varName ->
            if (!variables.containsKey(varName) || variables[varName] == null) {
                missing.add(varName)
            }
        }
        return missing
    }

    /**
     * Check for unused variables (for debugging/warnings).
     *
     * @param templateContent Template content
     * @param variables Provided variables
     * @return Set of variable names that are provided but not used in template
     */
    fun findUnusedVariables(
        templateContent: String,
        variables: Map<String, Any?>,
    ): Set<String> {
        // Simple check: look for variable references in template
        val usedVariables = mutableSetOf<String>()
        variables.keys.forEach { varName ->
            // Check if variable is referenced in template
            // This is a simple check - Mustache variables use {{varName}} syntax
            val pattern = "\\{\\{[#^/]?\\s*$varName\\s*\\}\\}".toRegex()
            if (pattern.containsMatchIn(templateContent)) {
                usedVariables.add(varName)
            }
        }
        return variables.keys - usedVariables
    }
}
