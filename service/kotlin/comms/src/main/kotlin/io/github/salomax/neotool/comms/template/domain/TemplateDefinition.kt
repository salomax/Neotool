package io.github.salomax.neotool.comms.template.domain

import java.util.Locale

/**
 * Complete template definition including metadata, variables, and content.
 */
data class TemplateDefinition(
    val key: String,
    val channel: Channel,
    val metadata: TemplateMetadata,
    val variables: List<VariableDefinition>,
    val locales: Map<Locale, TemplateContent>,
    val defaultLocale: Locale,
)
