package io.github.salomax.neotool.comms.template.domain

import java.util.Locale

/**
 * Locale-specific template content.
 */
data class TemplateContent(
    val locale: Locale,
    val subject: String?,
    val body: String,
    val channelConfig: Map<String, Any> = emptyMap(),
)
