package io.github.salomax.neotool.comms.template.substitution

/**
 * Exception thrown when variable substitution fails.
 */
class VariableSubstitutionException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
