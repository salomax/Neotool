package io.github.salomax.neotool.comms.template.domain

/**
 * Metadata about a template.
 */
data class TemplateMetadata(
    val name: String,
    val description: String? = null,
    val owner: String? = null,
)
