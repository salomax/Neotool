package io.github.salomax.neotool.assistant.service

import com.fasterxml.jackson.databind.ObjectMapper
import io.github.salomax.neotool.common.tracing.TraceExecution
import jakarta.inject.Singleton

@TraceExecution
@Singleton
open class EscalationDetector(
    private val objectMapper: ObjectMapper,
) {
    open fun detect(content: String?): EscalationSignal {
        val raw = content?.trim().orEmpty()
        if (raw.isEmpty()) {
            return EscalationSignal(escalate = false)
        }

        return try {
            val node = objectMapper.readTree(raw)
            val escalateNode = node?.get("escalate")
            val reasonNode = node?.get("reason")

            val isEscalation = node?.isObject == true && escalateNode?.isBoolean == true && escalateNode.booleanValue()
            val reason = reasonNode?.takeIf { it.isTextual }?.asText()?.trim().orEmpty()

            if (isEscalation && reason.isNotEmpty()) {
                EscalationSignal(escalate = true, reason = reason)
            } else {
                EscalationSignal(escalate = false)
            }
        } catch (_: Exception) {
            EscalationSignal(escalate = false)
        }
    }
}

data class EscalationSignal(
    val escalate: Boolean,
    val reason: String? = null,
)
