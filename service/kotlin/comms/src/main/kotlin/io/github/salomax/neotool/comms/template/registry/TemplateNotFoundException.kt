package io.github.salomax.neotool.comms.template.registry

import io.github.salomax.neotool.comms.template.domain.Channel
import java.util.Locale

/**
 * Exception thrown when a template cannot be found.
 */
class TemplateNotFoundException(
    val key: String,
    val locale: Locale,
    val channel: Channel,
    message: String? = null,
) : RuntimeException(
        message
            ?: "Template not found: key='$key', locale='$locale', channel='$channel'",
    )
