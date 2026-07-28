package io.github.salomax.neotool.mcp.observability

import io.github.salomax.neotool.mcp.security.McpAuthenticationFailureCause
import jakarta.inject.Singleton
import mu.KotlinLogging
import org.slf4j.MDC

interface McpObservabilityLogger {
    fun recordProtocolRequest(
        method: String,
        outcome: String,
        latencyMs: Long,
        clientId: String?,
        attributes: Map<String, Any?> = emptyMap(),
    )

    fun recordCatalogFetchFailure(
        method: String,
        clientId: String?,
        error: Throwable,
    )

    fun recordCatalogEmpty(
        method: String,
        clientId: String?,
        rawToolCount: Int,
    )

    fun recordAuthenticationFailure(
        endpoint: String,
        cause: McpAuthenticationFailureCause,
        oauthError: String,
    )
}

@Singleton
class DefaultMcpObservabilityLogger(
    private val metrics: McpMetrics = McpMetrics(),
) : McpObservabilityLogger {
    private val logger = KotlinLogging.logger {}

    override fun recordProtocolRequest(
        method: String,
        outcome: String,
        latencyMs: Long,
        clientId: String?,
        attributes: Map<String, Any?>,
    ) {
        metrics.recordProtocolRequest(
            method = method,
            outcome = outcome,
            latencyMs = latencyMs,
            clientId = clientId,
        )
        val toolCount = (attributes["tool_count"] as? Number)?.toInt()
        if (method == "tools/list" && outcome == "success" && toolCount != null) {
            metrics.recordToolsListSize(toolCount)
        }
        val fields =
            linkedMapOf<String, Any?>(
                "event" to "mcp_protocol_request",
                "mcp_method" to method,
                "outcome" to outcome,
                "latency_ms" to latencyMs,
                "client_id" to clientId,
            ).apply { putAll(attributes) }
        logWithMdc(fields)
    }

    override fun recordCatalogFetchFailure(
        method: String,
        clientId: String?,
        error: Throwable,
    ) {
        metrics.incrementToolCatalogFetchFailure()
        logWithMdc(
            linkedMapOf(
                "event" to "mcp_tool_catalog_fetch_failure",
                "mcp_method" to method,
                "client_id" to clientId,
                "error_type" to error::class.simpleName,
                "error_message" to error.message,
            ),
        )
    }

    override fun recordCatalogEmpty(
        method: String,
        clientId: String?,
        rawToolCount: Int,
    ) {
        logWithMdc(
            linkedMapOf(
                "event" to "mcp_tool_catalog_empty",
                "mcp_method" to method,
                "client_id" to clientId,
                "raw_tool_count" to rawToolCount,
            ),
        )
    }

    override fun recordAuthenticationFailure(
        endpoint: String,
        cause: McpAuthenticationFailureCause,
        oauthError: String,
    ) {
        metrics.incrementAuthenticationFailure(
            cause = cause.name,
            endpoint = endpoint,
        )
        logWithMdc(
            linkedMapOf(
                "event" to "mcp_authentication_failure",
                "endpoint" to endpoint,
                "cause" to cause.name.lowercase(),
                "oauth_error" to oauthError,
            ),
        )
    }

    private fun logWithMdc(fields: Map<String, Any?>) {
        runCatching {
            val populatedFields =
                fields
                    .filterValues { it != null }
                    .mapValues { sanitizeMdcValue(it.value.toString()) }
            val closeables = populatedFields.map { (key, value) -> MDC.putCloseable(key, value) }
            try {
                logger.info {
                    populatedFields.entries.joinToString(" ") { "${it.key}=${formatMessageValue(it.value)}" }
                }
            } finally {
                closeables.asReversed().forEach { it.close() }
            }
        }
    }

    private fun sanitizeMdcValue(value: String): String =
        value
            .replace(CONTROL_CHARS_REGEX, " ")
            .trim()

    private fun formatMessageValue(value: String): String =
        if (value.any { it == ' ' || it == '=' || it == '"' }) {
            "\"${value.replace("\\", "\\\\").replace("\"", "\\\"")}\""
        } else {
            value
        }

    companion object {
        private val CONTROL_CHARS_REGEX = Regex("[\\u0000-\\u001F\\u007F]+")
    }
}
