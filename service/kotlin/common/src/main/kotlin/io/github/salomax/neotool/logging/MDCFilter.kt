package io.github.salomax.neotool.logging

import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.filter.Filter
import ch.qos.logback.core.spi.FilterReply
import org.slf4j.MDC
import java.util.*

/**
 * Logback filter that adds correlation ID to MDC for request tracing.
 * This ensures every log entry includes a correlation ID for tracking requests across services.
 */
class MDCFilter : Filter<ILoggingEvent>() {

    companion object {
        const val CORRELATION_ID_KEY = "correlationId"
        const val REQUEST_ID_KEY = "requestId"
        const val USER_ID_KEY = "userId"
        const val SESSION_ID_KEY = "sessionId"
    }

    override fun decide(event: ILoggingEvent): FilterReply {
        // Ensure correlation ID is always present
        if (MDC.get(CORRELATION_ID_KEY) == null) {
            MDC.put(CORRELATION_ID_KEY, generateCorrelationId())
        }
        
        return FilterReply.ACCEPT
    }

    private fun generateCorrelationId(): String {
        return UUID.randomUUID().toString().substring(0, 8)
    }
}
