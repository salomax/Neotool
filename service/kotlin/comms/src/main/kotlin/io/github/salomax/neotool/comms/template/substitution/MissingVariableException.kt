package io.github.salomax.neotool.comms.template.substitution

/**
 * Exception thrown when required variables are missing.
 */
class MissingVariableException(
    val missingVariables: List<String>,
    message: String? = null,
) : RuntimeException(
        message ?: "Missing required variables: ${missingVariables.joinToString(", ")}",
    )
