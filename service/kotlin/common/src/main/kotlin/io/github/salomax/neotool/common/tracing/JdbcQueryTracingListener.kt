package io.github.salomax.neotool.common.tracing

import io.micronaut.context.annotation.Requires
import io.micronaut.context.event.BeanCreatedEvent
import io.micronaut.context.event.BeanCreatedEventListener
import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.api.trace.StatusCode
import jakarta.inject.Singleton
import net.ttddyy.dsproxy.ExecutionInfo
import net.ttddyy.dsproxy.QueryInfo
import net.ttddyy.dsproxy.listener.QueryExecutionListener
import net.ttddyy.dsproxy.support.ProxyDataSourceBuilder
import java.util.ArrayDeque
import javax.sql.DataSource

/**
 * Wraps DataSource beans to emit one tracing span per SQL statement.
 */
@Singleton
@Requires(property = "neotool.tracing.jdbc.enabled", notEquals = "false", defaultValue = "true")
class JdbcQueryTracingDataSourceListener : BeanCreatedEventListener<DataSource> {
    override fun onCreated(event: BeanCreatedEvent<DataSource>): DataSource {
        val dataSource = event.bean
        if (dataSource.javaClass.name.contains("ProxyDataSource")) {
            return dataSource
        }

        return ProxyDataSourceBuilder
            .create(dataSource)
            .listener(OtelSqlQueryExecutionListener)
            .build()
    }
}

internal object OtelSqlQueryExecutionListener : QueryExecutionListener {
    private val tracer = GlobalOpenTelemetry.getTracer("io.github.salomax.neotool.jdbc")
    private val spanStack = ThreadLocal.withInitial { ArrayDeque<Span>() }
    private const val maxStatementLength = 2048

    override fun beforeQuery(
        execInfo: ExecutionInfo,
        queryInfoList: List<QueryInfo>,
    ) {
        val statement = buildStatement(queryInfoList)
        val span =
            tracer
                .spanBuilder("db.query.${extractOperation(statement)}")
                .setSpanKind(SpanKind.CLIENT)
                .startSpan()

        span.setAttribute("db.system", "postgresql")
        span.setAttribute("db.operation", extractOperation(statement))
        span.setAttribute("db.statement", statement)
        span.setAttribute("db.query.count", queryInfoList.size.toLong())

        spanStack.get().addLast(span)
    }

    override fun afterQuery(
        execInfo: ExecutionInfo,
        queryInfoList: List<QueryInfo>,
    ) {
        val stack = spanStack.get()
        val span = stack.pollLast() ?: return

        if (!execInfo.isSuccess) {
            execInfo.throwable?.let { span.recordException(it) }
            span.setStatus(StatusCode.ERROR)
        }

        span.setAttribute("db.query.duration.ms", execInfo.elapsedTime.toDouble())
        span.end()

        if (stack.isEmpty()) {
            spanStack.remove()
        }
    }

    private fun buildStatement(queryInfoList: List<QueryInfo>): String {
        val joined =
            queryInfoList
                .asSequence()
                .map { it.query.trim() }
                .filter { it.isNotBlank() }
                .joinToString(separator = "; ")

        if (joined.isBlank()) {
            return "unknown"
        }

        return if (joined.length <= maxStatementLength) joined else joined.take(maxStatementLength)
    }

    private fun extractOperation(statement: String): String {
        val op = statement.trim().substringBefore(' ').lowercase()
        return if (op.isBlank()) "unknown" else op
    }
}
