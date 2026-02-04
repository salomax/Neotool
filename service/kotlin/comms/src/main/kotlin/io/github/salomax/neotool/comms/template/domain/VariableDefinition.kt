package io.github.salomax.neotool.comms.template.domain

/**
 * Definition of a template variable.
 */
data class VariableDefinition(
    val name: String,
    val type: VariableType,
    val required: Boolean = true,
    val default: Any? = null,
    val description: String? = null,
)
