package io.github.salomax.neotool.mcp.audit

import jakarta.inject.Singleton

@Singleton
class ToolInputRedactor {
    fun redactAll(arguments: Map<String, Any?>): Map<String, Any?> {
        if (arguments.isEmpty()) {
            return arguments
        }
        return redactAllMap(arguments)
    }

    fun redact(
        arguments: Map<String, Any?>,
        sensitiveInputFields: List<String>,
    ): Map<String, Any?> {
        if (sensitiveInputFields.isEmpty() || arguments.isEmpty()) {
            return arguments
        }
        val sensitiveFields =
            sensitiveInputFields
                .map(String::trim)
                .filter(String::isNotEmpty)
                .toSet()
        if (sensitiveFields.isEmpty()) {
            return arguments
        }
        return redactMap(arguments, sensitiveFields)
    }

    private fun redactAllValue(value: Any?): Any? =
        when (value) {
            is Map<*, *> -> redactAllMap(value)
            is List<*> -> value.map { redactAllValue(it) }
            else -> REDACTED_VALUE
        }

    private fun redactAllMap(map: Map<*, *>): Map<String, Any?> =
        map.entries.associate { (rawKey, rawValue) ->
            rawKey.toString() to redactAllValue(rawValue)
        }

    private fun redactValue(
        value: Any?,
        sensitiveFields: Set<String>,
    ): Any? =
        when (value) {
            is Map<*, *> -> redactMap(value, sensitiveFields)
            is List<*> -> value.map { redactValue(it, sensitiveFields) }
            else -> value
        }

    private fun redactMap(
        map: Map<*, *>,
        sensitiveFields: Set<String>,
    ): Map<String, Any?> =
        map.entries.associate { (rawKey, rawValue) ->
            val key = rawKey.toString()
            key to
                if (key in sensitiveFields) {
                    REDACTED_VALUE
                } else {
                    redactValue(rawValue, sensitiveFields)
                }
        }

    companion object {
        const val REDACTED_VALUE = "[REDACTED]"
    }
}
