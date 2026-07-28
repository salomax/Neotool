package io.github.salomax.neotool.mcp.observability

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import io.github.salomax.neotool.mcp.security.McpAuthenticationFailureCause
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.slf4j.LoggerFactory

class McpObservabilityLoggerTest {
    private val meterRegistry = SimpleMeterRegistry()
    private val logger =
        DefaultMcpObservabilityLogger(
            McpMetrics(
                meterRegistry = meterRegistry,
                config = McpMetricsConfig(allowedClientIds = listOf("client-1")),
            ),
        )
    private val targetLogger = LoggerFactory.getLogger(DefaultMcpObservabilityLogger::class.java) as Logger

    @AfterEach
    fun tearDown() {
        targetLogger.detachAndStopAllAppenders()
        targetLogger.level = null
    }

    @Test
    fun `formats caller controlled values as one escaped key value line`() {
        val appender = ListAppender<ILoggingEvent>().apply { start() }
        targetLogger.addAppender(appender)
        targetLogger.level = Level.INFO

        logger.recordProtocolRequest(
            method = "unknown",
            outcome = "success",
            latencyMs = 12,
            clientId = "client-1",
            attributes =
                mapOf(
                    "raw_method" to "tools/missing\nforged",
                    "client_name" to "Claude User",
                    "client_version" to "1.0=\"beta\"",
                ),
        )

        val message = appender.list.single().formattedMessage
        assertThat(message).contains("event=mcp_protocol_request")
        assertThat(message).contains("mcp_method=unknown")
        assertThat(message).contains("raw_method=\"tools/missing forged\"")
        assertThat(message).contains("client_name=\"Claude User\"")
        assertThat(message).contains("client_version=\"1.0=\\\"beta\\\"\"")
        assertThat(message).doesNotContain("\n")
        assertThat(message.lineSequence().count()).isEqualTo(1)
    }

    @Test
    fun `swallows backend logging failures`() {
        targetLogger.level = Level.INFO

        logger.recordProtocolRequest(
            method = "tools/list",
            outcome = "error",
            latencyMs = 0,
            clientId = "client-1",
            attributes =
                mapOf(
                    "raw_method" to
                        object {
                            override fun toString(): String = error("boom")
                        },
                ),
        )
    }

    @Test
    fun `protocol metrics keep authenticated client id while authentication metrics never add it`() {
        logger.recordProtocolRequest(
            method = "tools/list",
            outcome = "success",
            latencyMs = 15,
            clientId = "client-1",
            attributes = mapOf("tool_count" to 2),
        )
        logger.recordAuthenticationFailure(
            endpoint = "/mcp",
            cause = McpAuthenticationFailureCause.INVALID_TOKEN,
            oauthError = "invalid_token",
        )

        val protocolCounter =
            meterRegistry
                .find(McpMetrics.PROTOCOL_REQUESTS_TOTAL)
                .tags("method", "tools_list", "outcome", "success", "client_id", "client-1")
                .counter()
        val authenticationMeters =
            meterRegistry
                .find(McpMetrics.AUTHENTICATION_FAILURES_TOTAL)
                .meters()

        assertThat(protocolCounter).isNotNull
        assertThat(protocolCounter!!.count()).isEqualTo(1.0)
        assertThat(authenticationMeters).hasSize(1)
        assertThat(authenticationMeters.single().id.tags.map { it.key }).doesNotContain("client_id")
    }

    @Test
    fun `records tools list size only for successful tools list requests`() {
        val metrics = mock<McpMetrics>()
        val logger = DefaultMcpObservabilityLogger(metrics)

        logger.recordProtocolRequest(
            method = "tools/list",
            outcome = "success",
            latencyMs = 15,
            clientId = "client-1",
            attributes = mapOf("tool_count" to 2),
        )
        logger.recordProtocolRequest(
            method = "initialize",
            outcome = "success",
            latencyMs = 10,
            clientId = "client-1",
            attributes = mapOf("tool_count" to 99),
        )
        logger.recordProtocolRequest(
            method = "tools/list",
            outcome = "error",
            latencyMs = 11,
            clientId = "client-1",
            attributes = mapOf("tool_count" to 3),
        )

        verify(metrics).recordToolsListSize(2)
        verify(metrics, never()).recordToolsListSize(99)
        verify(metrics, never()).recordToolsListSize(3)
    }
}
