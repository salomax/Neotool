package io.github.salomax.neotool.common.tracing

import io.micronaut.context.event.BeanCreatedEvent
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanBuilder
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.api.trace.Tracer
import net.ttddyy.dsproxy.ExecutionInfo
import net.ttddyy.dsproxy.QueryInfo
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.io.PrintWriter
import java.sql.Connection
import java.sql.SQLException
import java.util.logging.Logger
import javax.sql.DataSource

@DisplayName("JDBC Query Tracing Unit Tests")
class JdbcQueryTracingListenerTest {
    @Nested
    @DisplayName("JdbcQueryTracingDataSourceListener")
    inner class JdbcQueryTracingDataSourceListenerTests {
        private val queryListener: OtelSqlQueryExecutionListener = mock()
        private val listener = JdbcQueryTracingDataSourceListener(queryListener)

        @Test
        fun `should return same datasource when already proxied`() {
            val event = mock<BeanCreatedEvent<DataSource>>()
            val dataSource = ProxyDataSourceStub()
            whenever(event.bean).thenReturn(dataSource)

            val result = listener.onCreated(event)

            assertThat(result).isSameAs(dataSource)
        }

        @Test
        fun `should wrap datasource when not already proxied`() {
            val event = mock<BeanCreatedEvent<DataSource>>()
            val dataSource = PlainDataSourceStub()
            whenever(event.bean).thenReturn(dataSource)

            val result = listener.onCreated(event)

            assertThat(result).isNotSameAs(dataSource)
            assertThat(result.javaClass.name).contains("ProxyDataSource")
        }
    }

    @Nested
    @DisplayName("OtelSqlQueryExecutionListener")
    inner class OtelSqlQueryExecutionListenerTests {
        private lateinit var openTelemetry: OpenTelemetry
        private lateinit var tracer: Tracer
        private lateinit var spanBuilder: SpanBuilder
        private lateinit var span: Span
        private lateinit var listener: OtelSqlQueryExecutionListener

        @BeforeEach
        fun setUp() {
            openTelemetry = mock()
            tracer = mock()
            spanBuilder = mock()
            span = mock()

            whenever(openTelemetry.getTracer("io.github.salomax.neotool.jdbc")).thenReturn(tracer)
            whenever(tracer.spanBuilder(any())).thenReturn(spanBuilder)
            whenever(spanBuilder.setSpanKind(SpanKind.CLIENT)).thenReturn(spanBuilder)
            whenever(spanBuilder.startSpan()).thenReturn(span)

            listener = OtelSqlQueryExecutionListener(openTelemetry)
        }

        @Test
        fun `should create span and set attributes for normal SQL query`() {
            val execInfo = mock<ExecutionInfo>()
            val queryInfo = query(" SELECT * FROM users ")

            listener.beforeQuery(execInfo, listOf(queryInfo))

            verify(tracer).spanBuilder("db.query.select")
            verify(span).setAttribute("db.system", "postgresql")
            verify(span).setAttribute("db.operation", "select")
            verify(span).setAttribute("db.statement", "SELECT * FROM users")
            verify(span).setAttribute("db.query.count", 1L)
        }

        @Test
        fun `should fallback to unknown for blank statements`() {
            val execInfo = mock<ExecutionInfo>()
            val blank = query("   ")

            listener.beforeQuery(execInfo, listOf(blank))

            verify(tracer).spanBuilder("db.query.unknown")
            verify(span).setAttribute("db.operation", "unknown")
            verify(span).setAttribute("db.statement", "unknown")
        }

        @Test
        fun `should record duration and close span on successful query`() {
            val execInfo = mock<ExecutionInfo>()
            val queryInfo = query("SELECT 1")
            whenever(execInfo.isSuccess).thenReturn(true)
            whenever(execInfo.elapsedTime).thenReturn(25L)

            listener.beforeQuery(execInfo, listOf(queryInfo))
            listener.afterQuery(execInfo, listOf(queryInfo))

            verify(span).setAttribute("db.query.duration.ms", 25.0)
            verify(span).end()
        }

        @Test
        fun `should mark span as error and record exception on failed query`() {
            val failure = RuntimeException("sql failed")
            val execInfo = mock<ExecutionInfo>()
            val queryInfo = query("UPDATE users SET name = 'x'")
            whenever(execInfo.isSuccess).thenReturn(false)
            whenever(execInfo.throwable).thenReturn(failure)
            whenever(execInfo.elapsedTime).thenReturn(50L)

            listener.beforeQuery(execInfo, listOf(queryInfo))
            listener.afterQuery(execInfo, listOf(queryInfo))

            verify(span).recordException(failure)
            verify(span).setStatus(StatusCode.ERROR)
            verify(span).setAttribute("db.query.duration.ms", 50.0)
            verify(span).end()
        }

        @Test
        fun `should do nothing when afterQuery is called without beforeQuery`() {
            val execInfo = mock<ExecutionInfo>()

            listener.afterQuery(execInfo, emptyList())

            verify(span, never()).end()
        }

        private fun query(sql: String): QueryInfo {
            val queryInfo = mock<QueryInfo>()
            whenever(queryInfo.query).thenReturn(sql)
            return queryInfo
        }
    }

    private open class PlainDataSourceStub : DataSource {
        override fun getConnection(): Connection = throw UnsupportedOperationException("Not needed in tests")

        override fun getConnection(
            username: String?,
            password: String?,
        ): Connection = throw UnsupportedOperationException("Not needed in tests")

        override fun getLogWriter(): PrintWriter = throw UnsupportedOperationException("Not needed in tests")

        override fun setLogWriter(out: PrintWriter?) = Unit

        override fun setLoginTimeout(seconds: Int) = Unit

        override fun getLoginTimeout(): Int = 0

        override fun getParentLogger(): Logger = Logger.getGlobal()

        override fun <T : Any?> unwrap(iface: Class<T>?): T = throw SQLException("Unsupported")

        override fun isWrapperFor(iface: Class<*>?): Boolean = false
    }

    // Name intentionally contains "ProxyDataSource" to trigger skip branch.
    private class ProxyDataSourceStub : PlainDataSourceStub()
}
