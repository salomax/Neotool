package io.github.salomax.neotool.logging

import mu.KotlinLogging
import org.slf4j.MDC
import java.time.Duration
import java.time.Instant

/**
 * Logging utilities and best practices for the application.
 * Provides structured logging with MDC support and performance monitoring.
 */
object LoggingUtils {
    
    private val logger = KotlinLogging.logger {}

    /**
     * Set correlation ID in MDC for request tracing
     */
    fun setCorrelationId(correlationId: String) {
        MDC.put(MDCFilter.CORRELATION_ID_KEY, correlationId)
    }

    /**
     * Set user ID in MDC for user context
     */
    fun setUserId(userId: String?) {
        if (userId != null) {
            MDC.put(MDCFilter.USER_ID_KEY, userId)
        } else {
            MDC.remove(MDCFilter.USER_ID_KEY)
        }
    }

    /**
     * Set request ID in MDC
     */
    fun setRequestId(requestId: String?) {
        if (requestId != null) {
            MDC.put(MDCFilter.REQUEST_ID_KEY, requestId)
        } else {
            MDC.remove(MDCFilter.REQUEST_ID_KEY)
        }
    }

    /**
     * Set OpenTelemetry trace context in MDC
     */
    fun setTraceContext(traceId: String?, spanId: String?) {
        if (traceId != null) {
            MDC.put("traceId", traceId)
            MDC.put("otel.trace_id", traceId)
        }
        if (spanId != null) {
            MDC.put("spanId", spanId)
            MDC.put("otel.span_id", spanId)
        }
    }

    /**
     * Execute a block with timing information
     */
    fun <T> withTiming(operation: String, block: () -> T): T {
        val start = Instant.now()
        return try {
            val result = block()
            val duration = Duration.between(start, Instant.now())
            logger.debug { "Operation '$operation' completed successfully in ${duration.toMillis()}ms" }
            result
        } catch (e: Exception) {
            val duration = Duration.between(start, Instant.now())
            logger.error(e) { "Operation '$operation' failed after ${duration.toMillis()}ms" }
            throw e
        }
    }

    /**
     * Log method entry with parameters (sanitized)
     */
    fun logMethodEntry(methodName: String, vararg params: Pair<String, Any?>) {
        val sanitizedParams = params.associate { (key, value) ->
          key to sanitizeValue(value)
        }
      logger.debug { "Entering $methodName with params: $sanitizedParams" }
    }

    /**
     * Log method exit with result (sanitized)
     */
    fun logMethodExit(methodName: String, result: Any? = null) {
        val sanitizedResult = sanitizeValue(result)
        logger.debug { "Exiting $methodName with result: $sanitizedResult" }
    }

    /**
     * Log business operation with context
     */
    fun logBusinessOperation(operation: String, entityType: String, entityId: String?, vararg context: Pair<String, Any?>) {
        val contextMap = context.toMap().mapValues { (_, value) -> sanitizeValue(value) }
        logger.info { 
            "Business operation: $operation on $entityType${entityId?.let { " (id: $it)" } ?: ""} - Context: $contextMap" 
        }
    }

    /**
     * Log data access operation
     */
    fun logAuditData(operation: String, serviceName: String, recordId: String? = null, vararg context: Pair<String, Any?>) {
        val contextMap = context.toMap().mapValues { (_, value) -> sanitizeValue(value) }
        logger.debug { 
            "Data access: $operation on $serviceName${recordId?.let { " (id: $it)" } ?: ""} - Context: $contextMap"
        }
    }

    /**
     * Log GraphQL operation
     */
    fun logGraphQLOperation(operation: String, query: String?, variables: Map<String, Any?>? = null) {
        val sanitizedVariables = variables?.mapValues { (_, value) -> sanitizeValue(value) }
        logger.info { 
            "GraphQL $operation - Query: ${query?.take(100)}${if (query?.length ?: 0 > 100) "..." else ""} - Variables: $sanitizedVariables" 
        }
    }

    /**
     * Log OpenTelemetry span events (for integration with distributed tracing)
     */
    fun logSpanEvent(eventName: String, vararg attributes: Pair<String, Any?>) {
        val sanitizedAttributes = attributes.toMap().mapValues { (_, value) -> sanitizeValue(value) }
        val correlationId = MDC.get(MDCFilter.CORRELATION_ID_KEY) ?: "unknown"
        val traceId = MDC.get("traceId") ?: "unknown"
        val spanId = MDC.get("spanId") ?: "unknown"
        
        logger.debug { "Span event: $eventName - Attributes: $sanitizedAttributes - CorrelationId: $correlationId - TraceId: $traceId - SpanId: $spanId" }
    }

    /**
     * Log performance metrics (for Prometheus integration)
     */
    fun logPerformanceMetric(metricName: String, value: Number, vararg labels: Pair<String, String>) {
        val labelsMap = labels.toMap()
        val correlationId = MDC.get(MDCFilter.CORRELATION_ID_KEY) ?: "unknown"
        val traceId = MDC.get("traceId") ?: "unknown"
        
        logger.debug { "Performance metric: $metricName = $value - Labels: $labelsMap - CorrelationId: $correlationId - TraceId: $traceId" }
    }

    /**
     * Log business operation with enterprise context
     */
    fun logBusinessOperationWithContext(operation: String, entityType: String, entityId: String?, vararg context: Pair<String, Any?>) {
        val contextMap = context.toMap().mapValues { (_, value) -> sanitizeValue(value) }
        val correlationId = MDC.get(MDCFilter.CORRELATION_ID_KEY) ?: "unknown"
        val traceId = MDC.get("traceId") ?: "unknown"
        
        logger.info { 
            "Business operation: $operation on $entityType${entityId?.let { " (id: $it)" } ?: ""} - Context: $contextMap - CorrelationId: $correlationId - TraceId: $traceId" 
        }
    }

    /**
     * Sanitize sensitive values for logging
     */
    internal fun sanitizeValue(value: Any?): Any? {
        return when (value) {
            is String -> {
                when {
                    value.contains("@") && value.contains(".") -> "***@***.***" // Email
                    value.length > 20 -> "${value.take(10)}...${value.takeLast(10)}" // Long strings
                    else -> value
                }
            }
            is Map<*, *> -> value.mapValues { (_, v) -> sanitizeValue(v) }
            is List<*> -> value.map { sanitizeValue(it) }
            else -> value
        }
    }
}
