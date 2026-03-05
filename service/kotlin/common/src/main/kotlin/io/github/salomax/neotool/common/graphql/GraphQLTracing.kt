package io.github.salomax.neotool.common.graphql

import graphql.schema.DataFetchingEnvironment
import graphql.schema.GraphQLNamedType
import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.api.trace.StatusCode

/**
 * OpenTelemetry span helpers for GraphQL data fetchers.
 *
 * The goal is to expose low-cardinality attributes that make it easy to
 * break down traces by business object and action in Tempo/Grafana.
 */
internal object GraphQLTracing {
    private val tracer = GlobalOpenTelemetry.getTracer("io.github.salomax.neotool.graphql")
    private val scalars = setOf("String", "Int", "Float", "Boolean", "ID")
    private val roots = setOf("Query", "Mutation", "Subscription")

    fun <T> withResolverSpan(
        env: DataFetchingEnvironment,
        actionHint: String? = null,
        block: () -> T,
    ): T {
        val fieldName =
            runCatching { env.fieldDefinition.name }
                .getOrNull()
                ?.takeIf { it.isNotBlank() }
                ?: "unknown"

        // Skip GraphQL introspection fields to reduce noise.
        if (fieldName.startsWith("__")) {
            return block()
        }

        val parentType =
            runCatching { (env.parentType as? GraphQLNamedType)?.name ?: env.parentType.toString() }
                .getOrNull()
                ?.takeIf { it.isNotBlank() }
                ?: "unknown"

        val operationType =
            runCatching { env.operationDefinition.operation.name.lowercase() }
                .getOrDefault("unknown")

        val operationName =
            runCatching { env.operationDefinition.name }
                .getOrNull()
                ?.takeIf { it.isNotBlank() }
                ?: "anonymous"

        val path =
            runCatching { env.executionStepInfo.path.toString() }
                .getOrDefault("unknown")

        val returnType =
            runCatching { env.fieldDefinition.type.toString() }
                .getOrDefault("unknown")

        val objectName = inferObjectName(returnType, parentType)
        val actionName = actionHint?.takeIf { it.isNotBlank() } ?: fieldName

        val span =
            tracer
                .spanBuilder("graphql.resolver.$parentType.$fieldName")
                .setSpanKind(SpanKind.INTERNAL)
                .startSpan()

        try {
            span.setAttribute("neotool.graphql.parent_type", parentType)
            span.setAttribute("neotool.graphql.field", fieldName)
            span.setAttribute("neotool.graphql.path", path)
            span.setAttribute("neotool.graphql.return_type", returnType)
            span.setAttribute("neotool.graphql.operation.type", operationType)
            span.setAttribute("neotool.graphql.operation.name", operationName)
            span.setAttribute("neotool.graphql.is_root_field", parentType in roots)
            span.setAttribute("neotool.domain.object", objectName)
            span.setAttribute("neotool.domain.action", actionName)

            span.makeCurrent().use { return block() }
        } catch (t: Throwable) {
            span.recordException(t)
            span.setStatus(StatusCode.ERROR)
            throw t
        } finally {
            span.end()
        }
    }

    private fun inferObjectName(
        returnType: String,
        parentType: String,
    ): String {
        val candidate =
            Regex("[A-Za-z_][A-Za-z0-9_]*")
                .findAll(returnType)
                .map { it.value }
                .lastOrNull()
                ?: return parentType

        return if (candidate in scalars) parentType else candidate
    }
}
